# 릴리스 빌드에서 디버그 로그 호출을 없앤다.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
