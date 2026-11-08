# 톡바 개발 노트

옛 이름은 "카톡 나우바". 2026-11-08 상표 문제로 이름·패키지(`com.siryul.talkbar`)·아이콘을 바꿨다.

카톡 등 고른 앱의 새 알림을 갤럭시 **Now Bar**(잠금화면·AOD)에 띄우고, 새 알림이 오면 화면을 켜주는 앱. 기본은 카톡만 켜져 있고, "앱 고르기"에서 다른 앱도 켜고 끈다 (2026-11-09 추가). 폴드8(One UI 9 / Android 17)에서 만들고 확인했다. 화면 문구는 영어(기본)와 한국어.

삼성 AOD 위젯 자리는 일반 앱에 열려 있지 않다. 그래서 안드로이드 16의 **Live Update**(승격 알림)를 쓴다. One UI 8부터 Live Update를 Now Bar에 표시하고, One UI 9는 잠금화면·AOD에도 보여준다.

사용자용 설명은 [README.md](README.md), 출시 절차는 [RELEASE.md](RELEASE.md), 개인정보처리방침은 [PRIVACY.md](PRIVACY.md).

## 동작

1. **수집**: `KakaoListenerService`가 알림 접근 권한으로 고른 앱의 알림을 받는다. 고른 앱은 `Prefs.sources` (기본 `com.kakao.talk`). 클래스 이름이 Kakao…인 건 카톡 전용이던 때 그대로다. 바꾸면 사용자가 준 '알림 접근'이 풀린다.
2. **해석**: `NotificationParser`가 알림 하나를 한 줄로 만든다.
   - 메시지형(`MessagingStyle`, 카톡·문자·메신저): 마지막 메시지의 보낸 사람·내용·시각. 단체방이면 방 이름도.
   - 그 밖의 알림: 제목(큰 제목 → 제목)과 본문(긴 글 → 본문 → 여러 줄 알림의 마지막 줄). 시각은 알림이 올라온 시각.
   - 건너뛰는 알림: 묶음 요약, 계속 떠 있는 알림(ongoing·포그라운드 서비스), 음악(미디어 세션)·통화·내비·진행률·서비스·시스템·스톱워치·위치 공유·운동·광고·추천 종류(`category`), "조용한 알림 제외"가 켜져 있으면 중요도 DEFAULT 미만.
   - 원래 알림의 작은 아이콘을 24dp 비트맵으로 옮겨 Now Bar 아이콘으로 쓴다 (다른 앱의 리소스 번호를 넘기지 않게). 못 옮기면 톡바 아이콘.
3. **표시**: `NowBarNotifier`가 내 앱 이름으로 Live Update 알림 하나를 올린다. 여러 알림은 한 알림에 줄로 모은다. 앱이 둘 이상 섞이면 줄 앞에 앱 이름을 붙인다. 카드 아래 작은 글자는 알림이 하나면 앱 이름, 여럿이면 "알림 N개". 카드를 누르면 맨 위 알림의 앱을 연다. 내용이 그대로면 다시 올리지 않는다 (Now Bar 카드가 괜히 다시 그려지지 않게).
4. **화면 켜기**: 새 알림이면 `ScreenWaker`가 화면을 켠다 (아이폰처럼). 원래 앱이 '한 번만 알림'으로 소리 없이 고쳐 올린 알림은 새 알림으로 치지 않는다. 아래 "화면 켜기" 참고.
5. **정리**: 원래 앱에서 읽어서 그 알림이 사라지면 Now Bar에서도 뺀다. 앱 고르기에서 끈 앱의 알림도 바로 뺀다. 사용자가 Now Bar 알림을 지우면 새 알림이 올 때까지 다시 띄우지 않는다.

### 앱 고르기 (2026-11-09)

