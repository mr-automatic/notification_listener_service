package notification.listener.service;

import static notification.listener.service.NotificationUtils.isPermissionGranted;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import notification.listener.service.models.Action;
import notification.listener.service.models.ActionCache;

public class NotificationListenerServicePlugin implements FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler, PluginRegistry.ActivityResultListener, EventChannel.StreamHandler {

    private static final String CHANNEL_TAG = "x-slayer/notifications_channel";
    private static final String EVENT_TAG = "x-slayer/notifications_event";
    private static final String TAG = "NotificationPlugin";

    private MethodChannel channel;
    private EventChannel eventChannel;
    private NotificationReceiver notificationReceiver;
    private Context context;
    private Activity mActivity;

    private MethodChannel.Result pendingResult;
    private static final int REQUEST_CODE_FOR_NOTIFICATIONS = 1199;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();

        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), CHANNEL_TAG);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), EVENT_TAG);
        eventChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) {
            channel.setMethodCallHandler(null);
        }
        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        pendingResult = result;

        switch (call.method) {
            case "isPermissionGranted":
                result.success(isPermissionGranted(context));
                break;

            case "requestPermission":
                if (mActivity != null) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_NOTIFICATIONS);
                } else {
                    result.error("Activity", "Activity is not attached", null);
                }
                break;

            case "sendReply":
                handleSendReply(call, result);
                break;

            case "scheduleReply":
                handleScheduleReply(call, result);
                break;

            default:
                result.notImplemented();
        }
    }

    private void handleSendReply(MethodCall call, MethodChannel.Result result) {
        String message = call.argument("message");
        Integer notificationId = call.argument("notificationId");

        if (message == null || notificationId == null) {
            result.error("InvalidArguments", "message or notificationId is null", null);
            return;
        }

        Action action = ActionCache.cachedNotifications.get(notificationId);
        if (action == null) {
            result.error("Notification", "Can't find this cached notification", null);
            return;
        }

        try {
            action.sendReply(context, message);
            result.success(true);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to send reply", e);
            result.success(false);
        }
    }

    private void handleScheduleReply(MethodCall call, MethodChannel.Result result) {
        Integer notificationId = call.argument("notificationId");
        String notificationKey = call.argument("notificationKey");
        String message = call.argument("message");
        Integer delaySeconds = call.argument("delay");

        if (notificationId == null || notificationKey == null || message == null || delaySeconds == null) {
            result.error("InvalidArguments", "One or more arguments are null", null);
            return;
        }

        scheduleReply(context, notificationId, notificationKey, message, delaySeconds);
        result.success(true);
    }

    private void scheduleReply(Context context, int notificationId, String notificationKey, String message, int delaySeconds) {
        if (context == null) {
            Log.e(TAG, "Context is null in scheduleReply()");
            return;
        }

        WorkManager workManager = WorkManager.getInstance(context);

        Data inputData = new Data.Builder()
                .putInt("notificationId", notificationId)
                .putString("notificationKey", notificationKey)
                .putString("message", message)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DelayedReplyWorker.class)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(inputData)
                .build();

        workManager.enqueue(workRequest);
        Log.i(TAG, "Scheduled delayed reply: " + message + " in " + delaySeconds + " seconds");
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.mActivity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        this.mActivity = null;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        if (context == null) {
            Log.e(TAG, "Context is null in onListen");
            return;
        }

        try {
            IntentFilter intentFilter = new IntentFilter(NotificationConstants.INTENT);
            notificationReceiver = new NotificationReceiver(events);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.registerReceiver(notificationReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(notificationReceiver, intentFilter);
            }

            Intent listenerIntent = new Intent(context, NotificationReceiver.class);
            context.startService(listenerIntent);

            Log.i(TAG, "Started the notifications tracking service.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register notification receiver", e);
        }
    }

    @Override
    public void onCancel(Object arguments) {
        try {
            if (notificationReceiver != null) {
                context.unregisterReceiver(notificationReceiver);
                notificationReceiver = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while unregistering notification receiver", e);
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FOR_NOTIFICATIONS && pendingResult != null) {
            if (resultCode == Activity.RESULT_OK) {
                pendingResult.success(true);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                pendingResult.success(isPermissionGranted(context));
            } else {
                pendingResult.success(false);
            }
            pendingResult = null;
            return true;
        }
        return false;
    }
}
