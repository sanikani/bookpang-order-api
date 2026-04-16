# Project

## 프로젝트 개요
- 프로젝트명: 온라인 서점 북팡문고
- 기간: 2025.06~2025.07
- 기술 스택: Java 21, Spring Boot 3.4, Spring Data JPA, RabbitMQ, Querydsl, OpenFeign, Swagger, JUnit 5, Mockito, Awaitility, H2, MySQL
- 참여 인원: 8인
- 서비스 설명: 온라인 서점의 주문 API로 주문 생성, 결제 승인·취소, 재고 차감·복원, 쿠폰 적용·복구, 포인트 적립·환급, 미결제 주문 자동 정리를 담당

---

## 프로젝트 핵심 경험 (분석 결과)

1.
- 문제: 동시 주문이 몰릴 때 같은 도서 재고를 여러 트랜잭션이 동시에 갱신해 재고 불일치가 발생할 수 있었다.
- 원인: 재고 엔티티가 단일 행으로 관리되고, 주문 프로세스 안에서 동일 ISBN에 대한 동시 업데이트 충돌이 발생할 수 있는 구조였다.
- 해결: `BookStock`에 `@Version`을 적용하고 `StockService`에 `@Retryable`(최대 5회, 지수 백오프)과 `@Recover`를 구현했으며, 주문 트랜잭션 초기에 재고를 먼저 차감하도록 배치했다.
- 결과: `StockServiceConcurrencyTest`에서 10스레드 동시 차감 시 최종 재고가 성공 건수 기준 기대값과 일치했고, 부족 재고 상황에서도 음수 재고가 발생하지 않음을 검증했다.
- 기술: JPA Optimistic Lock, Spring Retry, `@Transactional`, `@Version`, `@Recover`

2.
- 문제: 주문 트랜잭션 안에서 포인트 적립을 동기 처리하면 주문 커밋이 후속 포인트 처리 장애에 영향을 받을 수 있고, 비동기 처리 실패 시 유실 추적도 어려웠다.
- 원인: 포인트 적립은 사용자 포인트 조회·생성, 정책 계산, 이력 저장까지 수반하는 후속 작업이라 주문 완료 흐름과 강하게 결합되면 장애 전파 범위가 커졌다.
- 해결: `@TransactionalEventListener(AFTER_COMMIT)`로 주문 커밋 이후에만 포인트 적립 이벤트를 발행하고, RabbitMQ 소비자에 DLQ·parking-lot·fatal message logging을 추가해 retryable/fatal 예외를 분리 처리했다.
- 결과: `PointServiceQueueConcurrencyTest`에서 50건 동시 적립 후 총 500,000포인트가 누락 없이 적립되고 버전이 50까지 증가함을 검증했으며, `FatalExceptionLogTest`로 치명 메시지 로그 저장도 확인했다.
- 기술: Spring Event, RabbitMQ, DLQ, parking-lot queue, Awaitility, `@RabbitListener`, `@TransactionalEventListener`

3.
- 문제: 결제 수단이 늘어날수록 승인·취소 로직 분기와 외부 PG 연동 코드가 한 서비스에 누적될 수 있었다.
- 원인: 카드 결제와 Toss 결제는 승인·취소 방식이 달라 공통 서비스에서 직접 분기하면 변경 영향 범위가 커지는 구조였다.
- 해결: `ExternalPaymentService` 전략 인터페이스와 `ExternalPaymentServiceFactory`를 도입해 `CARD`, `TOSS` 구현체를 맵 기반으로 선택하도록 구성했다.
- 결과: `PaymentService`는 공통 오케스트레이션만 담당하고, 실제 승인·취소는 결제수단별 구현체로 분리되어 결제 수단 2종을 동일 진입점으로 처리하는 확장 구조를 확보했다.
- 기술: Strategy Pattern, Factory Pattern, Spring DI, Java `HttpClient`

