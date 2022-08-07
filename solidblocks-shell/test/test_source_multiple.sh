#!/usr/bin/env bash

set -eu -o pipefail

DIR="$(cd "$(dirname "$0")" ; pwd -P)"
TEMP_DIR="${DIR}/.tmp"

source "${DIR}/../test.sh"
source "${DIR}/../download.sh"
source "${DIR}/../file.sh"
source "${DIR}/../software.sh"

software_ensure_terraform "0.12.23" "78fd53c0fffd657ee0ab5decac604b0dea2e6c0d4199a9f27db53f081d831a45"
software_ensure_export_path

test_assert_matches "terraform version" "Terraform v0.12.23" "$(terraform version)"

