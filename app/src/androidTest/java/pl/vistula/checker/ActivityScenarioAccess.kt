package pl.vistula.checker

import android.app.Activity
import androidx.test.ext.junit.rules.ActivityScenarioRule

/** Resolve the current instance after recreation; never keep a stale Activity in a fixture. */
internal val <A : Activity> ActivityScenarioRule<A>.activity: A
    get() {
        var current: A? = null
        scenario.onActivity { current = it }
        return checkNotNull(current)
    }
