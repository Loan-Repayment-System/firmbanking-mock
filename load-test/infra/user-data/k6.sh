#!/bin/bash
# k6 서버: run.sh를 실행하는 곳이다. 측정 중 SSH가 끊겨도 계속 돌도록 tmux를 넣는다.
set -euxo pipefail

dnf install -y docker git tmux
systemctl enable --now docker
usermod -aG docker ec2-user

sudo -u ec2-user git clone --branch develop https://github.com/Loan-Repayment-System/firmbanking-mock.git /home/ec2-user/firmbanking-mock
docker build -t firmbanking-mock-k6 /home/ec2-user/firmbanking-mock/load-test
# run.sh가 DB 조회·커넥션 수 수집에 mysql 클라이언트 컨테이너를 쓴다.
docker pull mysql:8.4