4.
- 문제: 주문 생성 플로우에 재고 차감, 가격 계산, 할인 적용, 회원·비회원 분기, 포인트 적립, 주문 만료 후속 처리가 한 흐름에 얽혀 복잡도가 높았다.
- 원인: 주문 생명주기 전반의 책임이 뭉치면 정책 변경이나 예외 케이스 추가 시 수정 범위가 넓어지는 구조였다.
- 해결: 주문 생성은 `OrderProcessService`, 상태 변경은 `OrderStatusService`, 환불/복구는 `RefundService`, 가격 계산은 `PricingService`, 회원·비회원 정책은 `OrderProcessorFactory`와 2개 Processor로 분리했다.
- 결과: 주문 생명주기를 5개 핵심 컴포넌트와 2개 주문 Processor로 나눠 정책 변경 범위를 역할 단위로 국소화했고, 주문 만료 큐 전송도 별도 서비스로 분리했다.
- 기술: Service Layer 분리, Strategy Pattern, Factory Pattern, Transaction Script 분해

5.
- 문제: 주문·포인트·쿠폰 도메인에서 금액을 primitive로 다루면 음수 금액, 부족 금액, 비율 계산 같은 규칙이 중복되고 실수 여지가 커졌다.
- 원인: 금액 연산과 검증 규칙이 도메인 곳곳에 흩어지면 동일 규칙을 반복 구현해야 하고 예외 처리 일관성도 깨질 수 있었다.
- 해결: `Money` VO를 도입해 합산, 차감, 곱셈, 나눗셈, 비교, 퍼센트 계산과 예외 규칙을 한 곳에 모으고 주문·포인트·쿠폰 도메인에 내재화했다.
- 결과: `MoneyTest` 39개 케이스로 금액 규칙을 고정했고, 3개 도메인에서 동일한 금액 모델을 재사용해 중복 검증 로직을 줄였다.
- 기술: Value Object, `@Embeddable`, Domain Validation

6.
- 문제: 결제 페이지 이탈 시 미결제 주문이 재고·쿠폰을 점유한 채 남아 후속 주문 정합성을 해칠 수 있었다.
- 원인: 주문 생성 이후 결제가 완료되지 않아도 주문이 `BEFORE_PAYMENT` 상태로 남을 수 있고, 별도 정리 로직이 없으면 점유 자원이 해제되지 않는 구조였다.
- 해결: 주문 생성 시 `OrderQueueService`가 RabbitMQ로 만료 메시지를 발행하고, TTL 만료 후 `OrderDelayConsumer`가 `BEFORE_PAYMENT` 상태인 주문만 `refundAndCleanup` 하도록 구현했다.
- 결과: 현재 코드 기준 TTL 50초 이후 미결제 주문을 자동 취소하고 재고·쿠폰 복구를 수행하도록 구성해 수동 배치 없이 이탈 주문 정리를 자동화했다.
- 기술: RabbitMQ TTL, DLX, `@RabbitListener`, 주문 상태 가드

7.
- 문제: 동시성·비동기·도메인 규칙 중심 로직은 회귀 버그를 재현하기 어렵고 수정 시 안정성을 확인하기 힘들다.
- 원인: 포인트 적립, 재고 차감, 금액 계산, 주문 상태 변경이 각각 트랜잭션·메시지 큐·도메인 검증에 걸쳐 있어 단일 테스트 방식으로는 검증 범위가 부족했다.
- 해결: 단위 테스트, `SpringBootTest` 기반 통합 테스트, Awaitility 기반 비동기 검증을 함께 구성해 도메인/서비스/컨트롤러/비동기 시나리오를 분리 검증했다.
- 결과: 현재 저장소 기준 56개 테스트 파일과 450개 테스트 케이스로 락 충돌, MQ 소비, 금액 규칙, 상태 전이를 회귀 검증 가능한 구조를 갖췄다.
- 기술: JUnit 5, Mockito, Spring Boot Test, Awaitility

---

## 핵심 성과

