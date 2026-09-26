# Releasing

All modules are published to Maven Central as `ch.lxrin:*` by the
[`Publish`](../.github/workflows/publish.yml) workflow. The Gradle plugin
`ch.lxrin.ql.codegen` is also published to the Gradle Plugin Portal.

| Artifact | Repository |
|---|---|
| `lxrin-ql-bom`, `lxrin-ql-core`, `lxrin-ql-codegen`, `lxrin-ql-spring`, `lxrin-ql-test` | Maven Central |
| `lxrin-ql-maven-plugin` (packaging `maven-plugin`) | Maven Central |
| `lxrin-ql-gradle-plugin` and its plugin marker | Maven Central and Gradle Plugin Portal |

## One-time setup

1. Create an account on the [Central Portal](https://central.sonatype.com) and verify
   the namespace `ch.lxrin` (DNS TXT record on `lxrin.ch`).
2. Generate a user token (Account → Generate User Token).
3. Create a GPG key and publish the public key:

   ```bash
   gpg --full-generate-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

4. Create an account on the [Gradle Plugin Portal](https://plugins.gradle.org) and an
   API key.
5. Add these repository secrets on GitHub (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `MAVEN_CENTRAL_USERNAME` | token username |
   | `MAVEN_CENTRAL_PASSWORD` | token password |
   | `SIGNING_KEY` | output of `gpg --armor --export-secret-keys <KEY_ID>` |
   | `SIGNING_KEY_PASSWORD` | passphrase of the GPG key |
   | `GRADLE_PUBLISH_KEY` | Plugin Portal key |
   | `GRADLE_PUBLISH_SECRET` | Plugin Portal secret |

## Release

1. Set the new version in `gradle.properties`, update `CHANGELOG.md` and the versions
   in `README.md` and `docs/`, and push to `main`. The [`Build`](../.github/workflows/build.yml)
   workflow must be green.
2. Create a GitHub release with the tag `v<version>` (e.g. `v3.1.0`).

The workflow builds and tests everything, uploads and releases the Maven Central
artifacts, and publishes the Gradle plugin to the Plugin Portal. Maven Central
artifacts are usually available within 30 minutes.

## Checking a release locally

```bash
./gradlew publishAllPublicationsToIntegrationTestRepository   # writes build/it-repo
```

The integration tests use this file repository to build the example Gradle and Maven
projects in `integration-tests/consumers` against the fresh artifacts.

To publish from your machine instead:

1. Put `mavenCentralUsername`, `mavenCentralPassword`, `signing.keyId`,
   `signing.password`, `signing.secretKeyRingFile`, `gradle.publish.key` and
   `gradle.publish.secret` in `~/.gradle/gradle.properties`.
2. Run `./gradlew publishToMavenCentral :lxrin-ql-gradle-plugin:publishPlugins`.
