#!/bin/sh

set -eu

usage() {
  echo "사용법: $0 <dev|prod> <40자리 Git SHA> <브랜치>" >&2
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

environment=${1:-}
image_revision=${2:-}
source_branch=${3:-}

case "$environment" in
  dev)
    deployment_emoji="🧪"
    color=3447003
    ;;
  prod)
    deployment_emoji="🚀"
    color=15158332
    ;;
  *) usage ;;
esac

if [ "${#image_revision}" -ne 40 ] || [ -z "$source_branch" ]; then
  usage
fi

case "$image_revision" in
  *[!0-9a-f]*) usage ;;
esac

require_value DISCORD_WEBHOOK_URL
require_value GITHUB_ACTOR
require_value GITHUB_REPOSITORY
require_value GITHUB_RUN_ID
require_value GITHUB_SERVER_URL
require_value RUNNER_TEMP

repository_url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}"
run_url="${repository_url}/actions/runs/${GITHUB_RUN_ID}"
revision_url="${repository_url}/commit/${image_revision}"
short_revision=$(printf '%s' "$image_revision" | cut -c1-8)
commit_subject=$(git show -s --format='%s' "$image_revision" | cut -c1-100)
if [ -z "$commit_subject" ]; then
  commit_subject="커밋 메시지 없음"
fi
image_repository=$(printf '%s' "$GITHUB_REPOSITORY" | tr '[:upper:]' '[:lower:]')
image_reference="ghcr.io/${image_repository}:${image_revision}"
payload_file="${RUNNER_TEMP}/discord-deployment-payload.json"

jq -n \
  --arg title "${deployment_emoji} ${environment} 서버 기동 완료" \
  --arg environment "$environment" \
  --arg branch "$source_branch" \
  --arg actor "$GITHUB_ACTOR" \
  --arg revision "$short_revision" \
  --arg revision_url "$revision_url" \
  --arg image "$image_reference" \
  --arg commit_subject "$commit_subject" \
  --arg run_url "$run_url" \
  --arg timestamp "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  --argjson color "$color" \
  '{
    username: "momogo-deploy",
    allowed_mentions: {parse: []},
    embeds: [{
      title: $title,
      url: $run_url,
      color: $color,
      fields: [
        {name: "환경", value: $environment, inline: true},
        {name: "브랜치", value: $branch, inline: true},
        {name: "실행자", value: $actor, inline: true},
        {name: "Revision", value: ("[" + $revision + "](" + $revision_url + ")"), inline: true},
        {name: "이미지", value: ("`" + $image + "`"), inline: false},
        {name: "배포 커밋", value: $commit_subject, inline: false},
        {name: "링크", value: ("[Actions 실행](" + $run_url + ")"), inline: false}
      ],
      timestamp: $timestamp
    }]
  }' > "$payload_file"

curl --fail-with-body --silent --show-error \
  -H 'Content-Type: application/json' \
  --data-binary "@${payload_file}" \
  "$DISCORD_WEBHOOK_URL"
