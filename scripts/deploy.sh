#!/bin/sh

set -eu

usage() {
  echo "사용법: $0 <dev|prod> <40자리 Git SHA>" >&2
  exit 2
}

require_value() {
  value_name=$1
  eval "value=\${$value_name:-}"
  if [ -z "$value" ]; then
    echo "오류: ${value_name} 값이 필요합니다." >&2
    exit 1
  fi
}

wait_until_healthy() {
  attempt=1
  while [ "$attempt" -le "$HEALTH_RETRIES" ]; do
    if curl --fail --silent --show-error "$HEALTH_URL" 2>/dev/null |
      grep -q '"status":"UP"'; then
      echo "헬스체크 성공 (${attempt}/${HEALTH_RETRIES})"
      return 0
    fi

    echo "헬스체크 대기 중 (${attempt}/${HEALTH_RETRIES})"
    sleep "$HEALTH_INTERVAL_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

environment=${1:-}
image_revision=${2:-}

case "$environment" in
  dev | prod) ;;
  *) usage ;;
esac

if [ "${#image_revision}" -ne 40 ]; then
  usage
fi

case "$image_revision" in
  *[!0-9a-f]*) usage ;;
esac

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
deploy_env_file=${MOMOGO_DEPLOY_ENV_FILE:-"/opt/momogo/config/${environment}.env"}
compose_file="${project_dir}/deploy/compose.${environment}.yml"

if [ ! -r "$deploy_env_file" ]; then
  echo "오류: 배포 환경 파일을 읽을 수 없습니다: ${deploy_env_file}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$deploy_env_file"
set +a

require_value IMAGE_REPOSITORY
require_value DB_HOST
require_value DB_NAME
require_value DB_USERNAME
require_value DB_PASSWORD
require_value JWT_SECRET_BASE64

IMAGE_REFERENCE="${IMAGE_REPOSITORY}:${image_revision}"
HOST_PORT=${HOST_PORT:-8080}
HEALTH_RETRIES=${HEALTH_RETRIES:-30}
HEALTH_INTERVAL_SECONDS=${HEALTH_INTERVAL_SECONDS:-3}
HEALTH_URL="http://127.0.0.1:${HOST_PORT}/actuator/health"
export IMAGE_REFERENCE HOST_PORT

compose() {
  docker compose -f "$compose_file" "$@"
}

docker_config_dir=""
cleanup() {
  if [ -n "$docker_config_dir" ] && [ -d "$docker_config_dir" ]; then
    rm -rf -- "$docker_config_dir"
  fi
}
trap cleanup EXIT HUP INT TERM

if [ -n "${GHCR_TOKEN:-}" ]; then
  require_value GHCR_ACTOR
  docker_config_dir=$(mktemp -d)
  DOCKER_CONFIG=$docker_config_dir
  export DOCKER_CONFIG
  printf '%s' "$GHCR_TOKEN" |
    docker login ghcr.io --username "$GHCR_ACTOR" --password-stdin >/dev/null
  unset GHCR_TOKEN GHCR_ACTOR
fi

compose config --quiet

previous_container=$(compose ps --all --quiet app)
previous_image=""
if [ -n "$previous_container" ]; then
  previous_image=$(docker inspect --format '{{.Config.Image}}' "$previous_container")
fi

echo "${environment}에 ${IMAGE_REFERENCE} 배포를 시작합니다."
compose pull app

deploy_succeeded=true
if ! compose up --detach --no-deps app; then
  deploy_succeeded=false
elif ! wait_until_healthy; then
  deploy_succeeded=false
fi

if [ "$deploy_succeeded" = true ]; then
  echo "배포가 완료되었습니다."
  docker image prune --force >/dev/null
  exit 0
fi

echo "오류: 새 버전의 배포 또는 헬스체크에 실패했습니다." >&2
compose logs --tail 100 app >&2 || true

if [ -z "$previous_image" ] || [ "$previous_image" = "$IMAGE_REFERENCE" ]; then
  echo "오류: 롤백할 이전 이미지가 없습니다." >&2
  exit 1
fi

echo "이전 이미지로 롤백합니다: ${previous_image}" >&2
IMAGE_REFERENCE=$previous_image
export IMAGE_REFERENCE

if compose up --detach --no-deps app && wait_until_healthy; then
  echo "롤백이 완료되었습니다. 새 버전 배포는 실패로 처리합니다." >&2
else
  echo "오류: 롤백 후에도 서비스가 정상화되지 않았습니다." >&2
  compose logs --tail 100 app >&2 || true
fi

exit 1
