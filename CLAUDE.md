# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Maven plugin which fails the build when a Polarion extension bundle would be rejected by
Polarion's own compatibility gate. It scans the packaged jar and its nested jars, and
reimplements the Jakarta compatibility scan Polarion 2606 runs at startup, so a green build
means a server which boots.

**Maven Central:** `com.intechcore:polarion-compatibility-maven-plugin`
**GitHub:** `git@github.com:intechcore/polarion-compatibility-maven-plugin.git`
**SonarCloud:** `intechcore_polarion-compatibility-maven-plugin`

## Build Commands

```bash
# Build and run tests
mvn clean test

# Build with coverage report
mvn clean verify

# Build and install locally
mvn clean install

# Run a single test class
mvn test -Dtest=PackageRulesTest

# Check code style
mvn checkstyle:check
```

## Architecture

- `CheckMojo` - the goal (`polarion-compatibility:check`), bound to `verify`.
- `BundleScanner` - walks the jar, recursing into nested jars, and dispatches each entry.
- `ForbiddenPackageVisitor` - ASM `ClassVisitor`, a mirror of Polarion's `JavaxDetectionVisitor`.
- `PackageRules` - substring matcher over forbidden packages, with optional allow entries.
- `RulesetLoader` - reads rulesets from resources, files, and inline configuration.
- `ManifestChecker`, `WebXmlChecker` - manifest, `web.xml` and `.jsp` checks.
- `ViolationReport` - renders findings for the build log.

## Landmines

### Nullability annotations do not go on arrays

`@NotNull byte[] bytes` annotates the primitive component and IDEA reports "Primitive type
members cannot be annotated". The JLS-correct form `byte @NotNull [] bytes` fixes IDEA but
breaks the build: QDox inside `maven-plugin-plugin:descriptor` cannot parse it, for object
arrays too. Array parameters and return types therefore carry no nullability annotation. Do not
add one back in either form.

### Parity with Polarion is the whole point

The reference implementation is `com/polarion/alm/install/extensions/validator/`. Read
`ExtensionsScanner`, `JakartaCompatibilityChecker` and `JavaxDetectionVisitor` before changing
detection. The README section "How Polarion checks this at runtime" documents them. Siemens'
own announcement is
<https://blogs.sw.siemens.com/polarion/polarion-2606-tomcat-11-custom-extension-update/>; it
names the problem but not the algorithm, so it is background rather than a specification.

Check the behavior against the released `com.polarion.alm.install:install` artifact for the
Polarion version you target. `javap -p -c` on that jar gives the current member names, the
parsing options and the package list. Notes taken from an earlier 2606 build are already out of
date: both constructors gained a second argument in the released 2606 artifact.

That artifact also makes an oracle possible. `JakartaCompatibilityChecker` is public, its
constructor takes the forbidden and excluded sets, and `isExtensionCompatible` takes a plain
`File`, so its verdict on a built bundle can be compared with this plugin's. It needs
`com.polarion.core.util:util`, `org.ow2.asm:asm`, `log4j-api` and `log4j-core` on the
classpath; the deployer's generated pom declares no dependencies.

Any change which makes this plugin **more lenient** than Polarion is a bug: the build goes green
and the server then refuses to start. Stricter is acceptable and several deliberate differences
already are, listed in the README.

### Matching is a plain substring search, not a prefix or boundary match

`content.replace('/', '.')` then `contains(forbiddenPackage)`, copied from
`JakartaCompatibilityChecker.hasForbiddenPackages`. Consequences that look like bugs but
are not:

- `javax.transaction` matches `javax.transaction.xa.XAResource`, a JDK package.
- `javax.annotation` matches `javax.annotation.processing.Processor`, a JDK package.
- A `Bundle-ClassPath` entry naming `javax.mail-1.6.2.jar` fails, with no class involved.

An earlier version used prefix matching plus a capitalization heuristic on dotted string
literals, and shipped allow entries for the two JDK subpackages. All of that made the plugin
more lenient than the gate. Do not reintroduce it.

### The default ruleset must stay identical to Polarion's list

`ruleset-jakarta.txt` is `ExtensionsScanner.DEFAULT_PACKAGES`, 22 entries, verbatim. Extra
Jakarta packages belong in `ruleset-jakarta-extended.txt`, which is opt-in.

### ASM parsing options must stay at 6

`ForbiddenPackageVisitor.PARSING_OPTIONS` is `SKIP_DEBUG | SKIP_FRAMES`, the value Polarion
passes to `ClassReader.accept`. `visitLocalVariable` therefore never fires in either
implementation; the override is kept only so the two stay comparable.

A `static final String` lives in a `ConstantValue` attribute, which ASM passes as the `value`
argument of `visitField`. Neither implementation checks it. That is why `ph-base` declaring
`"javax.activation.debug"` and `xmlbeans` declaring `"javax.xml.soap.character-set-encoding"`
do not fail: the constants are never loaded with `ldc` inside the bundle.

## Testing

JUnit 5 and AssertJ. `TestArchives` writes real class files with ASM `ClassWriter` and packs
them into in-memory jars, so the tests need no fixture files. A hand-rolled byte prefix will not
survive `ClassReader`.

`CheckMojoTest` drives the goal through a reflective field setter. Maven applies
`@Parameter(defaultValue = ...)` through its configurator, which `new CheckMojo()` never runs,
so every default the test relies on has to be set explicitly in `setUp`. Forgetting
`checkClasses` makes the scan silently find nothing.

## Code Style

Checkstyle with a customized Google style: 4-space indentation, 160 character line limit. Run
`mvn checkstyle:check` to verify. A warning fails the build.

## Release

1. Run the `Bump Version & Release` workflow with `patch`, `minor` or `major`. It writes the
   version to `pom.xml` and `README.md`, commits, tags `vX.Y.Z` and pushes.
2. The tag triggers `release.yml`: tests, publish to Maven Central, GitHub Release, then a
   commit that returns `main` to the next `-SNAPSHOT`.

`bump-version.yml` rewrites `<version>X.Y.Z</version>` in `README.md` with a global sed. Any
other version literal in the README will drift, so do not pin one.
