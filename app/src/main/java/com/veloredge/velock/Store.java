package com.veloredge.velock;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny preference store: where the padlock sits on screen. */
final class Store {

    private static final String FILE = "velock";
    private static final String KEY_X = "bubble_x";
    private static final String KEY_Y = "bubble_y";

    private Store() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int x(Context c) {
        return prefs(c).getInt(KEY_X, -1);
    }

    static int y(Context c) {
        return prefs(c).getInt(KEY_Y, -1);
    }

    static void save(Context c, int x, int y) {
        prefs(c).edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply();
    }

}
