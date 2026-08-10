# Momogo dev API 통합 테스트

`momogo-dev.postman_collection.json`은 dev API의 실제 HTTP 계약을 순서대로 확인하는 Postman Collection이다. 기준 명세는 `https://api.dev.mogumogo.com/v3/api-docs`다.

## 확인하는 흐름

1. 공개 초기화 API와 인증 누락 `401` 응답을 확인한다.
2. 실행마다 고유한 게스트 사용자 2명을 가입시킨다.
3. 사용자 조회·닉네임 변경과 refresh token 회전을 확인한다.
4. 그룹 생성 → 초대 조회 → 가입 → 중복 가입 오류 → 목록·상세·이름 변경 → 탈퇴를 확인한다.
5. 명시적으로 허용한 경우에만 사진 업로드 → 등록 → Discord 신고를 한 번 확인한다.
6. refresh token을 로그아웃 처리하고 두 임시 계정을 삭제한다.

Collection Runner가 테스트 실패 뒤에도 다음 요청을 계속 실행하는 기본 동작을 사용한다. 마지막 정리 요청까지 실행해야 dev DB에 임시 데이터가 남을 가능성이 작아지므로 `--bail` 옵션을 붙이지 않는다.

사진 신고의 무인증 `401 INVALID_AUTH_CREDENTIALS` 계약은 기본 실행에서도 확인한다. 성공 신고 흐름은 Collection에 포함되어 있지만 기본값으로 건너뛴다. 성공 흐름은 R2에 테스트 이미지를 올리고 dev Discord 채널에 실제 메시지 1건을 보낸다. 공개된 삭제 API가 없으므로 이때 만든 R2 오브젝트와 비활성 사진 관련 데이터는 실행 후에도 남을 수 있다.

## Postman 앱에서 실행

1. `momogo-dev.postman_collection.json`과 `momogo-dev.postman_environment.json`을 import한다.
2. `Momogo dev` 환경을 선택한다.
3. 개별 폴더가 아니라 전체 Collection을 처음부터 순서대로 실행한다.

성공 신고까지 확인하려면 실행 전에 환경의 `enablePhotoReport`를 `true`로 바꾸고, 한 번 실행한 뒤 다시 `false`로 되돌린다.

액세스 토큰, refresh token, 사용자 ID와 그룹 ID는 실행 중 Collection 변수에만 저장되며 환경 파일에는 들어 있지 않다.

## Postman CLI에서 실행

[Postman CLI](https://learning.postman.com/docs/postman-cli/postman-cli-installation/)를 설치한 뒤 저장소 루트에서 실행한다.

```sh
./scripts/run-postman-integration.sh
```

다른 서버에 실행하려면 base URL을 런타임에만 덮어쓴다.

```sh
MOMOGO_API_BASE_URL=http://localhost:8080 ./scripts/run-postman-integration.sh
```

사진 업로드와 성공 신고까지 실행하려면 외부 부작용을 명시적으로 허용한다. 한 Collection 실행에서 성공 신고 요청은 정확히 한 번만 전송된다.

```sh
MOMOGO_ENABLE_PHOTO_REPORT=true ./scripts/run-postman-integration.sh
```

CLI 결과가 실패하면 프로세스도 0이 아닌 코드로 종료된다. JUnit 결과는 `build/postman/results.xml`에 생성된다. Postman Cloud 자원을 사용하지 않으므로 `postman login`이나 `POSTMAN_API_KEY`는 필요하지 않다.

## GitHub Actions

`API integration / dev` workflow는 다음 때 같은 Collection을 실행한다.

- `CD / dev` 배포가 성공한 직후
- Actions 화면에서 수동으로 실행했을 때

자동 배포 후 실행에서는 사진 성공 신고가 항상 비활성화된다. 수동 실행에서만 `R2 업로드와 Discord 사진 신고 1건 실행`을 선택해 켤 수 있다.

Collection이 임시 데이터를 직접 정리하므로 별도 API 토큰 secret은 사용하지 않는다. 실패한 실행의 중간 데이터가 남았다면 `postman-owner-` 또는 `postman-member-` 접두사의 GUEST provider token으로 생성된 계정을 확인한다.
