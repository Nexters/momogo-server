# momogo-server

## 실행 프로필

서버를 실행할 때는 `local`, `dev`, `prod` 중 실행 환경에 맞는 프로필을 반드시 지정한다. 기본 프로필만 사용하는 실행은 지원하지 않는다.

로컬에서는 다음과 같이 `local` 프로필을 지정한다.

```sh
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

dev와 prod 배포에서는 Docker Compose가 프로필을 지정하며, 테스트에서는 `test` 프로필을 사용한다.

## JWT 설정

서버 실행 전 아래 환경변수를 설정해야 한다.

- `JWT_SECRET_BASE64`: 최소 32바이트의 암호학적 난수를 표준 Base64로 인코딩한 값이다. 저장소에 원문이나 안전하지 않은 기본값을 두지 않는다.
- `JWT_ISSUER`: access token의 `iss` 값이다. 생략하면 `momogo-server`를 사용한다.

예를 들어 OpenSSL로 secret을 만들려면 `openssl rand -base64 32`를 실행한다. 테스트에서는 `application-test.yml`의 테스트 전용 secret만 사용한다.

## 앱 버전 설정

플랫폼별 최신 버전, 최소 지원 버전과 스토어 URL은 각 프로필의 `application-{profile}.yml`에 있는 `momogo.app-version`에서 관리한다. 최소 지원 버전은 최신 버전보다 높게 설정할 수 없다.

## Cloudflare R2 설정

현재는 사진 API를 구현하지 않고 Cloudflare R2 연결에 필요한 `S3Client`, `S3Presigner`와 환경설정만 구성한다. R2의 S3 API region은 `auto`를 사용한다.

local, dev, prod는 서로 다른 버킷을 사용한다. local의 기본 버킷 이름은 `momogo-local`이며, dev와 prod는 각각의 배포 환경 파일에 서로 다른 `R2_BUCKET`을 설정한다.

- `R2_ENDPOINT`: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` 형식의 R2 S3 API 주소
- `R2_BUCKET`: 현재 환경에서 사용할 버킷 이름
- `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`: 해당 버킷에 접근할 R2 S3 API 인증 정보

local 프로필의 endpoint와 인증 정보 기본값은 애플리케이션 실행을 위한 placeholder다. 실제 R2 연결을 확인하려면 local 전용 버킷과 인증 정보를 환경변수로 전달해야 한다. dev와 prod에서는 네 환경변수가 모두 필수다.

R2 API token은 각 환경의 버킷만 읽고 쓸 수 있도록 범위를 제한하며 실제 인증 정보는 저장소에 커밋하지 않는다. 참고: [Cloudflare R2 S3 API](https://developers.cloudflare.com/r2/api/s3/api/)

## Docker Compose 배포

dev와 prod는 각각 `deploy/compose.dev.yml`, `deploy/compose.prod.yml`을 사용한다. 두 환경 모두 애플리케이션 포트는 호스트의 loopback 주소에만 공개하므로, 외부 요청은 같은 서버의 리버스 프록시를 통해 전달해야 한다.

API 서버에는 저장소를 clone하지 않는다. GitHub Actions가 배포할 때 Compose 파일과 배포 스크립트를 `/opt/momogo/deploy/dev` 또는 `/opt/momogo/deploy/prod`로 전송한다.

환경별 설정과 시크릿은 배포 파일과 분리해 API 서버의 다음 경로에 저장한다.

```text
/opt/momogo/config/
├── dev.env
└── prod.env
```

저장소의 `dev.env.example`과 `prod.env.example`을 참고해 서버에서 파일을 만든 뒤 이미지 저장소, DB 접속 정보, JWT secret과 환경별 R2 설정을 입력한다. 실제 `.env` 파일에는 시크릿이 포함되므로 서버의 배포 사용자만 읽고 수정할 수 있게 설정한다. 이 방식은 구성이 단순하지만 컨테이너 환경변수에 시크릿을 전달하므로 Docker 관리 권한이 있는 사용자는 `docker inspect`로 값을 확인할 수 있다.

배포 스크립트가 `.env` 파일을 셸 설정으로 읽으므로 비밀번호, JWT secret과 R2 인증 정보는 예시처럼 작은따옴표로 감싼다.

```sh
chmod 600 /opt/momogo/config/dev.env
chmod 600 /opt/momogo/config/prod.env
```

서버 관리자는 최초 한 번 `/opt/momogo/config`, `/opt/momogo/deploy/dev`, `/opt/momogo/deploy/prod` 디렉터리를 만들고 SSH 배포 사용자가 쓸 수 있도록 소유권을 설정해야 한다.

GitHub Actions의 `cd-dev.yml`, `cd-prod.yml`은 배포 파일을 SCP로 전송한 후 SSH로 접속해 스크립트를 실행한다. 각 GitHub Environment에 `SERVER_HOST`, `SERVER_PORT`, `SERVER_USER`, `SSH_PRIVATE_KEY`, `SERVER_FINGERPRINT` secret을 등록해야 한다. `SERVER_FINGERPRINT`에는 `ssh-keyscan`으로 확인한 서버 공개 키의 SHA256 지문을 넣어 접속 대상 서버를 검증한다. GHCR 로그인용 토큰은 작업 중 임시 Docker 설정 디렉터리에만 저장되고 배포가 끝나면 삭제된다.

새 컨테이너의 헬스체크가 실패하면 스크립트는 직전 이미지를 다시 실행해 자동으로 롤백하고, Actions 작업은 실패로 남겨 새 버전 배포 실패를 알린다.
