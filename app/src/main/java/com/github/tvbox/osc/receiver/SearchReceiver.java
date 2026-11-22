package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.util.AppManager;

import org.greenrobot.eventbus.EventBus;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class SearchReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.search.Action";

    /**
     * 发起一次远程搜索(远程搜索与语音搜索共用入口)
     */
    public static void send(Context context, String title) {
        send(context, title, -1);
    }

    /**
     * 发起一次远程搜索并指定"搜索后播放第N个结果"(语音搜索PlayIndex, 小于等于0时忽略)
     */
    public static void send(Context context, String title, int playIndex) {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString("title", title);
        if (playIndex > 0) bundle.putInt("playIndex", playIndex);
        intent.setAction(action);
        intent.setPackage(context.getPackageName());
        intent.setComponent(new ComponentName(context, SearchReceiver.class));
        intent.putExtras(bundle);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (action.equals(intent.getAction()) && intent.getExtras() != null) {
            if (AppManager.getInstance().getActivity(SearchActivity.class) != null) {
                AppManager.getInstance().backActivity(SearchActivity.class);
                EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SEARCH, intent.getExtras().getString("title")));
            } else {
                Intent newIntent = new Intent(context, SearchActivity.class);
                newIntent.putExtra("title", intent.getExtras().getString("title"));
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(newIntent);
            }
        }
    }
}