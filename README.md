# 똑똑 (ddokddok)

알림이 오면 화면을 **전원 버튼으로 켠 것처럼** 켜 주는 개인용 안드로이드 앱. 시스템이 '키를 눌러 켬'으로 기록하므로 잠금화면이 얼굴 인식을 바로 시작한다.

## 설치 파일 받기

[ddokddok-0.1.1.apk 내려받기](https://github.com/seooki/ddok-ddok/raw/main/release/ddokddok-0.1.1.apk) (Android 12 이상, 전원 버튼 방식은 Android 16 이상)

1. 폰에서 위 링크를 열어 받고 설치한다. '출처를 알 수 없는 앱' 설치를 허용하고, 막히면 설정 > 보안 및 개인정보 보호 > 자동 차단을 잠시 끈다.
2. 앱의 '시작하기' 안내대로 알림 접근과 접근성 서비스를 켠다. 막히면 앱 정보 > 오른쪽 위 ⋮ > '제한된 설정 허용'을 누른 뒤 다시 켠다.
3. '화면 켜기 테스트'를 AOD를 끈 상태와 켠 상태에서 한 번씩 해 본다.

## 동작 원리

| 단계 | 구현 |
|---|---|
| 알림 감지 | `WakeNotificationListener` (NotificationListenerService). 소리·진동 알림만 받도록 `conversations\|alerting` 필터를 건다. |
| 켤지 판단 | `core/WakePolicy` (안드로이드 API에 의존하지 않는 순수 Kotlin, 단위 테스트 대상) |
| 화면 켜기 | `wake/ScreenWaker` |
| 끄기 | 시스템에 맡긴다. 전원 버튼으로 켰을 때와 똑같이 꺼진다. |

화면 켜는 방식:

- **전원 버튼 방식** (Android 16+, 기본값): 접근성 서비스의 `performGlobalAction(GLOBAL_ACTION_MENU)`. 화면이 꺼져 있으면 메뉴 키가 '화면을 켜는 키'로 처리되어 `WAKE_REASON_WAKE_KEY`로 기록된다. AOSP 잠금화면은 이 이유를 전원 버튼과 같은 목록(`config_face_auth_wake_up_triggers`)에 두고 얼굴 인식을 시작한다.
- **기본 방식** (대체): `WakeLock` + `ACQUIRE_CAUSES_WAKEUP`. `WAKE_REASON_APPLICATION`으로 기록되어 얼굴 인식은 시작되지 않는다. Glimpse Notifications 같은 기존 앱이 이 방식이다.

켜지 않는 경우: 화면이 켜져 있음, 진행 중·무음 알림, 끈 앱, 방해 금지 모드, 소리 안 내는 묶음 알림, '한 번만 알림' 업데이트, 전화·알람, 잠금화면에 숨긴 알림, 통화 중, 방해하지 않을 시간, 간격(기본 5초), 엎어 둠(가속도 센서), 주머니 속(근접 센서, 기본 꺼짐).

## 구조

```
app/src/main/java/com/seooki/ddokddok/
  core/     판단 규칙과 설정 모델 (순수 Kotlin)
  data/     설정(SharedPreferences), 기록(noBackup 폴더 TSV), 앱 목록
  wake/     알림 처리, 화면 켜기, 센서 확인, 화면 켜기 테스트
  service/  알림 리스너, 접근성 서비스
  system/   권한 상태 확인과 시스템 설정 화면 열기
  ui/       Compose 화면 (홈, 화면 켜는 방식·테스트, 알림 조건, 앱 선택, 최근 기록)
```

## 빌드와 테스트

```
gradlew :app:testDebugUnitTest --offline   # 판단 규칙 단위 테스트
gradlew :app:assembleRelease --offline     # 서명된 릴리스 APK
```

- 릴리스 서명 정보는 `keystore.properties`와 `keystore/`에 있고 둘 다 저장소에서 제외된다. **키를 잃어버리면 같은 앱으로 업데이트 설치를 할 수 없으니 반드시 오프라인으로 백업한다.**
- 병합 매니페스트에 `INTERNET` 권한이 들어오면 빌드가 실패한다(개인정보 가드).
- `targetSdk`는 36으로 둔다. 37로 올리기 전에 기본 방식이 `TURN_SCREEN_ON` 권한 없이도 동작하는지 확인해야 한다.

## 실제 알림으로 검증하기 (`:notifier`)

`notifier` 모듈은 여러 종류의 실제 알림을 올리는 개발용 앱이다. 배포하지 않는다.

```
gradlew :notifier:assembleDebug --offline
adb install notifier/build/outputs/apk/debug/notifier-debug.apk
adb shell pm grant com.seooki.ddokddok.notifier android.permission.POST_NOTIFICATIONS
adb shell am broadcast -f 0x20 -n com.seooki.ddokddok.notifier/.PostReceiver --es case <시나리오>
```

시나리오: `alert`, `low`, `min`, `ongoing`, `alert_once`(두 번 보내면 두 번째는 업데이트), `update`, `group_children`, `group_summary`, `call`, `secret`, `messaging`, `fullscreen`, `cancel_all`.
화면을 끈 뒤 보내고 `PowerManagerService`의 "Waking up ... reason=" 로그와 앱 기록(디버그 빌드: `run-as com.seooki.ddokddok cat no_backup/wake_events.tsv`)을 본다.

## 확인 기록

2026-09-30, Android 16 에뮬레이터(네트워크 차단):

- 전원 버튼 방식은 `WAKE_REASON_WAKE_KEY`, 기본 방식은 `WAKE_REASON_APPLICATION`으로 기록된다(`PowerManagerService` 로그). 릴리스 빌드(R8)도 같다.
- 엎어짐, 간격, 방해 금지 규칙과 대체 방식이 동작한다.
- 앱을 강제 중지하면 안드로이드가 접근성 서비스를 꺼 버린다. 다시 켜야 한다(홈 화면에 표시됨).
- `uiautomator`를 실행하는 동안에는 다른 접근성 서비스가 잠시 멈춘다(테스트 도구의 특성).
- 재부팅 후 처음 잠금을 풀기 전에는 앱이 실행되지 않아 화면을 켜지 않는다. 한 번 풀면 서비스가 다시 연결되고 정상 동작한다.
- 배터리 최적화 예외 요청 창과 테스트 답변에 따른 자동 조정이 동작한다.
- 실제 알림 14개 시나리오가 모두 기대대로다: 소리 알림·메시지 갱신·대화 알림은 켬(`WAKE_KEY`), 무음·진행 중은 시스템이 전달하지 않음, 한 번만 울리는 업데이트는 안 켬, 묶음은 소리 나는 쪽에서 한 번만 켬, 전화·잠금화면 비공개는 이유를 남기고 안 켬, 전체 화면 알림은 시스템이 켜고 앱은 중복으로 켜지 않음.
- 자원 정리: 처리 후 남은 CPU 잠금·센서 연결·동적 수신기가 없다. 가속도 센서는 알림마다 1초 안에 등록과 해제가 끝난다.
- 디버그 빌드의 StrictMode(스레드·VM 정책 전체)에서 앱 시작, 전 화면 이동, 알림 처리 중 위반이 없다.

AOD 주의점(소스 분석, 에뮬레이터는 AOD 미지원이라 실기기 확인 필요):

- 화면이 완전히 꺼져 있으면 시스템이 메뉴 키를 삼키고 화면만 켠다.
- AOD(도즈)가 떠 있으면 메뉴 키가 잠금화면까지 전달된다. AOSP 잠금화면(`KeyguardKeyEventInteractor`)은 손을 뗄 때(ACTION_UP) 기기가 깨어 있다고 판단하면 메뉴 키를 잠금 해제 요청으로 받아 PIN 입력 화면을 띄운다. 깨어남 전달과 키 전달의 순서에 따라 달라지는 경합이다.
- 대응: 테스트에서 'PIN 입력 화면이 떴어요'를 고르면 AOD일 때만 기본 방식으로 켠다(`avoidMenuKeyWhenDozing`). 화면이 완전히 꺼진 상태에서는 계속 전원 버튼 방식을 쓴다.

아직 확인하지 않은 것: 갤럭시(One UI)에서 전원 버튼 방식이 화면을 켜는지, 얼굴 인식이 시작되는지, AOD에서 PIN 입력 화면이 뜨는지. 앱의 '화면 켜기 테스트'로 AOD를 켠 상태와 끈 상태 각각 확인한다.
