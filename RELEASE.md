# 출시 준비

## 0. 먼저 정할 것

첫 업로드 뒤에는 패키지 이름을 바꿀 수 없다.

1. **이름·패키지·아이콘 (상표)**: 2026-11-08 정함.
   - 이름 "톡바" / "TalkBar", 패키지 `com.siryul.talkbar`, 보라 말풍선 아이콘. 옛 이름 "카톡 나우바"와 노란 아이콘은 카카오 상표("카톡·KakaoTalk")와 삼성 기능 이름("Now Bar")에 걸려서 바꿨다.
   - 웹 검색으로는 같은 이름의 플레이 앱이 안 나왔다 (미국 기준 검색). 첫 업로드 전에 플레이 스토어에서 "톡바"·"TalkBar"를 직접 검색하고, 상표는 키프리스(kipris.or.kr)에서 확인한다.
   - 스토어 제목 예: "톡바 - 잠금화면·AOD 메시지 알림" / "TalkBar - Lock screen messages" (30자 이내).
   - 설명문에 "카카오톡 알림을 Now Bar에 띄워요"처럼 기능을 설명하는 건 보통 괜찮다. 대신 "카카오·삼성과 관계없는 앱" 문구를 넣는다.
2. **어디에 낼지**
   - 구글 플레이: 등록비 $25. 새로 만든 개인 개발자 계정은 비공개 테스트(최소 12명, 14일 연속)를 거쳐야 정식 출시를 신청할 수 있다 (2024-12 기준 규칙, 바뀔 수 있음).
   - 갤럭시 스토어: 어차피 삼성 폰에서만 의미 있는 앱이라 잘 맞는다. 셀러 포털 심사가 있다.
   - APK 직접 배포 (GitHub Releases 등): 심사가 없다. 대신 설치할 때 '출처를 알 수 없는 앱'을 허용해야 한다. 알림 접근도 '제한된 설정'으로 막혀서, 앱 정보 → ⋮ → 제한된 설정 허용을 거쳐야 한다 (앱 첫 화면에 안내가 있다).
     - 2026-11-09 GitHub에 올렸다: 코드 https://github.com/skdlzlvk/TalkBar, 첫 릴리스 v1.0.0 (`TalkBar-1.0.0.apk`). 다음 버전 올리는 법은 아래 "4. GitHub 릴리스".
     - 나중에 스토어에도 내면 서명을 맞춰야 GitHub에서 깐 사용자가 업데이트를 받는다. 갤럭시 스토어는 개발자가 서명한 APK를 올리니 같은 키를 쓰면 된다. 플레이는 '플레이 앱 서명'에서 구글이 새 키를 만들게 하면 서명이 달라진다. 앱 서명 키로 이 키를 올리는 쪽('Java 키 저장소에서 키 내보내기 및 업로드')을 고른다.
     - 구글 개발자 인증: 인증된 개발자 앱이 아니면 설치가 까다로워진다 (ADB나 별도 절차 필요). 브라질·인도네시아·싱가포르·태국은 2026-09-30부터, 한국 포함 전 세계는 2027년 예정이다. 그 뒤엔 직접 배포도 구글에 개발자 등록을 해야 한다.
3. **심사·동작 위험**
   - 구글 Live Update 가이드는 채팅 메시지를 부적절한 용도로 적어둔다. 다른 앱 알림을 옮겨 띄우는 것도 마찬가지다. 심사에서 문제 되거나, 나중 안드로이드 업데이트에서 승격이 막힐 수 있다.
     - 2026-11-09 다시 확인: 문서는 채팅 메시지 말고도 "다른 사람이 일으킨 활동"과 택배 추적을 부적절한 예로 든다. 문서에 적힌 결과는 사용자가 앱의 Live Update 권한을 끌 수 있다는 것뿐이고, 플레이 정책으로 막는다는 글은 찾지 못했다.
     - 플레이 심사관이 삼성 폰·개발자 옵션 없이 보면 핵심 기능이 안 보인다. Play Console '앱 액세스'에 켜는 법을 적고, 기기 목록을 삼성·안드로이드 16 이상으로 줄인다.
   - 설치된 앱 목록: "앱 고르기"가 매니페스트 `<queries>`(MAIN/LAUNCHER)로 런처 앱 목록을 읽는다. `QUERY_ALL_PACKAGES` 신고 대상은 아니다. 다만 플레이 정책은 설치된 앱 목록을 개인·민감 정보로 보므로 개인정보처리방침에 적었다. 기기 밖으로 안 나가니 데이터 보안 양식은 그대로 "수집 없음".
   - Now Bar는 삼성 개발자 옵션 '모든 앱의 실시간 정보 보기'에 기댄다. 설명문에 그대로 적는다.
   - 화면 켜기는 One UI 9가 `TURN_SCREEN_ON` 없이도 허용하고 있다. 나중에 막히면 사용자가 '화면 켜기 허용'을 켜야 한다 (앱 첫 화면 안내 있음).

