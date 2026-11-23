#!/usr/bin/env bash
set -euo pipefail
# Publish the exact locally built image; never deploy or modify GitOps.
stage=context
trap 'echo "::error::Image verification failed at line $LINENO (stage=$stage)" >&2' ERR
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
test "${GITHUB_REPOSITORY:?}" = nineOuting/nineouting-minutes-service
test "${GITHUB_REF:?}" = refs/heads/dev
test "${GITHUB_EVENT_NAME:?}" = push
test "${EXPECTED_ACCOUNT:?}" = 443845329525
test "${AWS_REGION:?}" = ap-northeast-2
test "${TARGET_IMAGE:?}" = 443845329525.dkr.ecr.ap-northeast-2.amazonaws.com/nineouting-dev-minutes
[[ "${GITHUB_SHA:?}" =~ ^[0-9a-f]{40}$ ]] || { echo "::error::Invalid publish context or digest"; exit 1; }
[[ "${GITHUB_RUN_ID:?}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:?}" =~ ^[0-9]+$ ]] || { echo "::error::Invalid publish context or digest"; exit 1; }
test "${LOCAL_IMAGE:?}" = nineouting-verified:local
tag="sha-${GITHUB_SHA}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
target="$TARGET_IMAGE:$tag"
stage=local-config
archive_dir=$(mktemp -d)
trap 'rm -rf -- "$archive_dir"' EXIT
docker image save "$LOCAL_IMAGE" -o "$archive_dir/local.tar"
local_config=$(python3 "$script_dir/image_config_digest.py" < "$archive_dir/local.tar")
test "$(docker image inspect "$LOCAL_IMAGE" --format '{{.Os}}/{{.Architecture}}')" = linux/amd64
stage=push
docker tag "$LOCAL_IMAGE" "$target"
docker push "$target"
stage=registry-manifest
docker buildx imagetools inspect "$target" > ecr-manifest.txt
digest=$(awk '$1 == "Digest:" {print $2; exit}' ecr-manifest.txt)
[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "::error::Invalid publish context or digest"; exit 1; }
docker buildx imagetools inspect --raw "$TARGET_IMAGE@$digest" > ecr-manifest-raw.json
registry_config=$(jq -er '.config.digest' ecr-manifest-raw.json)
[[ "$registry_config" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "::error::Invalid registry config digest"; exit 1; }
if [[ "$registry_config" != "$local_config" ]]; then
 echo "::error::Registry config mismatch: expected=$local_config actual=$registry_config" >&2
 exit 1
fi
echo "Local/ECR config digest MATCH: $local_config"
stage=pull-and-verify
docker pull --platform linux/amd64 "$TARGET_IMAGE@$digest"
docker image save "$TARGET_IMAGE@$digest" -o "$archive_dir/pulled.tar"
pulled_config=$(python3 "$script_dir/image_config_digest.py" < "$archive_dir/pulled.tar")
if [[ "$pulled_config" != "$local_config" ]]; then
 echo "::error::Pulled config mismatch: expected=$local_config actual=$pulled_config" >&2
 exit 1
fi
test "$(docker image inspect "$TARGET_IMAGE@$digest" --format '{{.Os}}/{{.Architecture}}')" = linux/amd64
stage=verified-output
image_uri="$TARGET_IMAGE@$digest"
printf 'image.repository=%s\nimage.digest=%s\nimage.tag=%s\nimage.uri=%s\n' \
 "$TARGET_IMAGE" "$digest" "$tag" "$image_uri" > image-result.txt
{
 echo "image_repository=$TARGET_IMAGE"
 echo "image_digest=$digest"
 echo "image_uri=$image_uri"
 echo "image_tag=$tag"
} >> "${GITHUB_OUTPUT:?}"
{
 echo '## DEV ECR Publish PASS'
 echo "- ECR image: $image_uri"
 echo '- Local/ECR/pulled config digest: MATCH (validated independently of Docker image-store IDs)'
 echo '- Platform: linux/amd64'
} >> "${GITHUB_STEP_SUMMARY:?}"
