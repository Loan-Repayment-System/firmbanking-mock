#!/usr/bin/env bash
# create.sh가 만든 자원을 모두 지운다. EC2·보안 그룹은 Project 태그로, 키 페어는 이름으로 찾는다.
#
#   ./load-test/infra/destroy.sh
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$INFRA_DIR/common.sh"

instances="$(aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=$PROJECT" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].InstanceId' --output text)"
if [ -n "$instances" ]; then
  log "EC2 종료: $instances"
  aws ec2 terminate-instances --instance-ids $instances >/dev/null
  aws ec2 wait instance-terminated --instance-ids $instances
fi

# 보안 그룹이 서로를 출발지로 참조하므로 인바운드 규칙을 먼저 지워야 그룹이 지워진다.
groups="$(aws ec2 describe-security-groups --filters "Name=tag:Project,Values=$PROJECT" \
  --query 'SecurityGroups[].GroupId' --output text)"
for group in $groups; do
  rules="$(aws ec2 describe-security-group-rules --filters "Name=group-id,Values=$group" \
    --query 'SecurityGroupRules[?!IsEgress].SecurityGroupRuleId' --output text)"
  if [ -n "$rules" ]; then
    aws ec2 revoke-security-group-ingress --group-id "$group" --security-group-rule-ids $rules >/dev/null
  fi
done
for group in $groups; do
  log "보안 그룹 삭제: $group"
  aws ec2 delete-security-group --group-id "$group" >/dev/null
done

log "키 페어 삭제: $KEY_NAME"
aws ec2 delete-key-pair --key-name "$KEY_NAME" >/dev/null
rm -f "$KEY_FILE" "$KNOWN_HOSTS"
log "끝"
