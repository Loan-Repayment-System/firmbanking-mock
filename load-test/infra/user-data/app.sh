#!/bin/bash
# 앱 서버: 해결 방법 브랜치마다 이미지 firmbanking-mock:{방법}을 빌드해 둔다.
set -euxo pipefail

dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

git clone https://github.com/Loan-Repayment-System/firmbanking-mock.git /opt/firmbanking-mock
cd /opt/firmbanking-mock
for method in synchronized-lock optimistic-lock pessimistic-lock conditional-update named-lock; do
  git checkout "fix/transfer-$method"
  docker build -t "firmbanking-mock:$method" .
done
