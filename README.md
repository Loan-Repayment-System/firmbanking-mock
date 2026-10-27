# firmbanking-mock

[repayment-backend](https://github.com/Loan-Repayment-System/repayment-backend)(캐피탈 서버)가 이체할 때 호출하는 Mock 펌뱅킹 서버입니다.

## 역할

실제 펌뱅킹은 기업 시스템 → VAN/금융망 → 은행 대외계 → 은행 계정계를 거칩니다.
이 서버는 VAN부터 계정계까지를 하나로 합친 것으로, 고정길이 전문을 받아 자체 계좌 DB에서 이체를 처리하고 결과 전문을 돌려줍니다.

```
repayment-backend ──(전문)──▶ firmbanking-mock (계좌 DB 포함)
```

전문 규격은 [헥토파이낸셜 실시간 펌뱅킹](https://developers.hectofinancial.co.kr/docs/firm-banking/00-overview)을 참고합니다.

## 실행

```bash
cp .env.sample .env
docker compose up -d      # 로컬 MySQL (3307)
./gradlew bootRun         # http://localhost:8081
```
