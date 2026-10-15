# Instrumentation shares this dependency with the tested app. Keep its public API so
# the separately optimized AndroidJUnitRunner can still report results and trace actions.
-keep class androidx.tracing.Trace {
    public static *;
}
# The instrumentation APK treats the app's Kotlin stdlib as a provided dependency.
# Retain that shared ABI in QA; these rules are deliberately absent from production release.
-keep class kotlin.** { *; }
# The separate test APK reads non-final resource fields from the app at runtime.
-keep class pl.vistula.checker.R$id { *; }
-keep class pl.vistula.checker.R$string { *; }
