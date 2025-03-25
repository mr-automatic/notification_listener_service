package notification.listener.service;

import static notification.listener.service.NotificationUtils.getBitmapFromDrawable;
import static notification.listener.service.models.ActionCache.cachedNotifications;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;

import notification.listener.service.models.Action;

@SuppressLint("OverrideAbstract")
@RequiresApi(api = VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "NotificationListener";

    @RequiresApi(api = VERSION_CODES.KITKAT)
    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        handleNotification(notification, false);
    }

    @RequiresApi(api = VERSION_CODES.KITKAT)
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        handleNotification(sbn, true);
    }

    @RequiresApi(api = VERSION_CODES.KITKAT)
    private void handleNotification(StatusBarNotification notification, boolean isRemoved) {
        if (notification == null || notification.getNotification() == null) {
            Log.w(TAG, "Notification or its content is null. Skipping...");
            return;
        }

        // ⛔ Пропуск собственных уведомлений
        if (getPackageName().equals(notification.getPackageName())) {
            return;
        }

        String packageName = notification.getPackageName();
        Notification notif = notification.getNotification();
        Bundle extras = notif.extras;

        byte[] appIcon = getAppIcon(packageName);
        byte[] largeIcon = null;

        Action action = NotificationUtils.getQuickReplyAction(notif, packageName);
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            largeIcon = getNotificationLargeIcon(getApplicationContext(), notif);
        }

        Intent intent = new Intent(NotificationConstants.INTENT);
        intent.putExtra(NotificationConstants.PACKAGE_NAME, packageName);
        intent.putExtra(NotificationConstants.ID, notification.getId());
        intent.putExtra(NotificationConstants.CAN_REPLY, action != null);

        // Базовые поля уведомления
        intent.putExtra(NotificationConstants.NOTIFICATION_TAG, notification.getTag());
        intent.putExtra(NotificationConstants.POST_TIME, notification.getPostTime());
        intent.putExtra(NotificationConstants.IS_ONGOING, notification.isOngoing());
        intent.putExtra(NotificationConstants.IS_CLEARABLE, notification.isClearable());

        // Пользователь и ключи уведомлений
        if (Build.VERSION.SDK_INT >= VERSION_CODES.KITKAT_WATCH) {
            intent.putExtra(NotificationConstants.NOTIFICATION_KEY, notification.getKey());
        }

        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            intent.putExtra(NotificationConstants.GROUP_KEY, notification.getGroupKey());
            if (notification.getUser() != null) {
                intent.putExtra(NotificationConstants.USER, notification.getUser().toString());
                intent.putExtra(NotificationConstants.USER_ID, notification.getUser().getIdentifier());
            }
        }

        if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
            intent.putExtra(NotificationConstants.IS_GROUP, notification.isGroup());
        }

        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            intent.putExtra(NotificationConstants.IS_APP_GROUP, notification.isAppGroup());
        }

        // Кешируем экшн, если он есть
        if (action != null) {
            cachedNotifications.put(notification.getId(), action);
        }

        // Иконки
        intent.putExtra(NotificationConstants.NOTIFICATIONS_ICON, appIcon);
        intent.putExtra(NotificationConstants.NOTIFICATIONS_LARGE_ICON, largeIcon);

        // Текст уведомления
        if (extras != null) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            intent.putExtra(NotificationConstants.NOTIFICATION_TITLE, title != null ? title.toString() : null);
            intent.putExtra(NotificationConstants.NOTIFICATION_CONTENT, text != null ? text.toString() : null);
            intent.putExtra(NotificationConstants.IS_REMOVED, isRemoved);

            // Картинка (EXTRA_PICTURE)
            if (extras.containsKey(Notification.EXTRA_PICTURE)) {
                Object pictureObj = extras.get(Notification.EXTRA_PICTURE);
                if (pictureObj instanceof Bitmap) {
                    byte[] pictureBytes = bitmapToByteArray((Bitmap) pictureObj, Bitmap.CompressFormat.PNG, 100);
                    intent.putExtra(NotificationConstants.EXTRAS_PICTURE, pictureBytes);
                    intent.putExtra(NotificationConstants.HAVE_EXTRA_PICTURE, true);
                } else {
                    intent.putExtra(NotificationConstants.HAVE_EXTRA_PICTURE, false);
                }
            } else {
                intent.putExtra(NotificationConstants.HAVE_EXTRA_PICTURE, false);
            }
        }

        sendBroadcast(intent);
    }

    public byte[] getAppIcon(String packageName) {
        try {
            PackageManager manager = getBaseContext().getPackageManager();
            Drawable icon = manager.getApplicationIcon(packageName);
            Bitmap bitmap = getBitmapFromDrawable(icon);
            if (bitmap != null) {
                return bitmapToByteArray(bitmap, Bitmap.CompressFormat.PNG, 100);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "App icon not found for package: " + packageName, e);
        } catch (Exception e) {
            Log.e(TAG, "Error while getting app icon", e);
        }
        return null;
    }

    @RequiresApi(api = VERSION_CODES.M)
    private byte[] getNotificationLargeIcon(Context context, Notification notification) {
        try {
            Icon largeIcon = notification.getLargeIcon();
            if (largeIcon == null) return null;

            Drawable iconDrawable = largeIcon.loadDrawable(context);
            if (iconDrawable instanceof BitmapDrawable) {
                Bitmap iconBitmap = ((BitmapDrawable) iconDrawable).getBitmap();
                return bitmapToByteArray(iconBitmap, Bitmap.CompressFormat.PNG, 80);
            }
        } catch (Exception e) {
            Log.e(TAG, "getNotificationLargeIcon failed", e);
        }
        return null;
    }

    private byte[] bitmapToByteArray(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        if (bitmap == null) return null;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(format, quality, stream);
            return stream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert bitmap to byte array", e);
            return null;
        }
    }
}