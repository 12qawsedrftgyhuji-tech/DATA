# Android 일정 관리 앱 (Google Calendar 연동)

이 프로젝트는 Android/Kotlin 기반 일정 관리 앱입니다. 구글 공식 Calendar API 및 OAuth 2.0 권한 승인을 통해 사용자의 Google Calendar에 일정을 실시간으로 생성하고 관리할 수 있습니다.

## 주요 기능 (Schedule / Calendar Tab)
- **Google 계정 연동 상태 확인**: 일정 화면 상단 카드에서 현재 구글 계정 연동 여부 및 연결된 이메일을 확인합니다.
- **계정 관리**: 계정 연결, 계정 변경, 연동 해제를 손쉽게 수행할 수 있습니다.
- **Google Calendar 동기화**: 일정 추가 바텀시트에서 **'Google Calendar에도 추가'** 옵션을 선택하여 일정을 등록하면 사용자의 Google Calendar에 이벤트가 등록됩니다.
- **시간 및 타임존 지정**: 시작 시간 및 종료 시간(TimePicker UI)을 정확히 설정하며, 기기의 현재 타임존이 반영됩니다.
- **안전한 오류 및 중복 방지**:
  - 중복 터치로 인한 동일 일정 이중 등록 방지
  - 로그인 취소/권한 거부 시 앱 종료 방지 (안정적인 예외 처리)
  - Google Calendar 등록 실패 시에도 앱 내부 일정은 정상 보존되며, 사용자에게 재시도 안내 다이얼로그 제공

## Google Cloud Console 설정 방법
자세한 Google Cloud Console, OAuth 클라이언트 ID 및 SHA-1 등록 가이드는 [GOOGLE_CALENDAR_SETUP.md](GOOGLE_CALENDAR_SETUP.md) 문서를 참고해주세요.
