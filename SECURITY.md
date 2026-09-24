# Security policy

## Reporting a vulnerability

Report a vulnerability privately through GitHub:
https://github.com/intechcore/polarion-compatibility-maven-plugin/security/advisories/new
(the **Security** tab, **Report a vulnerability**). Do not open a public issue for it.

We answer within a week. The fix goes into the next release, and its release notes name it.

## Supported versions

Only the latest release gets fixes.

## Scope

The plugin code, its rulesets, the build and the workflows of this repository. A bundle which
the plugin passes but Polarion rejects is a bug, not a vulnerability: open an issue for it.

Vulnerabilities in upstream software (Maven, ASM and the other dependencies in `pom.xml`) belong
to the upstream project. Tell us as well if this project is affected, so we can release a fix
when the upstream fix is out.
