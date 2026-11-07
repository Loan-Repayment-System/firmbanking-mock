#!/usr/bin/env bash
# 부하 측정용 EC2 3대(k6·앱·DB)와 보안 그룹·키 페어를 만든다. 개발 PC에서 실행한다.
#
#   ./load-test/infra/create.sh
#
# 앱 서버가 이미지 5개를 빌드하느라 끝날 때까지 5분쯤 걸린다. 끝나면 k6 서버 접속 방법을 출력한다.
# 켜 둔 시간만큼 요금이 나가므로 다 쓰면 destroy.sh로 지운다. 중간에 실패해도 destroy.sh로 지우고 다시 실행한다.
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$INFRA_DIR/common.sh"

INSTANCE_TYPE=c7i-flex.large
AMI_PARAMETER=/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64

tags() {
  local type="$1" name="$2"
  echo "ResourceType=$type,Tags=[{Key=Name,Value=$name},{Key=Project,Value=$PROJECT}]"
}

create_sg() {
  local role="$1"
  aws ec2 create-security-group --group-name "$PROJECT-$role" --description "$PROJECT $role" --vpc-id "$VPC_ID" \
    --tag-specifications "$(tags security-group "$PROJECT-$role")" --query GroupId --output text
}

# 출발지가 sg-로 시작하면 보안 그룹, 아니면 CIDR로 연다.
allow() {
  local group="$1" port="$2" source="$3"
  if [[ "$source" == sg-* ]]; then
    aws ec2 authorize-security-group-ingress --group-id "$group" --protocol tcp --port "$port" --source-group "$source" >/dev/null
  else
    aws ec2 authorize-security-group-ingress --group-id "$group" --protocol tcp --port "$port" --cidr "$source" >/dev/null
  fi
}

run_instance() {
  local role="$1" group="$2" disk_gb="$3"
  aws ec2 run-instances --image-id "$AMI_ID" --instance-type "$INSTANCE_TYPE" --key-name "$KEY_NAME" \
    --subnet-id "$SUBNET_ID" --security-group-ids "$group" \
    --block-device-mappings "DeviceName=/dev/xvda,Ebs={VolumeSize=$disk_gb,VolumeType=gp3,DeleteOnTermination=true}" \
    --user-data "file://$INFRA_DIR/user-data/$role.sh" \
    --tag-specifications "$(tags instance "$PROJECT-$role")" "$(tags volume "$PROJECT-$role")" \
    --query 'Instances[0].InstanceId' --output text
}

instance_field() {
  aws ec2 describe-instances --instance-ids "$1" --query "Reservations[0].Instances[0].$2" --output text
}

# 앱·DB 서버는 k6 서버에서만 SSH로 열리므로 k6 서버를 거쳐 명령을 보낸다.
on_k6() {
  ssh -i "$KEY_FILE" -o UserKnownHostsFile="$KNOWN_HOSTS" -o StrictHostKeyChecking=accept-new \
    -o BatchMode=yes -o ConnectTimeout=5 -o ServerAliveInterval=30 "ec2-user@$K6_IP" "$@"
}

wait_ssh() {
  local name="$1"
  shift
  for _ in $(seq 60); do
    if "$@" >/dev/null 2>&1; then
      return
    fi
    sleep 5
  done
  log "$name 서버에 5분 안에 SSH로 접속하지 못했다"
  exit 1
}

wait_install() {
  local name="$1"
  shift
  log "설치 대기: $name"
  if ! "$@" >/dev/null; then
    log "$name 서버 설치가 실패했다. 그 서버의 /var/log/cloud-init-output.log를 본다"
    exit 1
  fi
}

# 같은 태그의 EC2가 남아 있으면 두 벌이 떠서 요금이 두 배가 된다.
existing="$(aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=$PROJECT" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].InstanceId' --output text)"
if [ -n "$existing" ]; then
  log "이미 떠 있는 EC2가 있다: $existing. destroy.sh로 지운 뒤 다시 실행한다"
  exit 1
fi

