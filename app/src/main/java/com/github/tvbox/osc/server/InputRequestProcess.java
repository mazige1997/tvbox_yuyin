package com.github.tvbox.osc.server;

import android.text.TextUtils;

import com.github.tvbox.osc.voice.VoiceCommandManager;
import com.google.gson.JsonObject;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "voice": {
                            return handleVoiceAction(params);
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "liveApi": {
                            mDataReceiver.onLiveApiReceived(params.get("url").trim());
                            break;
                        }
                        case "danmuApi": {
                            mDataReceiver.onDanmuApiReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            String url = params.get("url");
                            if (url != null && url.trim().length() > 0) {
                                mDataReceiver.onPushReceived(url.trim());
                            }
                            break;
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }

    /** POST /action?do=voice&text=xxx: 语音指令入口(与GET /voice一致) */
    private NanoHTTPD.Response handleVoiceAction(Map<String, String> params) {
        String text = params.get("text");
        if (TextUtils.isEmpty(text)) text = params.get("word");
        if (TextUtils.isEmpty(text)) {
            JsonObject result = new JsonObject();
            result.addProperty("code", -1);
            result.addProperty("msg", "参数text不能为空");
            JsonObject cmdJson = new JsonObject();
            cmdJson.addProperty("type", "unknown");
            result.add("command", cmdJson);
            return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, result.toString());
        }
        JsonObject result = VoiceCommandManager.get().handle(ControlManager.mContext, text.trim());
        return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, result.toString());
    }
}
