package com.veloredge.velock;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements LockState.Observer {

    static final String EXTRA_NEEDS_OVERLAY = "needs_overlay";

    private static final int REQUEST_NOTIFICATIONS = 71;

    private TextView chip;
    private TextView primary;
    private TextView secondary;
    private TextView overlayState;
    private TextView notificationState;
    private ImageView emblem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chip = findViewById(R.id.chip);
        primary = findViewById(R.id.primary);
        secondary = findViewById(R.id.secondary);
        overlayState = findViewById(R.id.overlay_state);
        notificationState = findViewById(R.id.notification_state);
        emblem = findViewById(R.id.emblem);

        primary.setOnClickListener(v -> onPrimary());
        secondary.setOnClickListener(v -> LockOverlayService.freezeNow(this));
        findViewById(R.id.row_overlay).setOnClickListener(v -> openOverlaySettings());
        findViewById(R.id.row_notification).setOnClickListener(v -> onNotificationRow());

        if (getIntent().getBooleanExtra(EXTRA_NEEDS_OVERLAY, false)
                && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_allow_overlay, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LockState.observe(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onStop() {
        LockState.observe(null);
        super.onStop();
    }

    @Override
    public void onLockStateChanged() {
        refresh();
    }

    // ---------------------------------------------------------------- actions

    private void onPrimary() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        if (LockState.running) {
            LockOverlayService.stop(this);
        } else {
            LockOverlayService.start(this);
        }
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void onNotificationRow() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
    }

    // ---------------------------------------------------------------- rendering

    private void refresh() {
        boolean overlayAllowed = Settings.canDrawOverlays(this);
        boolean running = LockState.running;
        boolean locked = LockState.locked;
        boolean active = running || locked;

        chip.setText(locked ? R.string.chip_locked
                : running ? R.string.chip_active : R.string.chip_off);
        chip.setTextColor(getColor(active ? R.color.accent : R.color.text_secondary));
        chip.setBackgroundResource(active ? R.drawable.bg_chip_accent : R.drawable.bg_chip_muted);

        overlayState.setText(overlayAllowed ? R.string.state_allowed : R.string.state_required);
        overlayState.setTextColor(getColor(overlayAllowed ? R.color.text_secondary : R.color.accent));

        if (Build.VERSION.SDK_INT < 33) {
            notificationState.setText(R.string.state_not_needed);
        } else {
            boolean granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            notificationState.setText(granted ? R.string.state_allowed : R.string.state_optional);
        }
        notificationState.setTextColor(getColor(R.color.text_secondary));

        primary.setText(!overlayAllowed ? R.string.primary_grant
                : running ? R.string.primary_disable : R.string.primary_enable);
        secondary.setVisibility(running ? View.VISIBLE : View.GONE);
        secondary.setText(R.string.secondary_freeze);

        emblem.setImageTintList(ColorStateList.valueOf(getColor(
                active ? R.color.accent : R.color.text_muted)));
    }
}
