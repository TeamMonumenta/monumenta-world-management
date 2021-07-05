#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
$SCRIPT_DIR/mvn_version_wrapper.sh deploy "$@"
