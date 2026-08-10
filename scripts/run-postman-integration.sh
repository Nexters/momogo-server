#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
collection_file="${project_dir}/postman/momogo-dev.postman_collection.json"
environment_file="${project_dir}/postman/momogo-dev.postman_environment.json"
report_dir="${project_dir}/build/postman"
postman_cli=${POSTMAN_CLI:-postman}

if ! command -v "$postman_cli" >/dev/null 2>&1; then
  echo "오류: Postman CLI가 필요합니다. https://learning.postman.com/docs/postman-cli/postman-cli-installation/" >&2
  exit 127
fi

mkdir -p "$report_dir"

set -- \
  collection run "$collection_file" \
  --environment "$environment_file" \
  --iteration-count 1 \
  --timeout-request "${POSTMAN_REQUEST_TIMEOUT_MS:-15000}" \
  --reporters cli,junit \
  --reporter-junit-export "${report_dir}/results.xml"

if [ -n "${MOMOGO_API_BASE_URL:-}" ]; then
  set -- "$@" --env-var "baseUrl=${MOMOGO_API_BASE_URL}"
fi

case "${MOMOGO_ENABLE_PHOTO_REPORT:-false}" in
  true)
    set -- "$@" --env-var "enablePhotoReport=true"
    ;;
  false)
    set -- "$@" --env-var "enablePhotoReport=false"
    ;;
  *)
    echo "오류: MOMOGO_ENABLE_PHOTO_REPORT는 true 또는 false여야 합니다." >&2
    exit 2
    ;;
esac

"$postman_cli" "$@"
