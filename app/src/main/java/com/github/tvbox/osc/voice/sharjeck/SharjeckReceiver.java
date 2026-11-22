package com.github.tvbox.osc.voice.sharjeck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 夏杰语音指令广播接收器(按官方对接文档V1.2/V1.4注册, Manifest静态声明)。
 * 应用进程未运行时也能接收夏杰语音的重新注册要求(app.register.require)与指令分发
 * (action.live/action.video), 转交 {@link AIOpenCommandHandler} 处理。
 * 服务分发路径由 {@link SharjeckService} 承接, 处理逻辑一致。
 *
 * @author voice
 */
public class SharjeckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AIOpenCommandHandler.handle(context, intent);
    }
}
