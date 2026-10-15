# WorkManager creates the worker by class name after process recreation and app updates.
-keep class pl.vistula.checker.SyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# Room 2.6.1's transitive consumer rule keeps the class, but omits its reflective constructor.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
# Keep crash reports readable with the matching release mapping.txt.
-keepattributes SourceFile,LineNumberTable
