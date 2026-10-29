# firmbanking-mock

대출 상환 시스템(Loan Repayment System)의 Mock 펌뱅킹 서버

캐피탈 서버(`repayment-backend`)가 이체할 때 전문을 보내는 상대다. 실제 구조의 VAN/금융망 → 은행 대외계 → 은행 계정계를 이 서버 하나가 대신하고, 계좌 DB도 이 서버가 가진다.

## 빌드 · 실행

```bash
cp .env.sample .env
docker compose up -d      # 로컬 MySQL (빈 DB. 스키마는 앱이 뜰 때 Flyway가 넣는다 — §11)
./gradlew bootRun         # 전문 TCP 9001, 기본 프로필 local
./gradlew build           # 컴파일 + 테스트 + jar 패키징 — Docker 필요(Testcontainers)
./gradlew test            # 테스트만
```

`repayment-backend`와 함께 띄우므로 포트를 겹치지 않게 둔다(MySQL 3307, 전문 9001).

---

## 1. 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 (Groovy DSL) |
| 영속성 | MyBatis (스타터 3.0.5) |
| DB | MySQL 8.4 |
| 마이그레이션 | Flyway |
| 전문 통신 | TCP 소켓(java.net) + 스레드 풀 |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers(MySQL) |

---

## 2. 패키지 구조


```
com.firmbanking
├─ FirmbankingApplication
├─ telegram/                        # 전문 수신(TCP). 업무 로직은 두지 않는다
│  ├─ TelegramServer                # 포트를 열고 연결마다 TelegramHandler를 스레드 풀에 넘긴다
│  ├─ TelegramHandler               # 연결 하나: 읽기 → 디스패치 → 쓰기 → 닫기
│  ├─ TelegramDispatcher            # 거래 선택(A002), 업체 확인(A003), 예상 못한 예외 → 9999
│  ├─ codec/                        # 바이트 ↔ 전문. EUC-KR, N/AN, 형식 오류(A001)
│  └─ message/                      # 거래별 개별부 ↔ 도메인 DTO
├─ domain/
│  └─ {도메인}/                       # transfer, consent, account
│     ├─ service/
│     ├─ mapper/                    # MyBatis 인터페이스
│     └─ dto/{request,response}/
└─ global/
   ├─ code/                         # ResponseCode, 코드성 테이블 enum
   ├─ config/
   └─ util/

src/main/resources/mapper/{도메인}/{대상}Mapper.xml
```

- 도메인은 업무 기능 단위로 나누고, 구현하면서 정한다. 테이블 단위로 나누지 않는다.
- 코드성 테이블 enum은 여러 기능이 함께 쓰므로 도메인이 아니라 `global/code/`에 모은다.
- `domain`은 `telegram`을 참조하지 않는다. 전문 객체는 `message/`에서 도메인 DTO로 바꿔 서비스에 넘긴다.
- 서비스는 인터페이스 없이 클래스 하나로 만든다.
- 다른 도메인의 매퍼는 조회만 직접 호출할 수 있다.

---

## 3. 네이밍 컨벤션

| 대상 | 규칙 | 예 |
|---|---|---|
| 패키지 | 소문자·단수 | `com.firmbanking.domain.account` |
| 서비스 | `{대상}Service` | `TransferService` |
| 거래별 전문 변환 | `{거래}Message` | `NameInquiryMessage` |
| 매퍼 인터페이스 / XML | `{대상}Mapper` / `resources/mapper/{도메인}/{대상}Mapper.xml` | `mapper/account/AccountMapper.xml` |
| 요청 DTO | `{동작}{대상}Request` | `CreateAccountRequest` |
| 응답 DTO | `{대상}{용도}Response` | `AccountDetailResponse` |
| 응답코드 | 전역 단일 enum `ResponseCode`의 상수. 이름은 영어, 코드 값은 필드 | `ResponseCode.INSUFFICIENT_BALANCE("B003")` |
| 매퍼 메서드 | 조회 `find*`/`count*`/`exists*`, 변경 `insert*`/`update*`/`delete*` | `findByAccountNo` |
| enum 상수 | 영어 대문자. 코드성 테이블의 **id**와 1:1 (§8) | `TransferType.PAYMENT` |
| DB → 필드 | `snake_case` → `camelCase` | `account_balance` → `accountBalance` |
| 마이그레이션 | `V{버전}__{설명}.sql` | `V1__create_account.sql` |
| 테스트 클래스 | `{대상}Test` / `{대상}IntegrationTest` | `TransferServiceTest` |
| 테스트 메서드 | 한글 + 언더스코어 | `잔액이_부족하면_B003으로_응답한다()` |

