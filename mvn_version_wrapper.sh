#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

VERSION=$(git describe --tags --always --dirty)

# Update version in plugin.yml
cp "$SCRIPT_DIR/src/main/resources/plugin.yml" "$SCRIPT_DIR/src/main/resources/plugin.yml.compilebackup"
perl -p -i -e "s|^version: .*$|version: $VERSION|g" "$SCRIPT_DIR/src/main/resources/plugin.yml"

# Back up pom files and update their version numbers
for pom in $(find . -name '*pom.xml'); do
	cp "$pom" "${pom}.compilebackup"
	perl -p -i -e "s|<version>dev</version>|<version>$VERSION</version>|g" "$pom"
done

mvn clean "$@"
retcode=$?

# Move backup pom files back over top of originals
for pom in $(find . -name '*pom.xml'); do
	mv -f "${pom}.compilebackup" "$pom"
done

# Restore plugin.yml
mv -f "$SCRIPT_DIR/src/main/resources/plugin.yml.compilebackup" "$SCRIPT_DIR/src/main/resources/plugin.yml"

# Return the appropriate error code from compilation
if [[ $retcode -ne 0 ]]; then
	exit $retcode
fi
