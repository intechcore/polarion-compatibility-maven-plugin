# Contributing

## Development Setup

### Prerequisites

- Java 21
- Maven 3.6+

### Building

```bash
mvn clean verify                    # build and run tests
mvn clean package -DskipTests       # build without tests
mvn test -Dtest=CheckMojoTest       # run a single test class
mvn verify -Dinvoker.skip=true      # skip the integration tests
mvn verify -Dinvoker.test=legacy-bundle   # run one integration test project
```

`mvn verify` runs the integration tests in `src/it`. Each one builds a sample project with the
plugin, in its own Maven process, and a `verify.groovy` asserts what the build printed.

### Code Coverage

```bash
mvn clean verify
# Report at: target/site/jacoco/index.html
```

## Pull Request Process

1. Fork the repository.
2. Create a feature branch (`git checkout -b feat/amazing-feature`).
3. Make your changes.
4. Make sure the tests pass (`mvn clean verify`).
5. Commit your changes (`git commit -m 'feat: add amazing feature'`).
6. Push to the branch (`git push origin feat/amazing-feature`).
7. Open a pull request.

## Commit Messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` - new feature
- `fix:` - bug fix
- `docs:` - documentation changes
- `test:` - adding or updating tests
- `refactor:` - code refactoring
- `chore:` - maintenance tasks

A pre-commit hook validates the message. Install the hooks:

```bash
pre-commit install --hook-type commit-msg
```

## Code Style

This project uses Checkstyle with a customized Google style:

- 4-space indentation
- 160 character line limit
- Run `mvn checkstyle:check` to verify

Additional guidelines:

- Follow the existing code patterns.
- Keep line and branch coverage at 100%.
- Add Javadoc to public APIs.

## Reporting Issues

- Use GitHub Issues.
- Include the steps to reproduce.
- Include the Maven and Java versions.
