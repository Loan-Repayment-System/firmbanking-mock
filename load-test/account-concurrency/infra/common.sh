# create.sh·destroy.sh가 같이 쓰는 값. 만든 자원에는 모두 Project 태그를 붙이고 destroy.sh가 그 태그로 찾는다.
export AWS_REGION=ap-southeast-2
PROJECT=firmbanking-mock-load
KEY_NAME=$PROJECT
KEY_FILE="$INFRA_DIR/$PROJECT.pem"
KNOWN_HOSTS="$INFRA_DIR/known_hosts"

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}
