#!/bin/bash
# Returns the full version string (major.minor.patch.build)

FILE="version.properties"

if [ ! -f "$FILE" ]; then
  echo "version.properties not found!" >&2
  exit 1
fi

get_prop() {
    grep "^$1=" "$FILE" | cut -d'=' -f2 | tr -d '[:space:]'
}

MAJOR=$(get_prop versionMajor)
MINOR=$(get_prop versionMinor)
PATCH=$(get_prop versionPatch)
BUILD=$(get_prop versionBuild)

if [ -z "$BUILD" ]; then
  echo "$MAJOR.$MINOR.$PATCH"
else
  echo "$MAJOR.$MINOR.$PATCH.$BUILD"
fi