log "계정 $(aws sts get-caller-identity --query Account --output text), 리전 $AWS_REGION"
read -r SUBNET_ID VPC_ID < <(aws ec2 describe-subnets \
  --filters Name=default-for-az,Values=true "Name=availability-zone,Values=${AWS_REGION}a" \
  --query 'Subnets[0].[SubnetId,VpcId]' --output text)
AMI_ID="$(aws ssm get-parameter --name "$AMI_PARAMETER" --query Parameter.Value --output text)"
MY_IP="$(curl -fsS https://checkip.amazonaws.com)"
log "서브넷 $SUBNET_ID, AMI $AMI_ID, 내 IP $MY_IP"

log "키 페어"
rm -f "$KEY_FILE" "$KNOWN_HOSTS"
aws ec2 create-key-pair --key-name "$KEY_NAME" --key-type ed25519 --tag-specifications "$(tags key-pair "$KEY_NAME")" \
  --query KeyMaterial --output text > "$KEY_FILE"
chmod 400 "$KEY_FILE"

log "보안 그룹"
K6_SG="$(create_sg k6)"
APP_SG="$(create_sg app)"
DB_SG="$(create_sg db)"
allow "$K6_SG" 22 "$MY_IP/32"
allow "$APP_SG" 22 "$K6_SG"
allow "$APP_SG" 9001 "$K6_SG"
allow "$DB_SG" 22 "$K6_SG"
allow "$DB_SG" 3307 "$K6_SG"
allow "$DB_SG" 3307 "$APP_SG"

log "EC2"
K6_ID="$(run_instance k6 "$K6_SG" 10)"
APP_ID="$(run_instance app "$APP_SG" 20)"
DB_ID="$(run_instance db "$DB_SG" 10)"
aws ec2 wait instance-running --instance-ids "$K6_ID" "$APP_ID" "$DB_ID"
K6_IP="$(instance_field "$K6_ID" PublicIpAddress)"
APP_IP="$(instance_field "$APP_ID" PrivateIpAddress)"
DB_IP="$(instance_field "$DB_ID" PrivateIpAddress)"
log "k6 $K6_IP, 앱 $APP_IP, DB $DB_IP"

# run.sh가 BatchMode로 앱·DB에 SSH하므로 키를 두고, 처음 보는 호스트 키는 묻지 않고 받는다.
log "k6 서버에 앱·DB 접속 설정"
wait_ssh k6 on_k6 true
on_k6 'cat > ~/.ssh/id_ed25519 && chmod 600 ~/.ssh/id_ed25519' < "$KEY_FILE"
on_k6 'cat > ~/.ssh/config && chmod 600 ~/.ssh/config' <<EOF
Host $APP_IP $DB_IP
  StrictHostKeyChecking accept-new
  BatchMode yes
  ConnectTimeout 5
  ServerAliveInterval 30
EOF
on_k6 'cat > ~/load.env' <<EOF
APP_HOST=$APP_IP
DB_HOST=$DB_IP
APP_SSH=ec2-user@$APP_IP
DB_SSH=ec2-user@$DB_IP
EOF
wait_ssh 앱 on_k6 "ssh ec2-user@$APP_IP true"
wait_ssh DB on_k6 "ssh ec2-user@$DB_IP true"

wait_install k6 on_k6 'cloud-init status --wait'
wait_install DB on_k6 "ssh ec2-user@$DB_IP 'cloud-init status --wait'"
wait_install 앱 on_k6 "ssh ec2-user@$APP_IP 'cloud-init status --wait'"

cat <<EOF

준비됐다. k6 서버에 접속해 tmux 안에서 run.sh를 실행한다.
  ssh -i $KEY_FILE -o UserKnownHostsFile=$KNOWN_HOSTS ec2-user@$K6_IP
  tmux new -s load
  cd ~/firmbanking-mock && set -a && . ~/load.env && set +a
  METHOD=named-lock MYSQL_ROOT_PASSWORD=... MYSQL_PASSWORD=... ./load-test/run.sh

결과를 이 PC로 가져온 뒤 지운다.
  scp -r -i $KEY_FILE -o UserKnownHostsFile=$KNOWN_HOSTS ec2-user@$K6_IP:firmbanking-mock/load-test/results load-test/
  ./load-test/infra/destroy.sh
EOF
