# 좌석예보 FCM 설정 및 알림 권한

## 담당 기능

좌석예보 앱에서 돌발 알림 푸시를 받을 수 있도록 Firebase Cloud Messaging 설정과 Android 알림 권한을 준비하였다.

## 작업 목적

좌석예보 앱은 만석 위험, 출발 시각, 대체 경로 안내와 같은 돌발 상황을 사용자에게 푸시 알림으로 전달해야 한다.  
이를 위해 Android 앱에서 FCM Token을 발급받고, Android 13 이상에서 필요한 알림 권한을 요청하는 기능을 준비하였다.

---

## 1. AndroidManifest.xml 권한 추가

Android 13 이상에서는 알림을 보내기 위해 `POST_NOTIFICATIONS` 권한이 필요하다.

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

적용 위치:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        ...
    </application>

</manifest>
```

---

## 2. MainActivity.java 알림 권한 요청

앱 실행 시 Android 13 이상 기기에서 알림 권한을 요청한다.

```java
private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION
            );
        }
    }
}
```

`onCreate()`에서 호출한다.

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    requestNotificationPermission();
    checkFcmToken();
}
```

---

## 3. 권한 결과 처리

사용자가 알림 권한을 허용하거나 거부했을 때 결과를 Logcat으로 확인한다.

```java
@Override
public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("FCM", "알림 권한 허용");
        } else {
            Log.d("FCM", "알림 권한 거부");
        }
    }
}
```

---

## 4. FCM Token 확인

FirebaseMessaging을 통해 FCM Token이 정상적으로 발급되는지 확인한다.

```java
private void checkFcmToken() {
    FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w("FCM", "FCM 토큰 가져오기 실패", task.getException());
                    return;
                }

                String token = task.getResult();
                Log.d("FCM", "FCM Token: " + token);
            });
}
```

---

## 5. FCM 메시지 수신 서비스 예시

추후 돌발 알림 푸시를 수신하기 위한 Firebase Messaging Service 예시는 다음과 같다.

```java
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "새 FCM Token 발급: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Log.d(TAG, "FCM 메시지 수신");

        if (message.getNotification() != null) {
            Log.d(TAG, "알림 제목: " + message.getNotification().getTitle());
            Log.d(TAG, "알림 내용: " + message.getNotification().getBody());
        }
    }
}
```

---

## 6. 실행 화면 구성

실행 화면에는 다음 내용을 포함한다.

1. FCM 설정 확인
2. 알림 권한 요청
3. FCM Token 발급 확인
4. 테스트 푸시 수신
5. 알림 권한 준비 완료

---

## 실행 결과 설명

좌석예보 앱에서 FCM 푸시 알림을 사용하기 위해 알림 권한을 요청하고, FCM Token 발급 및 테스트 푸시 수신을 확인하였다.  
이를 통해 향후 만석 위험, 출발 시각, 대체 경로 안내와 같은 돌발 알림 기능과 연동할 수 있는 기반을 준비하였다.

---

## GitHub 업로드 위치

현재 GitHub 저장소가 Android 앱 코드 구조가 아니라 Python/data/backend 중심으로 구성되어 있으므로, 해당 내용은 문서 파일로 업로드한다.

추천 위치:

```text
docs/fcm_notification_permission.md
```

추천 브랜치명:

```text
feature/fcm-notification-permission
```

추천 커밋 메시지:

```text
Add FCM notification permission documentation
```
