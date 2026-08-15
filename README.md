# momogo-server

## 실행 프로필

서버는 `local`, `dev`, `prod` 중 실행 환경에 맞는 활성 프로필이 필요하다. 로컬에서는 다음 명령으로 실행한다.

```sh
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

실행하려면 표준 Base64로 인코딩한 32바이트 이상의 `JWT_SECRET_BASE64`가 필요하다. `JWT_ISSUER`를 생략하면 `momogo-server`를 사용한다. Spring 기반 테스트는 검증 목적에 따라 `test` 또는 `local` 프로필을 명시한다.

## 환경별 설정

- 플랫폼별 최신 버전, 최소 지원 버전과 스토어 URL은 `application-{profile}.yml`의 `momogo.app-version`에서 관리한다. 최소 지원 버전은 최신 버전보다 높을 수 없다.
- dev와 prod는 Hibernate가 DB 스키마를 검증만 한다. 엔티티 변경에 필요한 DDL은 배포 전에 대상 DB에 직접 적용한다.
- local의 R2와 Discord 기본값은 애플리케이션 실행용 placeholder다. 실제 외부 서비스 연동에는 환경변수를 지정해야 한다.

## Cloudflare R2

`POST /api/v1/photos/upload-urls`는 클라이언트가 R2에 이미지를 직접 PUT할 수 있는 15분 유효 presigned URL을 발급한다. R2의 S3 API region은 `auto`를 사용한다.

- `R2_ENDPOINT`: R2 S3 API 주소
- `R2_BUCKET`: 현재 환경에서 사용할 버킷 이름
- `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`: 해당 버킷의 S3 API 인증 정보

local과 dev가 개발 버킷을 공유할 때는 오브젝트 키의 `local/`, `dev/` 접두사로 데이터를 구분한다. prod는 개발 환경과 분리된 버킷을 사용한다. dev와 prod에서는 네 환경변수가 모두 필수다.

업로드 URL 발급 응답의 `contentType`은 서명에 사용된 정규화 값이다. 클라이언트는 이 값을 PUT 요청의 `Content-Type` 헤더에 그대로 사용해야 한다. 버킷의 public access는 필요하지 않으며, 브라우저에서 직접 업로드한다면 R2 CORS에 origin, PUT과 `Content-Type`을 허용한다.

R2 업로드가 끝나면 `POST /api/v1/photos`에 `objectKey`와 `groupIds`를 전달한다. 한 사용자는 그룹별로 하루에 사진 한 장만 등록할 수 있으며, 한 사진을 여러 그룹에 등록하면 선택한 각 그룹의 오늘 업로드를 모두 사용한다. 선택한 그룹 중 하나라도 제한을 사용했으면 전체 요청을 거절한다. `DELETE /api/v1/groups/{groupId}/photos/{photoId}`로 사진을 내리면 사진 원본과 다른 그룹의 연결은 유지되고 해당 그룹의 오늘 업로드만 다시 사용할 수 있다.

## 사진 신고 Discord 알림

`POST /api/v1/groups/{groupId}/photos/{photoId}/reports`는 현재 그룹 멤버가 활성 사진을 신고하는 API다. Discord가 메시지 저장을 확인한 경우에만 성공을 반환한다.

`PHOTO_REPORT_DISCORD_WEBHOOK_URL`에는 환경별 incoming webhook URL을 지정한다. dev와 prod에서는 필수이며, 신고 사유가 사용자나 역할을 멘션하지 않도록 `allowed_mentions`를 비워 전송한다.

## 서비스 이벤트 Discord 알림

회원 가입, 회원 탈퇴, 그룹 생성, 그룹 참여, 그룹 소멸을 Discord로 알린다. 그룹 소멸은 마지막 멤버가 나가 그룹이 삭제될 때만 알리며, 일반적인 그룹 탈퇴는 알리지 않는다.

`SERVICE_EVENT_DISCORD_WEBHOOK_URL`에는 사진 신고와 분리된 webhook URL을 지정한다. dev와 prod에서는 필수다.

알림은 트랜잭션 커밋 이후 별도 스레드에서 전송하므로 롤백된 요청은 알리지 않고, 전송이 실패해도 API 응답에 영향을 주지 않고 경고 로그만 남긴다. 알림 본문에는 사용자 ID와 그룹 ID만 담고 닉네임이나 그룹명 같은 사용자 입력은 보내지 않는다. test 프로필에서는 알림을 비활성화한다.

## Docker Compose 배포

dev와 prod는 각각 `deploy/compose.dev.yml`, `deploy/compose.prod.yml`을 사용한다. 애플리케이션 포트는 호스트의 loopback 주소에만 공개하고 외부 요청은 같은 서버의 리버스 프록시를 통해 전달한다.

API 서버에는 저장소를 clone하지 않는다. GitHub Actions가 Compose 파일과 배포 스크립트를 `/opt/momogo/deploy/dev` 또는 `/opt/momogo/deploy/prod`로 전송한다. 환경별 설정과 시크릿은 다음 경로에 별도로 둔다.

```text
/opt/momogo/config/
├── dev.env
└── prod.env
```

`deploy/config/*.env.example`을 복사해 값을 설정하고 배포 사용자만 읽고 수정할 수 있도록 권한을 `600`으로 제한한다. 배포 스크립트가 이 파일을 셸 설정으로 읽으므로 비밀번호와 토큰은 예시처럼 작은따옴표로 감싼다.

GitHub의 dev와 prod Environment에는 `SERVER_HOST`, `SERVER_PORT`, `SERVER_USER`, `SSH_PRIVATE_KEY`, `SERVER_FINGERPRINT`, `DISCORD_WEBHOOK_URL` secret을 등록한다. `SERVER_FINGERPRINT`는 접속 대상 서버의 공개 키 지문이다.

새 컨테이너의 배포 또는 헬스체크가 실패하면 직전 이미지로 롤백을 시도하고 Actions 작업은 실패로 남긴다. 직전 이미지가 없거나 새 이미지와 같으면 롤백하지 못한 채 배포 실패로 종료한다.