- 메인 화면 "2. 앱 고르기"(영어 "2. Apps")의 "앱 고르기" 버튼 (`AppPickerActivity`). 런처에 아이콘이 있는 앱만 나온다. 매니페스트 `<queries>`의 MAIN/LAUNCHER로 보는 범위다. 모든 앱 보기(`QUERY_ALL_PACKAGES`)는 스토어가 제한하는 권한이라 안 쓴다.
- 순서: 켠 앱 → 깔려 있는 메시지 앱(카톡, 삼성·구글 메시지, 라인, 텔레그램, 왓츠앱, 인스타그램, 메신저, 디스코드, 슬랙, 팀즈, 밴드, Gmail, 삼성 이메일) → 나머지 이름순. 검색은 앱 이름·패키지 이름으로.
- 켜고 끌 때마다 떠 있는 알림을 다시 읽는다. 켠 앱의 알림은 바로 들어오고 끈 앱의 알림은 바로 빠진다. 이때 화면은 켜지 않는다.
- 카톡이 안 깔린 폰에선 메인 화면에 "고른 앱이 없어서 아무것도 안 떠요"가 나온다. 안 깔린 앱은 목록에 없어서 끌 수도 없으니 요약에서도 뺀다. 나중에 카톡을 깔면 바로 받는다.
- 에뮬레이터(안드로이드 16 기본 이미지 `BE2A.250530`)에서 확인한 것: 켜고 끄기, 메시지형·일반 알림 해석, 음악 알림 건너뜀, 여러 앱이 섞일 때 앱 이름 붙음, 비트맵 아이콘, 검색, 한국어 화면. 이 이미지엔 Live Update가 없어서 테스트 알림도 "Live Update 형식 아님 · 승격 꺼짐"으로 나온다.
- 폴드8(One UI 9)에서 확인한 것 (2026-11-09, 셸 일반 알림으로): Live Update로 승격되고, AOD 중이면 화면이 켜지고, 잠금화면 Now Bar 카드(`nowbar_main_text_on_normal_card`)와 AOD(`visibleList size = 1`)에 뜬다. 앱 고르기 화면도 정상 (켠 앱이 맨 위, 그다음 메시지 앱, 나머지 이름순). 카드 아이콘은 톡바 아이콘이다 (아래 "한계").
- 앱을 2개 이상 고르면 (깔린 앱만 센다) 접힌 카드 첫 줄 앞에 앱 이름을 붙인다 ("카카오톡 · 엄마"). 카드 아이콘으로는 어느 앱인지 알 수 없어서다. 테스트 항목엔 안 붙인다. 폴드8 확인 (2026-11-09): 카톡만 켜면 "택배 도착", 계산기까지 켜면 "셸 · 택배 출발 외 1개".

### 폴드8에서 실측한 카톡 알림 구조 (카톡 26.9.2)

- 대화방마다 알림 1개(tag = 방 ID)와 묶음 요약 1개가 온다. 요약은 건너뛴다.
- `MessagingStyle` 형식이고, `android.messages`에 메시지별 보낸 사람·내용·시각이 들어 있다.
- 일반 방은 `new_message_v1` 채널(중요도 4), 알림 끈 방은 `quiet_new_message` 채널(중요도 2, 이름 "알림 받지 않는 메시지")로 온다.

## Glimpse Notifications에서 가져온 것

| Glimpse | 이 앱 |
|---|---|
| 최소 중요도 필터 | "조용한 알림 제외" (중요도 DEFAULT 미만 건너뜀) |
| 앱 허용 목록 | "앱 고르기" (기본 카톡. 개발 빌드에선 셸 알림도 받을 수 있음) |
| 배터리 최적화 해제 안내 | 첫 화면 체크 항목 (앱 정보 → 배터리로 보냄) |
| 리스너 끊김 대응 | 끊기면 `requestRebind` |
| 근접 센서 (주머니 속이면 안 켬) | 화면 켜기 전에 근접 센서 확인 |
| 로그 버퍼 | 앱 안 "기록"과 "마지막 알림 구조"(내용은 글자 수만) |

## 화면 켜기 (One UI 9 실측, 2026-11-08)

