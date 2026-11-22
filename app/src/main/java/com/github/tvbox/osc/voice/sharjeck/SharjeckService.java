package com.github.tvbox.osc.voice.sharjeck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.LOG;

/**
 * 夏杰语音指令接收服务(按官方对接文档V1.2/V1.4创建)。
 * 夏杰语音通过 startService(action.live / action.video / app.register.require)分发指令,
 * 本服务接收后转交 {@link AIOpenCommandHandler} 统一处理。
 * Android 8.0+ 若被 startForegroundService 启动需及时调用 startForeground, 处理完成后自动退出。
 * 广播分发路径由 {@link SharjeckReceiver} 承接, 两者处理逻辑一致。
 *
 * @author voice
 */
public class SharjeckService extends Service {
    private static final String TAG = "SharjeckService";
    private static final String NOTIFICATION_CHANNEL_ID = "com.github.tvbox.osc.aiopen";
    private static final long STOP_DELAY_MS = 15000;

    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForce();
        mHandler.removeCallbacks(stopServiceTask);
        mHandler.postDelayed(stopServiceTask, STOP_DELAY_MS);
        AIOpenCommandHandler.handle(this, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler = null;
    }

    private void startForce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "语音指令服务", NotificationManager.IMPORTANCE_MIN);
            if (manager != null) manager.createNotificationChannel(channel);
            Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = builder.setOngoing(true)
                    .setSmallIcon(R.drawable.app_icon)
                    .setPriority(Notification.PRIORITY_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            startForeground(10, notification);
        } catch (Throwable th) {
            LOG.e(TAG + " startForeground error: " + th.getMessage());
        }
    }

    private final Runnable stopServiceTask = new Runnable() {
        @Override
        public void run() {
            try {
                stopSelf();
            } catch (Throwable e) {
                LOG.e(TAG + " stopSelf error: " + e.getMessage());
            }
        }
    };
}