## 1. 서명 키 만들기 (한 번만, 직접)

키 파일과 비밀번호를 잃어버리면 GitHub·갤럭시 스토어로 깐 사용자에게 업데이트를 낼 수 없다. 둘 다 따로 백업한다. 키 파일과 `keystore.properties`는 `.gitignore`에 들어 있다.

2026-11-09에 만들었다: `~/keys/talkbar-upload.jks`, 별칭 `upload`, RSA 4096, 인증서 `CN=TalkBar, C=KR`, 인증서 SHA-256 `0ca65b9bdcfd767af85fd4a2f237b280b28ed96a5fb20b17f9f5ca449f949094`.

```bash
mkdir -p ~/keys
```

```bash
$(/usr/libexec/java_home -v 21)/bin/keytool -genkeypair -v -keystore ~/keys/talkbar-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

프로젝트 맨 위에 `keystore.properties`를 만든다 (비밀번호는 위에서 정한 것):

```properties
storeFile=/Users/<사용자>/keys/talkbar-upload.jks
storePassword=비밀번호
keyAlias=upload
keyPassword=비밀번호
```

## 2. 빌드

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bundleRelease
```

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleRelease
```

- 플레이: `app/build/outputs/bundle/release/app-release.aab`
- 갤럭시 스토어·직접 배포: `app/build/outputs/apk/release/app-release.apk`
- 업데이트를 낼 때마다 `app/build.gradle.kts`의 `versionCode`를 1 올리고 `versionName`을 바꾼다.
- 출시 빌드에는 adb 테스트 수신기와 "셸 테스트 알림" 옵션이 없다. R8로 줄여서 APK가 117KB쯤이다 (1.0.0). 2026-11-09 출시 키로 서명한 1.0.0을 에뮬레이터(안드로이드 16)에 깔아 첫 화면·앱 고르기·테스트 알림이 정상인 것을 확인했다. 2026-11-08 폴드8에서 출시 빌드를 디버그 키로 서명해 설치해봤다: 리스너 연결·첫 화면 정상. 2026-11-09에 넣은 "앱 고르기"는 폴드8에서 개발 빌드로 확인했다: 다른 앱 알림의 승격, 화면 켜기, 잠금화면·AOD 카드가 정상이다. Now Bar 카드 아이콘은 어느 앱 알림이든 톡바 아이콘이다 (삼성이 정함). 그래서 앱을 2개 이상 고르면 카드 첫 줄에 앱 이름을 붙인다. 스크린샷·설명문이 이와 어긋나지 않게 한다.

## 3. 스토어에 적을 것

- **개인정보처리방침 URL**: https://github.com/skdlzlvk/TalkBar/blob/main/PRIVACY.md (알림 내용을 읽는 앱이라 꼭 있어야 한다). 문의처는 GitHub Issues로 적어뒀다. 스토어가 이메일을 따로 요구하면 그때 넣는다.
- **데이터 보안 (Data safety)**: "수집하는 데이터 없음, 공유하는 데이터 없음". 근거: 인터넷 권한이 없어서 기기 밖으로 보낼 수 없다. 알림 내용은 기기 메모리에서만 쓴다. 기기 안 기록에는 앱 이름·알림 제목(보낸 사람·방 이름 등)과 글자 수만 남는다. 설치된 앱 목록은 앱 고르기 화면에 보여주기만 한다.
- **권한 용도**
  - 알림 접근: 사용자가 고른 앱(기본 카톡)의 알림 읽기.
  - 알림 표시·실시간 알림: Now Bar에 띄우기.
  - 화면 켜기·웨이크락: 새 알림에 화면 켜기.
- **그 밖**: 콘텐츠 등급 설문, 타깃 연령, 광고 없음.
- **스크린샷**: 휴대전화 2장 이상. AOD는 캡처가 안 되므로 잠금화면 상태에서 찍거나 다른 폰으로 촬영한다.

### 설명문 초안

짧은 설명 (80자 이내)
- ko: 카톡 등 고른 앱의 새 알림을 갤럭시 Now Bar(잠금화면·AOD)에 띄우고 화면을 켜줘요.
- en: Shows new KakaoTalk and other app notifications in the Galaxy Now Bar.

자세한 설명 (ko)

> 카톡 등 고른 앱의 새 알림을 갤럭시 Now Bar에 띄워요. 잠금화면과 AOD에서 바로 보이고, 새 알림이 오면 화면이 켜져요.
>
> - 띄울 앱을 직접 골라요. 처음엔 카카오톡만 켜져 있고, 문자·메신저·메일 같은 다른 앱도 켤 수 있어요. 여러 앱을 켜면 카드에 앱 이름도 나와요.
> - 여러 알림은 카드 하나로 모아요. 카드를 누르면 알림마다 한 줄씩 펼쳐져요.
> - 원래 앱에서 읽으면 Now Bar에서도 사라져요.
> - 음악·통화·내비처럼 계속 떠 있는 알림과 광고 알림은 빼요.
> - 주머니 속이거나 뒤집어 둔 상태, 조용한 알림, 방해 금지 중엔 화면을 켜지 않아요.
> - 알림 내용을 숨기고 보낸 사람이나 제목만 보이게 할 수 있어요.
> - 인터넷 권한이 없어요. 알림 내용은 폰 밖으로 나가지 않아요.
>
> 필요한 것: 안드로이드 16 이상 갤럭시 (잠금화면·AOD 표시는 One UI 9). 삼성 제휴 앱이 아니라서 설정 → 개발자 옵션 → '모든 앱의 실시간 정보 보기'를 켜야 해요.
>
> 카카오·삼성과 관계없는 개인 개발 앱이에요. 카카오톡은 카카오의 상표예요.

Full description (en)

> Shows new notifications from KakaoTalk and other apps you choose in the Galaxy Now Bar, right on the lock screen and Always On Display, and turns the screen on when one arrives.
>
> - You choose the apps. Only KakaoTalk is on at first; turn on messages, messengers, email or any other app. With more than one app on, the card shows the app name.
> - Several notifications are grouped into one card. Tap the card to see one line each.
> - Notifications you read in the original app disappear from the Now Bar.
> - Music, calls, navigation, ads and other ongoing notifications are left out.
> - The screen stays off when the phone is in a pocket or face down, for silent notifications, and during Do Not Disturb.
> - Option to hide notification text and show only the sender or title.
> - No internet permission. Notification content never leaves your phone.
>
> Requires a Galaxy phone with Android 16 or later (lock screen and AOD display need One UI 9). Because this is not a Samsung partner app, turn on Settings → Developer options → ‘Live notifications for all apps’.
>
> Independent app, not affiliated with Kakao or Samsung. KakaoTalk is a trademark of Kakao Corp.

## 4. GitHub 릴리스 (직접 배포)

저장소: https://github.com/skdlzlvk/TalkBar (공개, MIT). 첫 릴리스 v1.0.0은 2026-11-09.

1. `app/build.gradle.kts`의 `versionCode`를 1 올리고 `versionName`을 바꾼다.
2. 위 "2. 빌드"의 `assembleRelease`로 APK를 만든다.
3. 서명을 확인한다. 인증서 SHA-256이 `0ca65b9b…f949094`(위 "1. 서명 키")와 같아야 사용자 폰에서 업데이트로 깔린다. apksigner는 Android SDK `build-tools` 폴더에 있다.
   ```bash
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```
4. 파일 이름을 `TalkBar-<버전>.apk`로 바꿔서 올린다. 릴리스 노트에는 설치 방법(한/영)과 APK·인증서 SHA-256을 적는다.
   ```bash
   gh release create v<버전> TalkBar-<버전>.apk --title "톡바 <버전>" --notes-file <릴리스 노트 파일>
   ```
5. README의 파일 이름(`TalkBar-1.0.0.apk`)을 새 버전으로 바꾼다.