- 동시 주문으로 재고 불일치가 발생할 수 있는 문제 → `BookStock`에 `@Version` 낙관적 락과 `StockService`의 `@Retryable`(최대 5회, 지수 백오프)·`@Recover`를 적용하고 재고 차감을 주문 트랜잭션 초기에 배치 → 10스레드 동시 차감 테스트에서도 최종 재고와 성공 건수가 일치하도록 데이터 정합성 보장
- 주문 커밋 이전에 포인트 적립을 동기 처리하면 후속 장애가 주문 완료 흐름에 전파되는 문제 → `@TransactionalEventListener(AFTER_COMMIT)` + RabbitMQ 소비 구조로 분리하고 fatal/retryable 예외를 DLQ·parking-lot으로 분기 → 50건 동시 적립 테스트에서 500,000포인트 누락 없이 적립하고 치명 메시지는 별도 로그로 추적 가능하게 개선
- 결제 수단 추가마다 승인·취소 분기 로직이 커지는 문제 → `ExternalPaymentService` Strategy와 `ExternalPaymentServiceFactory`를 도입해 `CARD`, `TOSS` 구현체를 맵 기반으로 선택 → `PaymentService` 변경 없이 결제수단 2종의 승인·취소 흐름을 확장 가능한 구조로 정리
- 주문 생성 플로우에 재고, 가격, 할인, 회원·비회원 정책, 포인트, 만료 처리가 얽히는 문제 → `OrderProcessService`, `OrderStatusService`, `RefundService`, `PricingService`, `OrderProcessorFactory`로 책임을 분리하고 회원·비회원 2개 Processor를 분리 → 주문 생명주기를 5개 핵심 컴포넌트와 2개 주문 Processor로 나눠 변경 범위를 국소화
- 주문·포인트·쿠폰 도메인에서 금액 규칙이 중복되는 문제 → `Money` VO를 도입해 합산·차감·비율 계산과 검증 로직을 공통화 → 39개 단위 테스트로 금액 규칙을 고정하고 3개 도메인에서 일관된 금액 모델을 재사용
- 결제 페이지 이탈 시 미결제 주문이 재고·쿠폰을 점유한 채 남는 문제 → 주문 생성 시 RabbitMQ TTL 메시지를 발행하고 만료 후 `OrderDelayConsumer`가 `BEFORE_PAYMENT` 상태만 자동 정리 → 현재 코드 기준 50초 TTL 이후 미결제 주문을 자동 취소하고 재고·쿠폰 복구를 배치 없이 처리
- 비동기·동시성 로직은 회귀 버그를 재현하기 어려운 문제 → `SpringBootTest`, Mockito, Awaitility를 조합해 도메인/서비스/비동기 시나리오 테스트를 분리 구축 → 저장소 기준 56개 테스트 파일과 450개 테스트로 락 충돌, MQ 소비, 금액 규칙을 회귀 검증 가능하게 구성

---

## 상세 기술 분석

### 1. 트랜잭션 및 데이터 정합성
- `OrderProcessService`는 주문상품 생성 → 재고 차감 → 가격 계산 → 할인 적용 → 주문 저장 → 주문 만료 큐 전송 순으로 처리해 핵심 상태 변경을 한 흐름에 묶는다.
- `PaymentService`, `OrderStatusService`, `PointServiceImpl`, `CouponService`, `UserCouponService`, `StockService` 등 쓰기 경로에는 `@Transactional`이 적용돼 있다.
- `OrderQueryService`, `OrderFormService`, `PricingService`, `OrderTotalPriceService` 등 조회 경로는 `@Transactional(readOnly = true)`로 분리돼 있다.
- `PointEventListener`는 `@TransactionalEventListener(phase = AFTER_COMMIT)`를 사용해 주문 커밋 이후에만 포인트 적립 메시지를 발행한다.
- `OrderDelayConsumer`, `PointEarnConsumer`, `CouponIssueConsumer`도 소비자 측 트랜잭션으로 메시지 처리와 DB 반영을 함께 묶는다.
- 주문 취소·반품 시 `RefundService`가 재고·쿠폰 복구를 담당하고, `OrderStatusService`가 포인트 환급 및 결제 취소까지 조합해 후처리 정합성을 맞춘다.

