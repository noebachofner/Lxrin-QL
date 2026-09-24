# Releasing

LxrinQL is published to Maven Central as `ch.lxrin:lxrin-ql` by the
[`Publish`](../.github/workflows/publish.yml) workflow.

## One-time setup

1. Create an account on the [Central Portal](https://central.sonatype.com) and
   verify the namespace `ch.lxrin` (DNS TXT record on `lxrin.ch`).
2. Generate a user token (Account → Generate User Token).
3. Create a GPG key and publish the public key:

   ```bash
   gpg --full-generate-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

4. Add these repository secrets on GitHub (Settings → Secrets and variables → Actions):

   | Secret                   | Value                                              |
   |--------------------------|----------------------------------------------------|
   | `MAVEN_CENTRAL_USERNAME` | token username                                     |
   | `MAVEN_CENTRAL_PASSWORD` | token password                                     |
   | `SIGNING_KEY`            | output of `gpg --armor --export-secret-keys <KEY_ID>` |
   | `SIGNING_KEY_PASSWORD`   | passphrase of the GPG key                          |

## Release

1. Set the new version in `gradle.properties` and `pom.xml`, update
   `CHANGELOG.md` and the version in `README.md`, and push to `main`.
2. Create a GitHub release with the tag `v<version>` (e.g. `v2.0.1`).

The workflow uploads and releases the artifacts. They are usually available on
Maven Central within 30 minutes.

To publish from your machine instead, put `mavenCentralUsername`,
`mavenCentralPassword`, `signing.keyId`, `signing.password` and
`signing.secretKeyRingFile` in `~/.gradle/gradle.properties` and run
`./gradlew publishToMavenCentral`. The key ring file can be created with
`gpg --export-secret-keys <KEY_ID> > ~/.gnupg/secring.gpg`.
