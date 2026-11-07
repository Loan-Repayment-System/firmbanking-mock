#!/bin/bash
# DB 서버: run.sh가 실행마다 MySQL 컨테이너를 새로 띄우므로 이미지만 받아 둔다.
set -euxo pipefail

dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

docker pull mysql:8.4
