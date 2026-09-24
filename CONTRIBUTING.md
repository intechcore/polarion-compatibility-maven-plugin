# Contributing

Issues and pull requests are welcome.

## Build and test

Requires Java 21 or later and Maven 3.6.3 or later.

```sh
mvn clean verify                          # build, unit and integration tests, coverage, javadoc
mvn clean package -DskipTests             # build without tests
mvn test -Dtest=CheckMojoTest             # run a single test class
mvn verify -Dinvoker.skip=true            # skip the integration tests
mvn verify -Dinvoker.test=legacy-bundle   # run one integration test project
mvn checkstyle:check                      # check the code style
```

`mvn verify` runs the integration tests in `src/it`. Each one builds a sample project with the
plugin, in its own Maven process, and a `verify.groovy` asserts what the build printed. The
coverage report lands in `target/site/jacoco/index.html`.

CI runs actionlint and zizmor on the workflows, the build on Linux and Windows with Java 21 and
25, and SonarCloud for every pull request.

## Code style

Checkstyle with a customized Google style: 4-space indentation, 160 character line limit. A
warning fails the build. Every public and protected member needs complete javadoc; a javadoc
warning fails the build too. Keep line and branch coverage at 100%.

## Pull requests

1. Branch from the default branch as `type/description`, for example `fix/empty-title`.
2. Keep one change per pull request. New behavior comes with tests; a bug fix adds a test that
   fails without it.
3. Write commit messages as [Conventional Commits](https://www.conventionalcommits.org/) without a
   scope: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`, `test: ...`, `build: ...`,
   `ci: ...`, `chore: ...`. A pre-commit hook checks the message:
   `pre-commit install --hook-type commit-msg`.
4. Sign your commits. The default branch accepts verified signatures only.
5. Add an entry under `## [Unreleased]` in `CHANGELOG.md`, written for users: the release notes
   quote it. Update the README when behavior or configuration changes.

Pull requests are squash-merged once all required checks are green.

## Releases

A maintainer runs the Bump Version workflow. It moves the Unreleased entries into a versioned
section, tags the release, and the Release workflow publishes it with signed build provenance.