- 켜지 않는 경우: 이미 화면이 켜져 있음 (화면을 끈 뒤 2초쯤까지도 아직 켜진 걸로 나온다. 2026-11-09 실측), 소리 없이 온 알림(알림 끈 방, 방해 금지), 20초 안에 이미 켰음, 근접 센서가 가려짐(주머니 속·뒤집힘), 사용자가 '화면 켜기 허용'을 껐음. 앱이 다시 켜진 뒤 올라온 2분 넘은 옛 알림도 안 켠다.
- 방식: `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE`를 3초 잡는다. 놓은 뒤 잠금화면 자체 시간 6초(`activityTimeoutWM=6000`)가 더해져서 화면은 8~9초 켜져 있다.
- 삼성은 엣지 라이팅이 켜져 있으면 앱의 웨이크락을 바로 쓰지 않는다(`isWakeUpPreventionNeededForEdgeLighting`). 대신 0.5초쯤 뒤 시스템이 `EDGELIGHTING:` + 앱 웨이크락 이름(`EDGELIGHTING:TalkBar:newMessage`)으로 화면을 켠다. 결과는 같다.
- 권한: 안드로이드 14+ `TURN_SCREEN_ON`은 `signature|privileged|appop`이라 설치만으로는 안 생긴다. 그래도 One UI 9는 아직 막지 않는다 (logcat `Allowing device wake-up without android.permission.TURN_SCREEN_ON`). 나중에 막히면 사용자가 '특별한 접근 → 화면 켜기'에서 허용하면 된다. 앱 첫 화면 "화면 켜기 허용" 줄이 그 화면을 연다 (`android.settings.TURN_SCREEN_ON_SETTINGS` + `package:`).
- 4초 넘게 잡고 있으면 삼성 PowerManager가 `abuse wakelock package`로 표시했다 (5초로 시험했을 때). 3초로 줄인 뒤엔 안 나온다.

## 다국어

- 문구는 전부 `res/values/strings.xml`(영어, 기본)과 `res/values-ko/strings.xml`(한국어)에 있다. 코드에 화면 문구를 직접 쓰지 않는다.
- 폰 언어가 한국어면 한국어, 그 밖의 언어는 영어로 나온다. 안드로이드 13+ "앱 언어" 설정에서 앱만 따로 바꿀 수도 있다 (`res/xml/locales_config.xml`).
- 언어 추가: `values/strings.xml`을 `values-xx/strings.xml`로 복사해 번역하고, `locales_config.xml`에 `xx`를 넣는다.
- Now Bar 알림 채널 이름도 언어를 따라 바뀐다 (알림을 올릴 때마다 채널 이름·설명을 다시 적는다).
- 시험: `adb shell cmd locale set-app-locales com.siryul.talkbar --locales en-US` (되돌리기 `--locales ''`).

## 빌드·설치

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleDebug   # macOS. 다른 OS는 JDK 21을 JAVA_HOME에
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 에뮬레이터도 켜져 있으면 `adb -s <폰 시리얼> …`로 폰을 고른다 (시리얼은 `adb devices`로 확인).
- AGP 8.13.2 · Kotlin 2.3.0 · Gradle 8.14 · compileSdk/targetSdk 36 · minSdk 36. 외부 라이브러리 없음. 인터넷 권한 없음.
- 개발(debug) 빌드에만 있는 것: adb 테스트 수신기(`src/debug`), "셸 테스트 알림도 받기" 옵션.
- 출시(release) 빌드: R8로 줄여서 APK가 100KB쯤. 서명·업로드는 [RELEASE.md](RELEASE.md).

## 폰에서 처음 설정

앱을 열고 "1. 처음 설정"의 ❌ 항목을 모두 켠다.

