package com.veloredge.velock;

import android.content.Context;

final class Ui {

    private Ui() {
    }

    static int dp(Context c, float value) {
        return Math.round(c.getResources().getDisplayMetrics().density * value);
    }

    static float dpf(Context c, float value) {
        return c.getResources().getDisplayMetrics().density * value;
    }
}
