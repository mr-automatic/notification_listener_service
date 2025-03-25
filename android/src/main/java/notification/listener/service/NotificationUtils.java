package notification.listener.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import notification.listener.service.models.Action;

public final class NotificationUtils {

    private static final String TAG = "NotificationUtils";
    private static final Set<String> REPLY_KEYWORDS = new HashSet<>(Arrays.asList(
            "reply", "android.intent.extra.text"
    ));
    private static final CharSequence INPUT_KEYWORD = "input";

    // Приватный конструктор — запрещает создание экземпляра утилитарного класса
    private NotificationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable == null) return null;

        final Bitmap bmp = Bitmap.createBitmap(
                Math.max(drawable.getIntrinsicWidth(), 1),
                Math.max(drawable.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888
        );

        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bmp;
    }

    public static boolean isPermissionGranted(Context context) {
        String packageName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");

        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName componentName = ComponentName.unflattenFromString(name);
                if (componentName != null &&
                        TextUtils.equals(packageName, componentName.getPackageName())) {
                    return true;
                }
            }
        }

        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static Action getQuickReplyAction(Notification n, String packageName) {
        NotificationCompat.Action action = null;

        if (Build.VERSION.SDK_INT >= 24) {
            action = getQuickReplyAction(n);
            if (action == null) {
                Log.d(TAG, "No standard quick reply action found, checking wearable extender.");
            }
        }

        if (action == null) {
            action = getWearReplyAction(n);
            if (action == null) {
                Log.d(TAG, "No wearable quick reply action found.");
                return null;
            }
        }

        return new Action(action, packageName, true);
    }

    private static NotificationCompat.Action getQuickReplyAction(Notification n) {
        int count = NotificationCompat.getActionCount(n);
        for (int i = 0; i < count; i++) {
            NotificationCompat.Action action = NotificationCompat.getAction(n, i);
            if (action != null && action.getRemoteInputs() != null) {
                for (RemoteInput remoteInput : action.getRemoteInputs()) {
                    if (remoteInput != null && remoteInput.getResultKey() != null &&
                            isKnownReplyKey(remoteInput.getResultKey())) {
                        return action;
                    }
                }
            }
        }
        return null;
    }

    private static NotificationCompat.Action getWearReplyAction(Notification n) {
        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender(n);
        for (NotificationCompat.Action action : wearableExtender.getActions()) {
            if (action.getRemoteInputs() != null) {
                for (RemoteInput remoteInput : action.getRemoteInputs()) {
                    String key = remoteInput.getResultKey();
                    if (key == null) continue;

                    key = key.toLowerCase(Locale.ROOT);

                    if (isKnownReplyKey(key)) {
                        return action;
                    } else if (key.contains(INPUT_KEYWORD)) {
                        return action;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isKnownReplyKey(String resultKey) {
        if (TextUtils.isEmpty(resultKey)) return false;
        resultKey = resultKey.toLowerCase(Locale.ROOT);

        for (String keyword : REPLY_KEYWORDS) {
            if (resultKey.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}