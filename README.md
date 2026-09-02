# Polarion Compatibility Maven Plugin

[![CI](https://github.com/intechcore/polarion-compatibility-maven-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/intechcore/polarion-compatibility-maven-plugin/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=coverage)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=bugs)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![GitHub Release](https://img.shields.io/github/v/release/intechcore/polarion-compatibility-maven-plugin)](https://github.com/intechcore/polarion-compatibility-maven-plugin/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.intechcore/polarion-compatibility-maven-plugin)](https://central.sonatype.com/artifact/com.intechcore/polarion-compatibility-maven-plugin)
[![Java 21](https://img.shields.io/badge/java-21-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Fails the Maven build when a Polarion extension bundle would be rejected by Polarion's own
compatibility gate. It scans the packaged jar and every jar nested inside it.

Polarion 2606 moved to Tomcat 11 and Jakarta EE. The `javax.*` Java EE packages are gone, and
Polarion refuses to boot when an installed extension still uses them. Extension sources break
at compile time against the new API, but bundled third-party jars do not: a dependency upgrade
can reintroduce `javax.servlet` without a single source change. This plugin closes that gap,
and it deliberately reproduces Polarion's own detection so that a green build means a server
which starts.

Siemens describes the migration itself in
[Polarion 2606: Tomcat 11 and the custom extension update][upgrade-guide]. Read that first to
port an extension; use this plugin afterward to keep it ported.

[upgrade-guide]: https://blogs.sw.siemens.com/polarion/polarion-2606-tomcat-11-custom-extension-update/

## Usage

```xml
<plugin>
    <groupId>com.intechcore</groupId>
    <artifactId>polarion-compatibility-maven-plugin</artifactId>
    <version>0.1.2</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The `check` goal binds to `verify`, so it runs after the jar is built:

```bash
mvn verify
```

## How Polarion checks this at runtime

Polarion 2606 gates both startup and update on a Jakarta compatibility scan. The plugin
reimplements that scan, so it is worth knowing exactly what the server does. The
[upgrade guide][upgrade-guide] announces the gate; what follows is what the shipped code
actually does.

### The classes

| Class                                                                              | Role                                                                   |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| `com.polarion.psvn.launcher.PolarionSVNApplication`                                | startup gate; `runImpl()` calls the scan and aborts boot when it fails |
| `com.polarion.alm.install.UpdateTool`                                              | runs the same scan during an update                                    |
| `com.polarion.alm.install.extensions.validator.ExtensionsScanner`                  | walks the extensions tree, owns the forbidden package list             |
| `com.polarion.alm.install.extensions.validator.helper.JakartaCompatibilityChecker` | inspects one file: jar, manifest, `web.xml` or `.jsp`                  |
| `com.polarion.alm.install.extensions.validator.helper.JavaxDetectionVisitor`       | ASM `ClassVisitor` which detects references in a `.class` file         |
| `com.polarion.alm.install.extensions.validator.CompatibilityValidatorLogs`         | builds the failure message                                             |
| `com.polarion.alm.install.extensions.validator.OperationType`                      | `STARTUP` or `UPDATE`                                                  |

### What it walks

`ExtensionsScanner.walkExtensionFiles` lists `<PolarionHome>/extensions/*`, resolves
`eclipse/plugins` under each, walks that tree, and keeps every file whose lowercased name ends
with `.jar` or `.jsp`, or equals `manifest.mf` or `web.xml`. Plugins named in the exclusion
property are dropped first, by `isPluginIncluded`.

`JakartaCompatibilityChecker.isExtensionCompatible` then dispatches by name:

- **`.jar`**: the jar manifest is checked, then every entry. Each entry name is lowercased before
  its suffix is tested, so an entry named `Foo.JAR` is scanned. A `.class` entry goes through ASM.
  A nested `.jar` entry is extracted to a temporary file and checked recursively, manifest
  included. Every other entry is ignored, so a `web.xml` or `.jsp` packed **inside** a jar is
  never checked. Recursion has no depth limit.
- **`manifest.mf`**: a loose manifest file on disk.
- **`web.xml`**: a loose descriptor on disk.
- **`.jsp`**: a loose page on disk.

An extension is incompatible when any one of its files fails. `findIncompatibleExtensions`
returns the list of failing files, and startup aborts.

### How it matches

Everything funnels into one method, `JakartaCompatibilityChecker.hasForbiddenPackages(String)`.
It replaces every `/` with `.` in the value, then answers whether any forbidden package occurs
in the result as a plain **substring**. There is no word boundary and no allow-list. Three
consequences matter:

1. `javax.transaction` also matches `javax.transaction.xa.XAResource`, and `javax.annotation`
   also matches `javax.annotation.processing.Processor`. Both are JDK packages which never
   moved to Jakarta. Polarion rejects them anyway.
2. A forbidden package anywhere in the string counts, including a string constant such as
   `"javax.servlet.context.tempdir"` and a manifest value such as a `Bundle-ClassPath` entry
   naming `javax.mail-1.6.2.jar`.
3. `jakarta.servlet` never matches `javax.servlet`, so a migrated bundle is clean.

### Where it looks inside a class

`JakartaCompatibilityChecker.isClassFileCompatible` reads the entry with an ASM `ClassReader`
and accepts a `JavaxDetectionVisitor` built from the forbidden package set, passing parsing
options `6`, that is `SKIP_DEBUG | SKIP_FRAMES`. Code is therefore parsed but debug information
is not.

`JavaxDetectionVisitor` checks the class name, superclass, interfaces and signature; annotation
descriptors on the class, fields, methods, parameters, instructions and local variables; field
and method descriptors and signatures; declared exceptions; the owner and descriptor of every
field and method instruction; type instructions; try-catch types; and every `String` loaded
with `ldc`. It stops at the first hit and returns a boolean.

A `static final String` constant is **not** checked: its value lives in a `ConstantValue`
attribute, which ASM reports as the `value` argument of `visitField`, and the visitor ignores
that argument. Wherever the constant is actually used, javac inlines it into an `ldc` at the
use site, and that is caught.

### `web.xml` and `.jsp`

A loose `web.xml` fails when it contains any of

```
http://xmlns.jcp.org/xml/ns/javaee
http://java.sun.com/xml/ns/javaee
http://java.sun.com/xml/ns/j2ee
```

A loose `.jsp` fails when its text contains a forbidden package, using the same substring rule.

### The forbidden list

`ExtensionsScanner.KNOWN_PACKAGES`, 22 entries, reproduced in
[`ruleset-jakarta.txt`](src/main/resources/com/intechcore/polarion/compatibility/ruleset-jakarta.txt):

```
javax.activation   javax.annotation   javax.ejb        javax.el
javax.enterprise   javax.faces        javax.inject     javax.jms
javax.json         javax.jws          javax.mail       javax.persistence
javax.resource     javax.security.auth.message         javax.security.enterprise
javax.servlet      javax.transaction  javax.validation javax.websocket
javax.ws.rs        javax.xml.bind     javax.xml.soap
```

### Server-side configuration

| `polarion.properties` key                                      | Default | Effect                                           |
|----------------------------------------------------------------|---------|--------------------------------------------------|
| `com.siemens.polarion.extensions.incompatibilityScan.enabled`  | `true`  | master switch; `false` skips the gate entirely   |
| `com.siemens.polarion.extensions.incompatiblePackages`         | unset   | comma-separated, **additive** only               |
| `com.siemens.polarion.extensions.incompatibilityScan.excludes` | unset   | comma-separated extension names skipped entirely |

`ExtensionsScanner.buildPackages` unions the packages property with the built-in list. It can
only make the scan stricter; it cannot exempt a package. Exemption happens at extension level
instead: `excludes` is matched against the `Bundle-SymbolicName` of a plugin, and Siemens
pre-seeds it with their own offenders, `com.siemens.polarion.teamcenter.services`,
`com.siemens.polarion.teamcenterlinkeddataintegration`, `com.teamcenter.tcme.externaljars` and
`org.polarion.synchronizer.proxy.salesforce`.

### These internals are not a public API

Everything above describes internal Polarion classes. They carry no compatibility promise, and
they change within a single release. An earlier 2606 build differs from the released
`com.polarion.alm.install:install:2606` artifact in six places:

|                                           | earlier 2606 build            | released 2606 artifact                                  |
|-------------------------------------------|-------------------------------|---------------------------------------------------------|
| `ExtensionsScanner` constructor           | `(String additionalPackages)` | `(List<String> packages, List<String> exclusions)`      |
| `JakartaCompatibilityChecker` constructor | `(Set<String>)`               | `(Set<String>, Set<String>)`                            |
| package list field                        | `DEFAULT_PACKAGES`            | `KNOWN_PACKAGES`                                        |
| extension exclusions                      | absent                        | `KNOWN_EXCLUDED_EXTENSIONS`, `isPluginExcludedFromScan` |
| tree walk method                          | `scanExtensionFiles`          | `walkExtensionFiles`, plus `isPluginIncluded`           |
| match method                              | `containsForbiddenPackages`   | `hasForbiddenPackages`                                  |

The 22 packages and the matching logic are the same in both. Before changing detection, check
the behavior against the `com.polarion.alm.install:install` artifact for the Polarion version
you target, not against these notes. `javap -p -c` on that jar shows the current member names
and the parsing options.

## Where this plugin differs from Polarion, on purpose

|                        | Polarion                 | This plugin                                                             |
|------------------------|--------------------------|-------------------------------------------------------------------------|
| Reports                | first hit, boolean       | every package, with the class and the value that matched                |
| Nesting depth          | unbounded recursion      | capped by `maxNestingDepth`; reaching it is reported like a violation   |
| Nested jars            | extracted to a temp file | streamed                                                                |
| `web.xml` inside a jar | not checked              | checked                                                                 |
| `.jsp` inside a jar    | not checked              | checked                                                                 |
| Jakarta packages       | 22                       | opt-in `jakarta-extended` ruleset adds 7 more                           |
| Allow entries          | none                     | supported, and documented as a way to become more lenient than the gate |

Everything in that list except the allow entries makes the build **stricter** than the server.
That is the safe direction: a build fails that the server would have accepted, never the
reverse.

## Rules

The default `jakarta` ruleset is Polarion's list, unchanged. Keep it that way. Removing an entry
lets a build pass that the server rejects.

`jakarta-extended` adds seven packages which moved to `jakarta.*` in Jakarta EE 9 but which
Polarion's list misses: `javax.batch`, `javax.decorator`, `javax.interceptor`,
`javax.security.jacc`, `javax.xml.registry`, `javax.xml.rpc`, `javax.xml.ws`. Polarion starts
with them present, and the extension then fails with `NoClassDefFoundError`.

```xml
<configuration>
    <rulesets>
        <ruleset>jakarta</ruleset>
        <ruleset>jakarta-extended</ruleset>
    </rulesets>
</configuration>
```

Omitting `<rulesets>` loads `jakarta`. An empty `<rulesets/>` loads none, which is how a project
runs on its own `rulesetFiles` or `rules` alone.

Add project rules in the ruleset line format:

```xml
<configuration>
    <rules>
        <rule>com.legacy.api -> com.modern.api</rule>
        <rule>!javax.transaction.xa</rule>
    </rules>
</configuration>
```

Or point at a file:

```xml
<configuration>
    <rulesetFiles>
        <rulesetFile>src/build/compatibility-rules.txt</rulesetFile>
    </rulesetFiles>
</configuration>
```

Order is bundled rulesets, then ruleset files, then inline rules. A later entry for the same
package replaces an earlier one.

An allow entry suppresses a match when the allow string both contains the forbidden package and
appears in the value. `!javax.transaction.xa` therefore clears `javax.transaction.xa.XAResource`
while `javax.transaction.UserTransaction` still fails. Polarion has no such mechanism, so a
bundle cleared this way still fails the server gate.

## Excluding a jar

When a dependency names a forbidden package in a code path the bundle never reaches, skip it:

```xml
<configuration>
    <excludedJars>
        <excludedJar>fop-core-*.jar</excludedJar>
    </excludedJars>
</configuration>
```

A pattern without a slash is matched against the jar file name, so it applies wherever the jar
is nested. A pattern with a slash is matched against the full nested path.

Prefer upgrading the dependency. An exclusion silences a jar completely, including the
references it grows in the next version, and Polarion will still reject it.

## Parameters

| Parameter              | Property                                      | Default                | Meaning                                    |
|------------------------|-----------------------------------------------|------------------------|--------------------------------------------|
| `jarFile`              | `polarion.compatibility.jarFile`              | the project artifact   | jar to scan                                |
| `rulesets`             |                                               | `jakarta` when omitted | bundled rulesets to load; empty loads none |
| `rulesetFiles`         |                                               | none                   | ruleset files in the project               |
| `rules`                |                                               | none                   | inline rules, applied last                 |
| `excludedJars`         |                                               | none                   | globs of nested jars to skip               |
| `checkClasses`         | `polarion.compatibility.checkClasses`         | `true`                 | scan compiled classes                      |
| `checkManifest`        | `polarion.compatibility.checkManifest`        | `true`                 | check jar manifests                        |
| `checkDescriptors`     | `polarion.compatibility.checkDescriptors`     | `true`                 | check `web.xml` and `.jsp`                 |
| `maxNestingDepth`      | `polarion.compatibility.maxNestingDepth`      | `5`                    | how deep nested jars are followed          |
| `maxSourcesPerPackage` | `polarion.compatibility.maxSourcesPerPackage` | `5`                    | classes listed per package                 |
| `failOnViolation`      | `polarion.compatibility.failOnViolation`      | `true`                 | fail, or only warn                         |
| `failOnMissingJar`     | `polarion.compatibility.failOnMissingJar`     | `false`                | fail when the jar is absent                |
| `skip`                 | `polarion.compatibility.skip`                 | `false`                | skip the check                             |

### Entries the scan cannot inspect

An entry the scan could not look inside is reported like a forbidden reference: it fails the build
when `failOnViolation` is `true`, and warns otherwise. Two things produce one: a nested jar deeper
than `maxNestingDepth`, and a class file ASM cannot parse.

Polarion answers the same way. `JakartaCompatibilityChecker.isClassFileCompatible` catches the
parse failure, logs it and returns incompatible, which stops the server. Counting an uninspected
entry as clean would make the build more lenient than the gate.

Raise `maxNestingDepth` when a bundle nests deeper than the default. To leave a jar out on
purpose, name it in `excludedJars`: an excluded jar is a deliberate opt-out and does not fail the
build.

## Surveying before enforcing

Run the whole build without breaking it:

```bash
mvn verify -Dpolarion.compatibility.failOnViolation=false
```

Scan a jar which is already built, without running the build. The goal prefix resolves the
version already declared in the project:

```bash
mvn polarion-compatibility:check -Dpolarion.compatibility.jarFile=target/my-extension.jar
```

Survey a cleaned build. Without `clean`, the bundle is reassembled over the previous `target/`,
so the scan reads the nested jars of the older dependency set and reports on a bundle which no
longer exists. This is the usual reason a survey names a jar nobody recognizes.

## Cost

A Polarion extension bundle of about 2700 classes in 21 nested jars scans in under a second.

## Requirements

- Java 21
- Maven 3.6.3+

## Building

```bash
mvn clean verify
```

```bash
mvn checkstyle:check
```

`mvn verify` also runs javadoc with `failOnWarnings`. A missing `@param`, `@return` or `@throws`
on a public or protected member fails the build.

It also runs the integration tests under `src/it`, which build three sample projects with the
plugin bound to their own `verify` phase. Add `-Dinvoker.skip=true` to leave them out.

## Release

1. Run the `Bump Version & Release` workflow with `patch`, `minor` or `major`. It writes the
   version to `pom.xml` and `README.md`, commits, tags `vX.Y.Z` and pushes.
2. The tag triggers `release.yml`: tests, publish to Maven Central, GitHub Release, then a
   commit that returns `main` to the next `-SNAPSHOT`.

## License

MIT. See [LICENSE](LICENSE).