1. 알림 접근 허용 (막혀 있으면 앱 정보 → ⋮ → 제한된 설정 허용)
2. 알림 표시 권한
3. 실시간 알림(Live Update) 허용
4. **삼성 Now Bar 허용**: 설정 → 개발자 옵션 → "모든 앱의 실시간 정보 보기" 켜기. 개발자 옵션 전체를 끄면 같이 꺼진다 (USB 디버깅만 끄는 건 괜찮다). 삼성 폰에서만 보이는 줄.
5. 이 앱 알림 설정에 "실시간 정보" 스위치가 보이면 켜기 (삼성 폰에서만 보이는 줄)
6. 화면 켜기 허용 ("새 알림이 오면 화면 켜기"를 켰을 때만 보이는 줄. One UI 9는 ❔ 상태로도 켜진다)
7. 배터리 사용 제한 없음

그다음 "2. 앱 고르기"에서 띄울 앱을 켠다 (기본은 카톡만).

### 삼성 Now Bar 조건 (One UI 9 실측, 2026-11-08)

- 안드로이드 쪽 승격은 바로 된다: 알림에 `PROMOTED_ONGOING` 플래그가 붙고, SystemUI도 `promotedContent`를 만든다.
- 그런데 삼성 Now Bar(`isNowBarVisible`)는 비어 있었다. 삼성은 Now Bar에 넣을 앱을 허용 목록(`getAllowedOngoingActivityAppList`)으로 관리한다. 쿠팡이츠 같은 제휴 앱은 삼성 전용 확장 키 `android.ongoingActivityNoti.*`를 쓴다.
- 허용 목록 밖의 앱은 개발자 옵션 "모든 앱의 실시간 정보 보기"(`enable_notification_nowbar_test`)를 켜야 Now Bar에 들어간다.
- 켜고 나서 확인한 것: SystemUI "Live Notifications" 목록에 들어가고(`promoted : true`), Now Bar 항목으로 추가된다(logcat `NowBarItemManager ... onNowBarItemUpdated added ... NOTI`). 잠금화면 아래쪽에 카드가 뜬다.
- 카드 글자: 첫 줄 = `contentTitle`(보낸 사람 / 방 · 보낸 사람, 일반 알림은 제목), 둘째 줄 = `setShortCriticalText`. 카드 색은 `setColor`를 따른다. 카드 아이콘은 늘 톡바 앱 아이콘이다 (small icon도 large icon도 안 먹음, 2026-11-09 실측). small icon(원래 앱 아이콘을 옮긴 비트맵)은 상태 표시줄 칩과 알림창에 쓰인다.
  - Live Update 알림이면 SystemUI(`OngoingActivityController` `onEntryUpdated`, `mIsRon`)가 Now Bar 둘째 줄을 `shortCriticalText`로 덮어쓴다. 삼성 전용 extras `android.ongoingActivityNoti.nowbarSecondaryInfo`를 넣어도 안 먹는다.
  - 그래서 칩 글자 자리에 마지막 메시지(최대 40자, 줄바꿈은 공백)를 넣는다. 상태 표시줄 칩에도 같은 글자가 나온다.
  - 커버 화면 카드에선 17자쯤 보이고 나머지는 "…"로 줄인다.
- 여러 알림일 때: 접힌 카드 첫 줄은 "최신 알림 제목 외 N개"(영어 "… and N more")이다. 앱을 2개 이상 골랐으면 제목 앞에 앱 이름이 붙는다. 삼성 extras `android.ongoingActivityNoti.primaryInfo`로 넣는다 (SystemUI가 contentTitle보다 먼저 읽고, 펼친 카드엔 안 쓴다). 잠금화면에서 카드를 누르면 펼쳐지면서 "알림 N개"와 알림마다 한 줄(최대 6개)이 보인다 (BigText).
- 띄울 알림이 없으면 Now Bar도 비어 있다. 원래 앱에서 읽으면 그 앱이 알림을 지우고, 이 앱도 따라서 뺀다.
- 상태 표시줄 칩은 굿락 QuickStar의 "진행 중 칩 숨기기"(`quickstar_indicator_hide_ongoing_chip=1`)가 켜져 있으면 안 보인다.
- AOD에도 같은 카드가 뜬다 (2026-11-08 눈으로 확인). AOD는 스크린샷에 안 찍혀서, 다시 확인할 땐 눈으로 보거나 logcat `getVisibleList ... aodShowState true ... visibleList size`를 본다.

