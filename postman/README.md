# Momogo Postman 기능 flow

`momogo-dev.postman_collection.json`은 dev API의 핵심 기능을 Postman 앱에서 폴더별로 실행할 수 있게 정리한 Collection이다. API 계약은 `https://api.dev.mogumogo.com/v3/api-docs`를 기준으로 한다.

## 가져오기와 인증 준비

1. Postman 앱에 `momogo-dev.postman_collection.json`과 `momogo-dev.postman_environment.json`을 import한다.
2. `Momogo dev` 환경을 선택한다.
3. `인증 > 인증 준비` 요청을 한 번 Send 한다.

`baseUrl`과 `nickname`만 입력되어 있으면 된다. `providerToken`을 비워 두면 요청 직전에 `momogo-postman-<GUID>` 형식의 값을 만들어 환경에 저장하고, 로그인 결과가 `404 USER_NOT_FOUND`일 때만 회원가입한다. 기존 GUEST 사용자를 재사용하려면 그 사용자의 `providerToken`을 직접 입력한다.

성공하면 `accessToken`, `refreshToken`, `userId`가 선택한 환경에 저장되고 Collection 수준의 Bearer 인증에 사용된다. 기능 폴더를 Collection Runner로 따로 실행할 때는 **Keep variable values**를 켜야 다음 폴더에서도 저장된 토큰과 ID를 사용할 수 있다.

## 기능별 실행

필요한 기능 폴더만 선택해 실행할 수 있다. 앞 폴더가 저장하는 ID를 사용하거나, 환경에 이미 알고 있는 ID를 직접 넣는다.

| 폴더 | 필수 입력 | 저장되는 값과 실행 내용 |
| --- | --- | --- |
| `인증` | `baseUrl`, `nickname`; 선택 `providerToken` | `인증 준비`가 `accessToken`, `refreshToken`, `userId`, 필요 시 새 `providerToken`을 저장한다. `토큰 재발급`은 저장된 refresh token을 새 토큰으로 교체한다. |
| `사용자` | `accessToken` | 내 정보를 조회하고 `nickname`으로 닉네임을 변경한다. |
| `그룹` | `accessToken` | 그룹을 만든 뒤 `groupId`, `inviteCode`를 저장하고 목록·상세·이름 변경·초대 정보를 확인한다. |
| `사진` | `accessToken`, `groupId` | R2 업로드 URL 발급 → 테스트 SVG 업로드 → 사진 등록 → 조회 순서로 실행하며 `photoId`를 저장한다. |
| `리액션` | `accessToken`, `groupId`, `photoId` | 리액션을 등록하고 `reactionId`를 저장한 뒤 조회·삭제한다. |
| `신고` | `accessToken`, `groupId`, 활성 `photoId`, 환경 변수 `reason` | 사진을 신고하고 `200`, `application/json`, 정확한 `{}` 응답을 확인한다. |
| `정리` | `accessToken`, `refreshToken`; 그룹 탈퇴 시 `groupId` | 그룹 탈퇴 → 로그아웃 → 회원 탈퇴 순서로 정리하고 저장된 토큰과 ID를 비운다. |

전체 기능을 확인할 때의 권장 순서는 `인증 준비 → 사용자 → 그룹 → 사진 → 리액션 → 신고 → 정리`다. 각 폴더의 요청은 위에서 아래 순서로 실행한다.

## 외부 부작용과 정리 주의사항

- `사진` 폴더는 dev R2에 실제 오브젝트와 사진 데이터를 만든다. 공개된 사진 삭제 API가 없어 R2 오브젝트가 남을 수 있으며, 같은 사용자·그룹에는 하루 한 장 제한이 적용된다.
- `신고` 폴더의 단일 요청은 dev Discord webhook으로 실제 메시지 1건을 보낸다. `groupId`와 `photoId`가 같은 활성 사진을 가리키는지 확인한 뒤 한 번만 Send 한다.
- `정리 > 회원 탈퇴`는 현재 `providerToken`에 연결된 계정과 관련 데이터를 실제로 삭제한다. 기존 GUEST 계정을 재사용했다면 이 요청을 실행하지 않는다.

환경 파일에는 비밀값을 넣지 않았다. `providerToken`, `accessToken`, `refreshToken`은 import 시 비어 있으며 실행 중 선택한 Postman 환경에만 저장된다.