---

## 4. DTO 규칙

- Request·Response는 class + Lombok `@Getter @NoArgsConstructor @AllArgsConstructor @Builder`로 만든다. `@Setter`는 쓰지 않는다.
- entity는 만들지 않는다. 매퍼는 Request DTO를 그대로 받고, Response DTO로 바로 반환한다.

---

## 5. 응답코드

- 전문 처리 결과는 응답코드 하나로 나간다. `global/code/ResponseCode`가 관리한다.
- 업무 거절(B·C·D 계열)은 서비스 반환값으로 돌려준다. 이체 거절도 transfer에 남겨야 하기 때문이다.

---

## 6. 예외 처리

- 예외는 전문 형식 오류 하나다. codec이 `InvalidTelegramException`을 던지면 디스패처가 A001로 바꾼다.
- 그 밖의 예외는 디스패처가 9999로 바꾸고 스택트레이스를 남긴다. 업무 거절에는 예외를 쓰지 않는다.

---

## 7. MyBatis 규칙

- 값은 `#{}`로 넘긴다. `${}`는 SQL에 문자열을 그대로 붙여 SQL 인젝션이 생긴다.
- 정렬 컬럼처럼 `#{}`로 안 되는 곳만 `${}`를 쓰고, 허용한 컬럼 이름인지 먼저 확인한다.
- `SELECT *`는 쓰지 않는다. 필요한 컬럼만 적는다.
- `AS` 별칭을 붙이지 않는다. `map-underscore-to-camel-case`가 켜져 있다.
- 매퍼 파라미터가 2개 이상이면 `@Param`을 붙인다.
- 응답 DTO 모양에 따라 매핑 방식을 고른다.

  | DTO 모양 | 매핑 |
  |---|---|
  | 필드가 모두 단순 값 | `resultType` |
  | 다른 DTO를 필드로 가짐 | `resultMap` + `<association>` |
  | 다른 DTO 목록을 필드로 가짐 | `resultMap` + `<collection>` |

- `resultType`을 쓸 때는 DTO 필드를 모두 SELECT한다. 빠진 필드는 에러 없이 `null`이 된다.
- `<association>`, `<collection>`을 쓰면 자동 매핑이 꺼진다. 바깥 DTO의 단순 필드도 `<result>`로 모두 적는다.
- 코드성 테이블 enum은 id로 매핑한다. 공통 TypeHandler를 `global/config/typehandler/`에 만들고
  `mybatis.type-handlers-package`로 등록한다. 그 외 TypeHandler는 만들지 않는다.

---

## 8. 데이터 규칙

- 금액은 `BigDecimal`로 다룬다. `double`, `float`는 쓰지 않는다.
- `BigDecimal`은 `equals` 대신 `compareTo`로 비교한다.
- `created_at`, `updated_at`은 DB가 채우므로 INSERT, UPDATE 문에 적지 않는다.
- 잔액을 바꿀 때는 그 근거 기록(이체 결과)의 INSERT와 한 트랜잭션으로 묶는다.
- 이체 결과 같은 기록 테이블은 지우지도 고치지도 않는다.
- 코드성 테이블의 id는 enum과 1:1이다. 이미 적용된 마이그레이션의 id를 바꾸지 않고, 코드를 추가할 때는 새 마이그레이션과 enum을 함께 추가한다.

---

## 9. 날짜 · 시간

