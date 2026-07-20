# momogo-server

## 패키지 규칙

- `presentation`: Controller를 두고 HTTP 요청과 응답 변환을 담당한다.
- `application`: Service를 두고 도메인 흐름과 트랜잭션을 담당한다.
- `domain`: 프레임워크에 의존하지 않는 순수 도메인 객체와 비즈니스 규칙을 둔다.
- `infrastructure.database.entity`: JPA 매핑 클래스를 두며 이름은 `Entity`로 끝낸다.
- `infrastructure.database.repository`: Service가 직접 사용하는 Spring Data `Repository`를 둔다.
- 값 검증은 도메인 객체가 담당하고 JPA Entity에는 비즈니스 로직을 두지 않는다.
- 의존 흐름은 `Controller → Service → Repository` 순서를 따른다.

## 로컬 PostgreSQL 실행

Docker Compose로 PostgreSQL을 실행한다.

```shell
docker compose up -d postgres
```

`local` 프로필로 애플리케이션을 실행한다.

```shell
./gradlew bootRun --args='--spring.profiles.active=local'
```

PostgreSQL에 직접 접속할 때는 컨테이너의 `psql`을 사용한다.

```shell
docker compose exec postgres psql -U momogo -d momogo
```

종료할 때는 다음 명령을 사용한다. 데이터는 Docker 볼륨에 유지된다.

```shell
docker compose down
```
