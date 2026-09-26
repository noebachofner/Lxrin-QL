# Releasing

All modules are published to Maven Central as `ch.lxrin:*` by the
[`Publish`](../.github/workflows/publish.yml) workflow. The Gradle plugin
`ch.lxrin.ql.codegen` is also published to the Gradle Plugin Portal.

| Artifact | Repository |
|---|---|
| `lxrin-ql-bom`, `lxrin-ql-core`, `lxrin-ql-codegen`, `lxrin-ql-spring`, `lxrin-ql-audit`, `lxrin-ql-test` | Maven Central |
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
2. Create a GitHub release with the tag `v<version>` (e.g. `v3.2.0`).

The workflow:

1. checks that the tag matches the version and that the Plugin Portal secrets are set;
2. builds and tests everything;
3. uploads and releases the Maven Central artifacts (usually available within 30
   minutes);
4. publishes the Gradle plugin to the Plugin Portal;
5. **verifies** that `https://plugins.gradle.org/m2/` serves the plugin marker of the
   version ([`check-plugin-portal.sh`](../.github/scripts/check-plugin-portal.sh), up to
   ten minutes). The workflow fails if it does not.

## Gradle Plugin Portal

`publishPlugins` also succeeds when the portal only *accepts a submission*: the first
version of a new plugin ID waits for manual approval by the portal team. For
`ch.lxrin.ql.codegen` the portal may also ask you to prove that you own `lxrin.ch`. The
plugin is not served until the approval is done. This is why 3.0.1 and 3.1.0 reported
success but were never on the portal. The verification step now catches this case.

The check does not follow redirects. For a plugin it does not host, the portal answers
`303 See Other` with a redirect to Maven Central, but builds that only use
`gradlePluginPortal()` still cannot resolve the plugin.

If the verification fails:

1. Look for "approval" in the log of the `publishPlugins` step (the workflow repeats it as
   a warning), and check the e-mail of the portal account and
   <https://plugins.gradle.org/u/noebachofner>.
2. Complete the approval, or verify the domain as the portal asks.
3. Run the `Publish` workflow manually (Actions → Publish → Run workflow) with the tag,
   e.g. `v3.2.0`. It publishes and verifies only the Gradle plugin; Maven Central is not
   touched.

Until the portal serves the plugin, consumers add Maven Central to their plugin
repositories (see [Code generation › Gradle](code-generation.md#gradle)).

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
