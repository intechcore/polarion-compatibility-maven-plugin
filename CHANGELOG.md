# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- OpenSSF Scorecard runs weekly and on every push to `main`. The README shows its badge.
- CodeQL also analyzes the GitHub Actions workflows.
- A new `lint` job in CI runs actionlint and zizmor on the workflows.
- Every workflow sets read-only default permissions. Checkouts keep no credentials,
  except the two that push the version commit.
- Workflow expressions reach the shell through environment variables only.
- The release builds without the Maven cache and creates the GitHub release with the
  `gh` CLI instead of a third-party action.
- Renovate pins every GitHub Action by its commit digest.
- GitHub releases after 0.1.2 carry the pom and a signed build provenance bundle (`*.intoto.jsonl`).
- Renovate takes its common rules from the shared preset `github>intechcore/renovate-config`, which also turns on OSV vulnerability alerts.

Earlier releases are listed on the
[GitHub releases page](https://github.com/intechcore/polarion-compatibility-maven-plugin/releases).
