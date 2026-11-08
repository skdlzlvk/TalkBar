# 톡바 (TalkBar)

카카오톡 등 고른 앱의 새 알림을 갤럭시 **Now Bar**(잠금화면·AOD)에 띄우고, 새 알림이 오면 화면을 켜주는 앱이에요.

[English](#english)

## 이런 앱이에요

- 카톡 새 메시지가 잠금화면과 AOD의 Now Bar 카드에 바로 보여요.
- 새 알림이 오면 화면이 켜져요. 주머니 속이거나 뒤집어 둔 상태, 조용한 알림, 방해 금지 중엔 켜지 않아요.
- 띄울 앱을 직접 골라요. 처음엔 카카오톡만 켜져 있고, 문자·메신저·메일 같은 다른 앱도 켤 수 있어요. 앱을 2개 이상 켜면 카드에 앱 이름도 나와요.
- 여러 알림은 카드 하나로 모여요. 카드를 누르면 알림마다 한 줄씩 펼쳐져요.
- 원래 앱에서 읽으면 Now Bar에서도 사라져요.
- 알림 내용을 숨기고 보낸 사람이나 제목만 보이게 할 수 있어요.
- 인터넷 권한이 없어요. 알림 내용은 폰 밖으로 나가지 않아요.

## 필요한 것

- 안드로이드 16 이상 갤럭시 폰. 잠금화면·AOD 표시는 One UI 9에서 확인했어요.
- 개발자 옵션의 **'모든 앱의 실시간 정보 보기'**. 삼성은 Now Bar를 제휴 앱에만 열어둬서, 이걸 켜야 톡바가 Now Bar에 들어가요.

## 설치

1. [Releases](https://github.com/skdlzlvk/TalkBar/releases/latest)에서 `TalkBar-1.0.0.apk`를 받아요.
2. 받은 파일을 눌러 설치해요. '출처를 알 수 없는 앱'을 허용하라고 나오면 허용해요.

## 처음 설정

톡바를 열고 "1. 처음 설정"의 ❌ 항목을 모두 켜요. 줄 오른쪽 버튼을 누르면 해당 설정 화면이 열려요.

1. **알림 접근 허용**: 스위치가 회색으로 막혀 있으면 앱 정보 → 오른쪽 위 ⋮ → '제한된 설정 허용'을 누른 뒤 다시 켜요.
2. **알림 표시 권한**
3. **실시간 알림(Live Update) 허용**
4. **삼성 Now Bar 허용 (개발자 옵션)**
   - 개발자 옵션이 없으면: 설정 → 휴대전화 정보 → 소프트웨어 정보 → '빌드번호'를 7번 눌러요.
   - 설정 → 개발자 옵션 → '모든 앱의 실시간 정보 보기'를 켜요. 개발자 옵션의 다른 항목은 건드리지 않아도 돼요.
   - 개발자 옵션 맨 위 스위치를 끄면 이것도 같이 꺼져요. USB 디버깅만 끄는 건 괜찮아요.
5. **앱 알림 설정의 '실시간 정보'**: 톡바 알림 설정에 이 스위치가 보이면 켜요. 없으면 건너뛰어요.
6. **화면 켜기 허용**
7. **배터리 사용 제한 없음**

그다음 "2. 앱 고르기"에서 띄울 앱을 켜요. "4. 테스트"에서 "테스트 알림 띄우기"를 누르고 화면을 끄면 잘 되는지 볼 수 있어요.

## 개인정보

톡바는 고른 앱의 알림을 읽어서 Now Bar에 다시 띄우기만 해요. 인터넷 권한이 없어서 알림 내용을 어디로도 보낼 수 없어요. 자세한 내용은 [개인정보처리방침](PRIVACY.md)에 있어요.

## 알아둘 점

- 카드 아이콘은 어느 앱 알림이든 톡바 아이콘이에요. 삼성이 그렇게 그려요.
- 알림창에는 원래 앱 알림과 톡바 알림이 둘 다 보여요.
- 구글은 채팅 메시지를 실시간 알림(Live Update)에 쓰지 말라고 안내해요. 나중 안드로이드·One UI 업데이트에서 동작이 바뀔 수 있어요.
- 카카오·삼성과 관계없는 개인 개발 앱이에요. 카카오톡은 카카오의 상표이고, Galaxy·One UI·Now Bar는 삼성전자의 상표예요.

## 문제·제안

[Issues](https://github.com/skdlzlvk/TalkBar/issues)에 남겨주세요.

## 직접 빌드

JDK 21과 Android SDK가 있으면 `./gradlew assembleRelease`로 빌드해요. 구조와 실측 기록은 [DEVELOPMENT.md](DEVELOPMENT.md), 출시 절차는 [RELEASE.md](RELEASE.md)에 있어요.

## 라이선스

[MIT](LICENSE)

---

## English

TalkBar shows new notifications from KakaoTalk and other apps you choose in the Galaxy **Now Bar** on the lock screen and Always On Display, and turns the screen on when one arrives.

- **Requires** a Galaxy phone with Android 16 or later (tested on One UI 9).
- **Install:** download `TalkBar-1.0.0.apk` from [Releases](https://github.com/skdlzlvk/TalkBar/releases/latest) and open it. Allow installing from unknown sources if asked.
- **Setup:** open TalkBar and turn on every item under "1. Setup". If notification access is greyed out: App info → ⋮ → Allow restricted settings.
- **Samsung Now Bar:** because TalkBar is not a Samsung partner app, turn on Settings → Developer options → ‘Live notifications for all apps’. No Developer options? Settings → About phone → Software information → tap Build number 7 times.
- **Apps:** choose apps under "2. Apps". Only KakaoTalk is on at first. With more than one app on, the card shows the app name.
- **Privacy:** no internet permission. Notification content never leaves your phone. See the [privacy policy](PRIVACY.md).
- Independent app, not affiliated with Kakao or Samsung. KakaoTalk is a trademark of Kakao Corp.; Galaxy, One UI and Now Bar are trademarks of Samsung Electronics.

License: [MIT](LICENSE)