- 타임존은 DB, JDBC, JVM 세 곳에서 `Asia/Seoul`로 맞춘다.
- JVM 옵션(`-Duser.timezone=Asia/Seoul`)은 `build.gradle`의 `bootRun`, `test`에만 걸려 있다. `java -jar`나 운영 배포에도 넣는다.

---

## 10. 환경 설정

- 프로필은 `local`(기본)과 `test`가 있다. `test`는 통합 테스트용이다.
- 로컬 포트나 계정을 바꿀 때는 `.env`만 고친다. docker compose와 Spring이 같은 `.env`를 읽는다.
- 비밀값은 커밋하는 파일에 넣지 않는다. `.env`는 커밋하지 않고, `.env.sample`에는 로컬용 값만 둔다.

---

## 11. DB 마이그레이션 (Flyway)

- 스키마는 앱이 뜰 때 Flyway가 적용한다. SQL을 손으로 실행하지 않는다.
- 이미 적용된 파일은 고치지 않는다. 바꿀 때는 새 버전 파일을 추가한다.
- 스키마 변경과 코드성 데이터는 `db/migration/`에 다음 번호로 추가한다.
- 로컬 DB를 처음부터 다시 만들려면 `docker compose down -v` 후 앱을 다시 띄운다.

---

## 12. 테스트

- 서비스와 계산 로직은 단위 테스트로 검증한다. DB 없이 Mockito나 순수 JUnit을 쓴다.
- 매퍼, Flyway는 통합 테스트로 검증한다. `@SpringBootTest` + `@Import(TestcontainersConfig.class)` + `@ActiveProfiles("test")`를 쓴다.
- 전문 송수신은 통합 테스트에서 실제 소켓으로 전문을 보내 검증한다.
- 단언은 AssertJ로 쓴다. 금액은 `isEqualByComparingTo`로 비교한다.
- 통합 테스트가 컨테이너 기동에서 실패하면 Docker Desktop이 켜져 있는지부터 확인한다.
- 데이터를 바꾸는 통합 테스트는 `@Transactional`로 롤백한다.

### 작성 순서

테스트는 요구사항과 결정 이력을 담은 문서다. 케이스를 사람이 검토하기 전에는 테스트 코드를 쓰지 않는다.

1. 코드를 쓰기 전에 케이스 목록을 먼저 제안한다. 케이스마다 상황(given), 동작(when), 기대 결과(then)를 적는다.
2. 사람이 케이스를 검토하고 확정할 때까지 기다린다. 추가·삭제·수정된 케이스는 반영해서 목록을 다시 보여준다.
3. 확정된 케이스만 코드로 옮긴다. 구현 중에 새 케이스가 필요해지면 멈추고 1번부터 다시 한다.

```
| # | 상황 (given) | 동작 (when) | 기대 결과 (then) |
|---|---|---|---|
| 1 | 출금 계좌 잔액 10,000원 | 20,000원을 이체한다 | 잔액부족 응답코드 |
```

### BDD 스타일

- 테스트 본문은 `// given`, `// when`, `// then` 주석으로 나눈다. `when`은 한 번만 호출한다.
- Mockito는 BDDMockito를 쓴다. `when(...).thenReturn(...)` 대신 `given(...).willReturn(...)`, `verify(...)` 대신 `then(...).should()`를 쓴다.
- 같은 상황의 케이스는 `@Nested` 클래스로 묶는다.

### @DisplayName

- 테스트 클래스, `@Nested` 클래스, 테스트 메서드에 모두 `@DisplayName`을 붙인다. 테스트 결과를 위에서부터 읽으면 명세처럼 읽혀야 한다.
- 테스트 클래스는 대상, `@Nested`는 상황("~일 때"), 메서드는 조건과 결과를 담은 문장("~하면 ~한다")으로 적는다.
- 숫자와 결과를 문장에 그대로 적는다. "정상 동작한다", "예외가 발생한다"처럼 무엇인지 알 수 없는 문장은 쓰지 않는다.
- 근거(전문 규격 항목 등)는 메서드 위 한 줄 주석으로 남긴다.
- 메서드 이름은 §3 네이밍을 따른다.

---

## 13. 참고 자료

