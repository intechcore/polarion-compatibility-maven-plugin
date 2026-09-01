# polarion-compatibility-maven-plugin

[![CI](https://github.com/intechcore/polarion-compatibility-maven-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/intechcore/polarion-compatibility-maven-plugin/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=intechcore_polarion-compatibility-maven-plugin&metric=coverage)](https://sonarcloud.io/summary/new_code?id=intechcore_polarion-compatibility-maven-plugin)
[![GitHub Release](https://img.shields.io/github/v/release/intechcore/polarion-compatibility-maven-plugin)](https://github.com/intechcore/polarion-compatibility-maven-plugin/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.intechcore/polarion-compatibility-maven-plugin)](https://central.sonatype.com/artifact/com.intechcore/polarion-compatibility-maven-plugin)
[![Java 21](https://img.shields.io/badge/java-21-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Maven plugin that fails the build when a Polarion extension bundles classes which reference a legacy
`javax` package.

Polarion 2606 runs on Tomcat 11 and Jakarta EE 11. Its own compatibility checker refuses to load an
extension whose bundled classes still reference a `javax` package that moved to `jakarta`. The check
happens at deploy time, in the server log. This plugin reports the same references at build time.

## Usage

```xml
<plugin>
    <groupId>com.intechcore</groupId>
    <artifactId>polarion-compatibility-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <id>check-jakarta-compatibility</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The `check` goal scans two locations:

1. `${project.build.outputDirectory}` for the module's own classes.
2. `${project.build.directory}` for every bundled jar, searched recursively.

### Excluding a jar

Some jars carry a legacy reference on a code path the extension never calls. Exclude them by file
name glob:

```xml
<configuration>
    <excludedJarNames>
        <excludedJarName>commons-logging-*.jar</excludedJarName>
    </excludedJarNames>
</configuration>
```

### Reporting without failing

Use this to measure the scope of a migration before you fix it:

```xml
<configuration>
    <failOnViolation>false</failOnViolation>
</configuration>
```

### Custom package lists

`forbiddenPackages` replaces the default list. `allowedPackages` lists the prefixes that stay
allowed inside a forbidden prefix:

```xml
<configuration>
    <forbiddenPackages>
        <forbiddenPackage>javax.servlet</forbiddenPackage>
        <forbiddenPackage>javax.ws.rs</forbiddenPackage>
    </forbiddenPackages>
    <allowedPackages/>
</configuration>
```

## Configuration Options

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `skip` | `polarion.compatibility.skip` | `false` | Skip the goal entirely |
| `failOnViolation` | `polarion.compatibility.failOnViolation` | `true` | Fail the build on a finding, or log a warning |
| `classesDirectory` | | `${project.build.outputDirectory}` | Directory with the module's own classes |
| `librariesDirectory` | | `${project.build.directory}` | Directory searched recursively for jars |
| `forbiddenPackages` | | see below | Package prefixes to reject |
| `allowedPackages` | | `javax.annotation.processing` | Prefixes that stay allowed inside a forbidden prefix |
| `excludedJarNames` | | (none) | Glob patterns matched against a jar file name |

Default `forbiddenPackages`: `javax.activation`, `javax.annotation`, `javax.batch`,
`javax.decorator`, `javax.ejb`, `javax.el`, `javax.enterprise`, `javax.faces`, `javax.inject`,
`javax.interceptor`, `javax.jms`, `javax.json`, `javax.jws`, `javax.mail`, `javax.persistence`,
`javax.resource`, `javax.security.auth.message`, `javax.security.enterprise`, `javax.security.jacc`,
`javax.servlet`, `javax.transaction`, `javax.validation`, `javax.websocket`, `javax.ws.rs`,
`javax.xml.bind`, `javax.xml.soap`, `javax.xml.ws`.

## How the Scan Works

The plugin reads the UTF-8 constant pool of each class file. The constant pool holds every type
name, method descriptor and string literal the class uses. Scanning it finds both compile-time
references and the string constants that Polarion rejects, without loading the class.

A finding names the source, the class and the reference:

```
[ERROR] jaxb-api-2.3.1.jar -> javax.xml.bind.JAXBContext references javax.xml.bind.JAXBContext
```

## Requirements

- Java 21
- Maven 3.6+

## Building

```bash
mvn clean verify        # build, test, coverage report
mvn clean install       # install locally
```

## License

MIT. See [LICENSE](LICENSE).
