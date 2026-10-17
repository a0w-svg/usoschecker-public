package pl.vistula.checker;

import android.app.Application;
import pl.vistula.checker.ui.Appearance;

/** Restore the saved appearance before activities are inflated, including after APK updates. */
public final class CheckerApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Appearance.apply(this);
    }
}