- 펌뱅킹 개요: https://developers.hectofinancial.co.kr/docs/firm-banking/00-overview
- 펌뱅킹 전문 API: https://developers.hectofinancial.co.kr/docs/api/firm-banking
- 캐피탈 서버: https://github.com/Loan-Repayment-System/repayment-backend

---

## 14. Git / GitHub 워크플로우

### 브랜치 · 커밋

- `main`은 배포 브랜치, `develop`은 기본 브랜치다. 둘 다 직접 push하지 않는다.
- 작업 브랜치는 `develop`에서 만들고 `develop`으로 PR한다. 병합은 Squash and Merge다.
- 릴리스는 사람이 `develop` → `main` PR을 만들어 Merge commit으로 병합한다.
- 브랜치 이름은 `{type}/{설명}`이고 영어 소문자와 하이픈만 쓴다.
  type은 `feat`, `fix`, `docs`, `chore`, `style`, `refactor`, `test`, `perf`, `ci` 중 하나다.
- 커밋 메시지는 `type: 한국어 설명`이다. `Co-Authored-By` 트레일러는 붙이지 않는다.
- 작업과 무관한 파일은 같이 커밋하지 않는다.

### 작업 순서

1~6단계는 확인 없이 이어서 진행한다. 7단계는 사람과 함께 한다.

1. 이슈 등록: `gh issue create --repo Loan-Repayment-System/firmbanking-mock`
2. 브랜치 생성: `git checkout develop && git pull origin develop && git checkout -b {type}/{설명}`
3. 구현 후 `./gradlew build`가 통과하는지 확인한다(Docker 필요).
4. 커밋
5. push: `git push -u origin {브랜치명}`
6. PR 생성: push 직후 Claude가 `.github/PULL_REQUEST_TEMPLATE.md` 형식으로 초안 본문을 써서 `gh pr create --base develop`으로 바로 올린다. 방향을 먼저 묻지 않는다.
   제목은 `[#이슈번호] type: 한국어 설명`이고, 마일스톤은 PR에 연결한다.
7. 검토: 사람이 GitHub에서 코드와 본문을 검토하고 말로 수정을 지시한다. Claude는 최신 본문을 읽고 해당 부분만 고친다(`gh pr edit`). 병합은 사람이 한다.

   PR 본문에 'Generated with Claude Code' 같은 문구는 붙이지 않는다.

큰 작업은 마일스톤으로 묶고, 하위 작업마다 이 순서를 반복한다.

### 이슈 작성

제목은 `[TYPE] 한국어 설명`이고, 접두사에 맞는 타입 라벨을 반드시 붙인다.

| 접두사 | 라벨 | 본문 섹션 |
|---|---|---|
| `[BUG]` | `🐛 버그` | 버그 설명 / 재현 방법 / 예상 동작 / 실제 동작 / 스크린샷 / 환경 / 추가 정보 |
| `[FEAT]` | `✨ 기능` | 기능 요약 / 배경 및 이유 / 구현 방법 나열 / 추가 정보 |
| `[TASK]` | `🛠️ 작업` | 작업 내용 / 작업 목표 / 세부 작업 목록(체크박스) / 참고 자료 / 완료 조건 |
| `[REFACTOR]` | `🧹 리팩터링` | 리팩토링 대상 / 현재 문제점 / 개선 방향 / 예상 영향 범위 / 주의사항 |
| `[DOCS]` | `📝 문서` | 문서 작업 내용 / 작업 이유 / 작업 범위(체크박스) / 참고 자료 |
| `[QUESTION]` | (없음) | 질문 내용 / 배경 / 시도해본 것 / 참고 자료 |
| `[LEARN]` | `📚 학습` | 무엇을 했나 / 왜 이렇게 했나 / 어떻게 동작하나 / 몰랐다가 알게 된 것 / 참고 사항 |

- 해당되면 우선순위·상태·영역 라벨을 더 붙인다. 라벨 목록과 설명은 `gh label list`로 본다.
- 마일스톤은 이슈에 붙이지 않고 PR에만 연결한다.