## adb 테스트 (개발 빌드만)

공백이 들어간 값은 `adb shell` 명령 전체를 큰따옴표로 감싸고 값은 작은따옴표로 쓴다. 안 그러면 폰 쪽 셸이 공백에서 잘라서 첫 단어만 들어간다.

```bash
# 테스트 항목 띄우기/지우기/상태 (화면이 꺼져 있으면 화면도 켜진다)
adb shell "am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd test --es sender '홍길동' --es text 'Now Bar 테스트'"
# room을 주면 방마다 따로 쌓인다 (여러 방 표시 확인용). clear는 테스트 항목 전부 지움
adb shell "am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd test --es room '가족방' --es sender '엄마' --es text '저녁 먹었어?'"
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd clear
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd status

# 5초 뒤 테스트 (보내고 바로 화면을 끄면 화면 켜기 확인). 앱 화면의 "5초 뒤 화면 켜기 테스트" 버튼과 같다
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd later
adb shell "dumpsys power | grep -E '^  mWakefulness='"   # Dozing(AOD) → Awake
adb logcat -d -s PowerManagerService | grep -E "talkbar|Waking up" | tail -5

# 앱 켜고 끄기 (앱 고르기 화면과 같다. status의 apps=에 보인다)
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd app --es pkg com.samsung.android.messaging --ez value true

# 카톡 대신 셸 알림으로 수집까지 시험 (셸은 앱 고르기와 상관없이 이 옵션으로 받는다)
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd set --es key accept_shell --ez value true
adb shell "cmd notification post -S messaging --conversation '가족방' --message '엄마:저녁 먹었어?' nowbar_test '새 메시지'"
# 일반 알림(제목·본문)과 음악 알림(건너뛰어야 함)
adb shell "cmd notification post -S bigtext -t '택배 도착' parcel '문 앞에 두었습니다'"
adb shell "cmd notification post -S media -t '노래' music '가수'"
# 셸 알림 지우기 (cmd notification엔 지우는 명령이 없어서 앱이 알림 접근 권한으로 지운다). 끝나면 옵션도 끄기
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd clear_shell
adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd set --es key accept_shell --ez value false

# 승격됐는지 시스템 기록으로 확인 (flags에 PROMOTED_ONGOING)
adb shell dumpsys notification | grep -A3 "pkg=com.siryul.talkbar"

# 삼성 Now Bar에 들어갔는지 (Live Notifications Showing list, Now Bar 항목 수)
adb shell dumpsys activity service com.android.systemui | grep -A6 "Showing list"
adb logcat -d | grep -E "onNowBarItemUpdated|getVisibleList" | tail -5
```

## 한계

- 구글 가이드는 채팅 메시지를 Live Update의 부적절한 용도로 적어둔다. 다른 앱 알림을 옮겨 띄우는 것도 마찬가지다. 스토어 심사나 나중 안드로이드 업데이트에서 막힐 수 있다 ([RELEASE.md](RELEASE.md)).
- 앱 고르기 목록엔 런처 아이콘이 있는 앱만 나온다.
- 잠금화면·AOD의 Now Bar 카드 아이콘은 어느 앱 알림이든 톡바 아이콘이다. 삼성이 알림을 올린 앱의 아이콘으로 그린다. 원래 앱 아이콘을 작은 아이콘·큰 아이콘에 넣어봤지만 카드는 그대로였다 (2026-11-09 폴드8 실측). 원래 앱 아이콘은 상태 표시줄 칩과 알림창에만 나온다. 대신 앱을 2개 이상 고르면 카드 첫 줄에 앱 이름을 붙인다.
- 삼성 제휴 앱이 아니라서 사용자가 개발자 옵션을 직접 켜야 Now Bar에 들어간다.
- 안드로이드 16(API 36) 이상만 설치된다.
- 알림창에 원래 앱 알림과 이 앱 알림이 둘 다 보인다.
- AOD에 글자가 얼마나 보이는지는 One UI가 정한다.
