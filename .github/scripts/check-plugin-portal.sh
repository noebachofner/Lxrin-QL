#!/usr/bin/env bash
# Fails unless the Gradle Plugin Portal itself serves the plugin marker POM of a version.
#
#   check-plugin-portal.sh <plugin id> <version> [attempts] [seconds between attempts]
#
# Redirects are not followed on purpose: for a plugin it does not host, plugins.gradle.org/m2 answers with a
# 303 redirect to Maven Central, so a check that followed it would pass although Gradle builds that only use the
# portal cannot resolve the plugin. PLUGIN_PORTAL_URL overrides the repository (for tests).
set -euo pipefail

id="${1:?plugin id}"
version="${2:?version}"
attempts="${3:-20}"
delay="${4:-30}"
base="${PLUGIN_PORTAL_URL:-https://plugins.gradle.org/m2}"
url="$base/$(echo "$id" | tr . /)/$id.gradle.plugin/$version/$id.gradle.plugin-$version.pom"

for attempt in $(seq 1 "$attempts"); do
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 "$url" || echo "000")
    if [ "$status" = "200" ]; then
        echo "The Gradle Plugin Portal serves $id $version: $url"
        exit 0
    fi
    echo "Attempt $attempt/$attempts: HTTP $status for $url"
    if [ "$attempt" -lt "$attempts" ]; then sleep "$delay"; fi
done

message="The Gradle Plugin Portal does not serve $id $version (last status $status). If publishPlugins reported that"
message="$message the plugin was submitted for approval, complete the approval on the portal account, then run this"
message="$message workflow manually for the tag (workflow_dispatch) to publish the plugin again."
if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error::$message"; else echo "$message" >&2; fi
exit 1
