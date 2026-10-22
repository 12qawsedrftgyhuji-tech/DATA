# Google Calendar 연동 가이드 및 Google Cloud Console 설정 문서

본 문서는 Android 앱(`com.example.wpqkf`)에서 구글 공식 Calendar API 및 OAuth 2.0 권한 승인을 사용하여 Google Calendar 연동을 정상 작동시키기 위한 구글 클라우드 콘솔(Google Cloud Console) 설정 방법입니다.

---

## 1. Google Cloud Console 프로젝트 설정

### 1-1. 프로젝트 생성 및 선택
1. [Google Cloud Console](https://console.cloud.google.com/)에 접속합니다.
2. 상단 프로젝트 선택 드롭다운에서 **[새 프로젝트]**를 클릭하거나 기존 프로젝트를 선택합니다.

### 1-2. Google Calendar API 활성화
1. 좌측 메뉴에서 **[API 및 서비스] > [라이브러리]**로 이동합니다.
2. 검색창에 **"Google Calendar API"**를 검색합니다.
3. 검색 결과에서 **Google Calendar API**를 선택하고 **[사용]** 버튼을 클릭합니다.

---

## 2. OAuth 동의 화면 (OAuth Consent Screen) 설정

1. 좌측 메뉴에서 **[API 및 서비스] > [OAuth 동의 화면]**으로 이동합니다.
2. **User Type**을 **외부 (External)**로 선택하고 **[만들기]**를 클릭합니다.
3. **앱 정보**:
   - 앱 이름: `DATA 일정 관리` (원하는 이름)
   - 사용자 지원 이메일: 본인 이메일 주소
   - 개발자 연락처 정보: 본인 이메일 주소
4. **범위 (Scopes)** 설정:
   - **[범위 추가 또는 삭제]** 버튼을 클릭합니다.
   - 검색창에 `https://www.googleapis.com/auth/calendar.events`를 검색하여 체크 추가합니다.
   - (Google Calendar 이벤트 추가, 수정, 조회 권한)
5. **테스트 사용자 (Test Users)**:
   - 개발 중일 경우 앱이 게시 전 상태이므로, 연동에 사용할 Google 계정 이메일을 테스트 사용자로 등록해야 합니다.
   - **[+ ADD USERS]**를 클릭하고 테스트용 Google 계정을 입력합니다.

---

## 3. OAuth 2.0 클라이언트 ID 생성 (Android)

1. 좌측 메뉴에서 **[API 및 서비스] > [사용자 인증 정보]**로 이동합니다.
2. 상단의 **[+ 사용자 인증 정보 만들기] > [OAuth 클라이언트 ID]**를 클릭합니다.
3. **애플리케이션 유형**: **Android** 선택
4. **기본 정보 입력**:
   - **이름**: Android Client
   - **패키지 이름**: `com.example.wpqkf`
   - **SHA-1 서명 인증서 지문**: 본인 개발 환경의 Debug SHA-1 지문 입력

### SHA-1 지문 확인 방법 (터미널 명령)
macOS / Linux:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```
Windows:
```cmd
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```
출력 내용 중 `SHA1:` 항목의 값(예: `AA:BB:CC:DD:...`)을 복사하여 콘솔의 SHA-1 서명 인증서 지문 란에 입력합니다.

5. **[만들기]**를 클릭하여 클라이언트 ID 조성을 완료합니다.

---

## 4. 앱 내 연동 동작 확인

1. 앱을 실행하고 **일정** 탭으로 이동합니다.
2. 상단 **Google Calendar: 미연동** 카드 우측의 **[Google 계정 연결]** 버튼을 클릭합니다.
3. 위에서 테스트 사용자로 등록한 Google 계정으로 로그인 및 Calendar 권한을 승인합니다.
4. 일정 추가(+) 버튼을 누르고 **'Google Calendar에도 추가'** 스위치를 켠 상태로 일정을 등록합니다.
5. 웹 Google Calendar([calendar.google.com](https://calendar.google.com/))에서 등록한 일정 이벤트가 실시간으로 생성되었는지 확인합니다.
