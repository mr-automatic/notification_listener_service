package notification.listener.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import notification.listener.service.models.Action;
import notification.listener.service.models.ActionCache;

public class DelayedReplyWorker extends Worker {

    private static final String TAG = "DelayedReplyWorker";

    // Ключи для передачи данных в воркер
    public static final String KEY_NOTIFICATION_ID = "notificationId";
    public static final String KEY_NOTIFICATION_KEY = "notificationKey";
    public static final String KEY_MESSAGE = "message";

    public DelayedReplyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Извлекаем входные данные
        Data input = getInputData();
        int notificationId = input.getInt(KEY_NOTIFICATION_ID, -1);
        String notificationKey = input.getString(KEY_NOTIFICATION_KEY);
        String message = input.getString(KEY_MESSAGE);

        // Логируем вход
        Log.d(TAG, "Starting delayed reply - id: " + notificationId + ", key: " + notificationKey + ", message: " + message);

        // Проверка входных параметров
        if (notificationId == -1 || isNullOrEmpty(notificationKey) || isNullOrEmpty(message)) {
            Log.e(TAG, "Invalid input data. Aborting.");
            return Result.failure();
        }

        // Поиск действия в кеше
        Action action = ActionCache.cachedNotifications.get(notificationId);
        if (action == null) {
            Log.e(TAG, "Failed to find cached action for id: " + notificationId);
            return Result.failure();
        }

        try {
            action.sendReply(getApplicationContext(), message);
            Log.i(TAG, "Successfully sent delayed reply for id: " + notificationId);

            // Очищаем кэш после выполнения
            ActionCache.cachedNotifications.remove(notificationId);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending delayed reply", e);
            return Result.failure();
        }
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
