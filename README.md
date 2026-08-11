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

`POST /api/v1/photos/upload-urls`는 클라이언트가 Cloudflare R2에 이미지를 직접 PUT할 수 있는 15분 유효 presigned URL을 발급한다. R2의 S3 API region은 `auto`를 사용한다.

local과 dev는 같은 개발 버킷을 사용하고 오브젝트 키의 `local/`, `dev/` 접두사로 데이터를 구분할 수 있다. prod는 개발 환경과 분리된 버킷을 사용한다. local의 `R2_BUCKET`을 설정하지 않으면 실행용 기본값인 `momogo-local`을 사용한다.

- `R2_ENDPOINT`: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` 형식의 R2 S3 API 주소
- `R2_BUCKET`: 현재 환경에서 사용할 버킷 이름
- `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`: 해당 버킷에 접근할 R2 S3 API 인증 정보

local 프로필의 endpoint와 인증 정보 기본값은 애플리케이션 실행을 위한 placeholder다. 실제 R2 연결을 확인하려면 local 전용 버킷과 인증 정보를 환경변수로 전달해야 한다. dev와 prod에서는 네 환경변수가 모두 필수다.

R2 API token은 각 환경의 버킷만 읽고 쓸 수 있도록 범위를 제한하며 실제 인증 정보는 저장소에 커밋하지 않는다. 참고: [Cloudflare R2 S3 API](https://developers.cloudflare.com/r2/api/s3/api/)

발급 요청의 `Content-Type`은 대소문자를 가리지 않지만, 서명에는 소문자로 정규화한 값을 쓴다. 클라이언트는 응답의 `contentType` 값을 그대로 `Content-Type` 헤더에 지정해 presigned URL로 PUT해야 한다. 버킷의 public access는 필요하지 않으며, 브라우저에서 직접 업로드한다면 R2 버킷 CORS에서 사용하는 origin, PUT 메서드와 `Content-Type` 헤더를 별도로 허용해야 한다.

R2 업로드가 끝나면 `POST /api/v1/photos`에 `objectKey`와 사진을 공유할 `groupIds`를 전달한다. 한 사용자는 그룹별로 하루에 사진 한 장만 등록할 수 있으며, 한 사진을 여러 그룹에 등록하면 선택한 각 그룹의 오늘 업로드를 모두 사용한다. 선택한 그룹 중 하나라도 오늘 업로드를 사용했으면 요청 전체를 거절한다. `DELETE /api/v1/groups/{groupId}/photos/{photoId}`로 사진을 내리면 사진 원본과 다른 그룹의 연결은 유지되고, 해당 그룹의 오늘 업로드만 다시 사용할 수 있다.

dev와 prod는 Hibernate가 DB 스키마를 자동 변경하지 않고 검증만 하므로, 엔티티 변경에 필요한 DDL은 배포 전에 대상 DB에 직접 적용해야 한다.

## 사진 신고 Discord 알림 설정

`POST /api/v1/groups/{groupId}/photos/{photoId}/reports`는 현재 그룹 멤버가 활성 사진을 사유와 함께 신고하는 API다. 서버는 실행 환경(DEV/PROD), 신고자 ID, 그룹 ID, 사진 ID와 사유를 Discord 웹훅으로 전송하고, Discord가 메시지 저장을 확인한 경우에만 성공을 반환한다.

- `PHOTO_REPORT_DISCORD_WEBHOOK_URL`: Discord에서 발급한 전체 incoming webhook URL

웹훅 URL에는 채널에 메시지를 보낼 수 있는 토큰이 포함되므로 저장소나 로그에 남기지 않고 환경별 `.env`에만 저장한다. local 프로필의 기본 URL은 애플리케이션 실행용 placeholder라서 실제 신고 전송에는 환경변수를 지정해야 하며, dev와 prod에서는 필수다. 신고 사유로 예상하지 않은 사용자·역할 멘션이 발생하지 않도록 Discord의 `allowed_mentions`를 비워서 전송한다. 자세한 요청 형식은 [Discord Execute Webhook 문서](https://docs.discord.com/developers/resources/webhook#execute-webhook)를 참고한다.

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
