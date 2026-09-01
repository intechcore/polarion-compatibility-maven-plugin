# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

Maven plugin that fails the build when a Polarion extension bundles classes which reference a legacy
`javax` package. Polarion 2606 runs on Jakarta EE 11 and rejects such an extension at deploy time.
The plugin moves that check into the build.

**Maven Central:** `com.intechcore:polarion-compatibility-maven-plugin`
**GitHub:** `git@github.com:intechcore/polarion-compatibility-maven-plugin.git`
**SonarCloud:** `intechcore_polarion-compatibility-maven-plugin`

## Build Commands

```bash
mvn clean verify                        # build, test, coverage report
mvn clean install                       # install locally
mvn test -Dtest=CheckMojoTest           # run a single test class
mvn checkstyle:check                    # code style
mvn package -DskipTests                 # package only
# Coverage report: target/site/jacoco/index.html
```

## Architecture

- `CheckMojo` - the `polarion-compatibility:check` goal. Holds the parameters, the default package
  lists, the jar collection and the reporting.
- `CompatibilityScanner` - scans a class directory or a jar and returns the forbidden references.
- `ClassFileReader` - reads the UTF-8 constant pool of a class file. Stops after the pool, so a
  truncated class file still parses.
- `ForbiddenReference` - record of source, class name and reference.

### Why the constant pool

The constant pool holds every type name, method descriptor and string literal. It catches both
compile-time references and the string constants that Polarion's own checker rejects through
`visitLdcInsn`. No bytecode library is needed.

### Reference matching

Each forbidden package is matched in both forms: internal (`javax/servlet/`) and dotted
(`javax.servlet.`). A match extends forward over identifier characters, so the report names the full
reference. `allowedPackages` then removes the prefixes that stay legal, such as
`javax.annotation.processing`.

## Testing

JUnit 5, Mockito and AssertJ. Coverage is measured with JaCoCo. Keep line and branch coverage at
100%: SonarCloud gates on it.

`ClassFiles` (test helper) writes synthetic class files and jars. It emits only the header and the
constant pool, which is all the reader needs.

## Code Style

Checkstyle with a customized Google style: 4-space indentation, 160 character line limit. Run
`mvn checkstyle:check` to verify. A warning fails the build.

## Release

1. Run the `Bump Version & Release` workflow with `patch`, `minor` or `major`. It writes the version
   to `pom.xml` and `README.md`, commits, tags `vX.Y.Z` and pushes.
2. The tag triggers `release.yml`: tests, publish to Maven Central, GitHub Release, then a commit
   that returns `main` to the next `-SNAPSHOT`.
