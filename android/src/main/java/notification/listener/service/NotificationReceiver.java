package notification.listener.service;

import static notification.listener.service.NotificationConstants.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.RequiresApi;

import io.flutter.plugin.common.EventChannel.EventSink;

import java.util.HashMap;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "NotificationReceiver";

    private volatile EventSink eventSink;

    public NotificationReceiver(EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @RequiresApi(api = VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || eventSink == null) {
            Log.w(TAG, "Intent or eventSink is null. Skipping.");
            return;
        }

        HashMap<String, Object> data = extractNotificationData(intent);

        try {
            eventSink.success(data);
        } catch (Exception e) {
            Log.e(TAG, "Error sending event to Flutter via eventSink", e);
        }
    }

    private HashMap<String, Object> extractNotificationData(Intent intent) {
        HashMap<String, Object> data = new HashMap<>();

        data.put("id", intent.getIntExtra(ID, -1));
        data.put("packageName", safeString(intent.getStringExtra(PACKAGE_NAME)));
        data.put("title", safeString(intent.getStringExtra(NOTIFICATION_TITLE)));
        data.put("content", safeString(intent.getStringExtra(NOTIFICATION_CONTENT)));
        data.put("notificationIcon", intent.getByteArrayExtra(NOTIFICATIONS_ICON));
        data.put("notificationExtrasPicture", intent.getByteArrayExtra(EXTRAS_PICTURE));
        data.put("largeIcon", intent.getByteArrayExtra(NOTIFICATIONS_LARGE_ICON));
        data.put("haveExtraPicture", intent.getBooleanExtra(HAVE_EXTRA_PICTURE, false));
        data.put("hasRemoved", intent.getBooleanExtra(IS_REMOVED, false));
        data.put("canReply", intent.getBooleanExtra(CAN_REPLY, false));
        data.put("notificationTag", safeString(intent.getStringExtra(NOTIFICATION_TAG)));
        data.put("postTime", intent.getLongExtra(POST_TIME, 0));
        data.put("isOngoing", intent.getBooleanExtra(IS_ONGOING, false));
        data.put("isClearable", intent.getBooleanExtra(IS_CLEARABLE, false));
        data.put("userId", safeString(intent.getStringExtra(USER_ID)));
        data.put("notificationKey", safeString(intent.getStringExtra(NOTIFICATION_KEY)));
        data.put("groupKey", safeString(intent.getStringExtra(GROUP_KEY)));
        data.put("isGroup", intent.getBooleanExtra(IS_GROUP, false));
        data.put("isAppGroup", intent.getBooleanExtra(IS_APP_GROUP, false));
        data.put("user", safeString(intent.getStringExtra(USER)));

        return data;
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }
}