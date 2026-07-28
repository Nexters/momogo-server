# momogo-server

## JWT 설정

서버 실행 전 아래 환경변수를 설정해야 한다.

- `JWT_SECRET_BASE64`: 최소 32바이트의 암호학적 난수를 표준 Base64로 인코딩한 값이다. 저장소에 원문이나 안전하지 않은 기본값을 두지 않는다.
- `JWT_ISSUER`: access token의 `iss` 값이다. 생략하면 `momogo-server`를 사용한다.

예를 들어 OpenSSL로 secret을 만들려면 `openssl rand -base64 32`를 실행한다. 테스트에서는 `application-test.yml`의 테스트 전용 secret만 사용한다.