### 2. 동시성 제어
- `BookStock`과 `UserPoint`는 각각 `@Version` 필드를 사용해 낙관적 락 기반 동시성 제어를 적용한다.
- `StockService.decreaseStock/increaseStock`는 `OptimisticLockingFailureException`에 대해 최대 5회 재시도와 지수 백오프를 설정한다.
- 재시도 소진 시 `@Recover`가 `StockProcessingFailedException`으로 전환해 실패를 명시적으로 드러낸다.
- `StockServiceConcurrencyTest`는 10스레드 동시 차감 시 최종 재고와 성공 건수 기반 기대값 일치를 검증하고, 부족 재고 시 음수 재고 미발생도 확인한다.
- `PointServiceQueueConcurrencyTest`는 50개 적립 메시지 동시 발행 후 총 500,000포인트 적립과 버전 증가를 `Awaitility`로 검증한다.

### 3. 비동기 처리 / 메시지 큐
- 포인트 적립은 주문 트랜잭션 커밋 이후 이벤트 발행 → RabbitMQ 전송 → 소비자 적립으로 비동기 분리돼 있다.
- 주문 생성 시 `OrderQueueService`가 만료용 메시지를 발행하고, TTL 만료 후 `OrderDelayConsumer`가 `BEFORE_PAYMENT` 주문만 자동 정리한다.
- 포인트·쿠폰 큐는 DLX와 parking-lot queue를 구성해 실패 메시지 경로를 분리한다.
- 포인트 큐는 fatal 예외를 즉시 ACK 후 로그로 저장하고, retryable 예외는 DLQ로 보내 최대 10회 재시도 후 parking-lot으로 이동시킨다.
- `FatalExceptionLogTest`는 잘못된 포인트 메시지 입력 시 치명 메시지 로그가 저장됨을 검증한다.

### 4. 아키텍처 및 설계 패턴
- 결제는 `ExternalPaymentService` 전략 인터페이스와 `ExternalPaymentServiceFactory`로 `CARD`, `TOSS` 구현체를 선택한다.
- 주문 생성은 `OrderProcessorFactory`를 통해 회원/비회원 Processor를 분기해 할인과 포인트 정책을 분리한다.
- 주문 생명주기는 `OrderProcessService`, `OrderStatusService`, `RefundService`, `PricingService`, `OrderProductCreateService`, `OrderQueueService`로 역할이 나뉘어 있다.
- 포인트 이력 저장은 `PointHistoryWriter`로 분리돼 소비자 로직의 책임을 줄인다.
- 등급별 포인트 정책 조회는 `QuerydslRepositorySupport` 기반 구현체로 분리돼 있다.

### 5. 성능 최적화
- 현재 코드에는 캐시 계층이 확인되지 않는다.
- 코드로 확인 가능한 최적화는 재고 차감을 주문 트랜잭션 초기에 배치해 낙관적 락 충돌 구간을 줄인 점과, 포인트 적립을 커밋 후 비동기 처리로 분리한 점이다.
- 저장소에는 정량 성능 측정 결과가 없으므로 성능 향상 수치는 현재 이력서에서 제외하는 것이 타당하다.
- 추후 측정 후보: 재고 차감 충돌률과 재시도 성공률
- 추후 측정 후보: 포인트 적립 큐 처리 지연 시간과 초당 처리량
- 추후 측정 후보: 주문 TTL 만료 후 자동 정리까지의 실제 소요 시간
- 추후 측정 후보: 결제 수단별 승인·취소 응답 시간

### 6. 기타 기술적 강점
- 금액은 `Money` VO로 통합돼 주문·포인트·쿠폰 도메인에서 재사용된다.
- `MoneyTest` 39개 케이스로 합산, 차감, 배수, 비교, 비율 계산, 예외 규칙을 검증한다.
- 예외는 `ApiException`과 `ControllerAdvice`로 HTTP 상태와 메시지 변환을 일관되게 처리한다.
- 테스트는 단위/통합을 함께 사용하며 현재 저장소 기준 56개 테스트 파일, 450개 테스트 케이스가 존재한다.
- `pom.xml`에는 Testcontainers 의존성이 있으나 현재 테스트 코드에서는 실제 사용 흔적이 확인되지 않는다.
