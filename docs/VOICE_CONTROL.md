# 语音助手接入指南(小爱音箱 / 天猫精灵 / 小度智能音箱)

本项目在原有**远程搜索**链路基础上, 增加了统一的**语音指令**入口, 支持小爱音箱、天猫精灵、小度智能音箱等语音助手控制 TVBox:

- 语音搜索(与远程搜索一致: `搜索狂飙`、直接说片名)
- 语音换台(电视直播): `换台到CCTV5`、`中央一台`、`第5台`、`下一个台`
- 语音换剧集: `第12集`、`下一集`、`上一集`
- 播放控制: `暂停`、`继续播放`、`快进30秒`、`音量大点`、`静音`

## 一、支持的语音指令

| 类型 | 指令示例 | 说明 |
| --- | --- | --- |
| 搜索 | `搜索狂飙` / `帮我搜一下庆余年` / `狂飙` | 直接说片名也按搜索处理 |
| 换台(按名) | `换台到CCTV5` / `切换到湖南卫视` / `中央一台` / `CCTV5` | 中央/央视N台自动对应CCTV-N, 频道名模糊匹配 |
| 换台(按号) | `第5台` / `换到第35台` / `换台15` | 全量频道序号(跳过加密分组), 从1开始 |
| 换台(上下) | `下一个台` / `上个台` / `换台` | "换台"单独说=下一个台 |
| 换剧集 | `第12集` / `播放第三集` / `下一集` / `上一集` | 中文数字支持(一~百) |
| 播放控制 | `暂停` / `继续播放` / `快进30秒` / `快退2分钟` | 直播模式下不支持快进快退 |
| 音量 | `音量大点` / `小声点` / `静音` | 系统音量, 无需界面在前台 |

> 未识别的文本兜底按**搜索**处理, 与远程搜索交互习惯一致。
> 搜索关键词不做中文数字转换(《八佰》等片名不受影响); 换台指令会自动转换。

## 二、统一语音入口

三条入口最终都汇聚到 `VoiceCommandManager`(解析: `VoiceCommandParser`, 事件分发: `VoiceEvent`):

```
小爱音箱/天猫精灵/小度 ──云技能──► 你的技能服务端 ──HTTP──► http://<TVBox IP>:9978/voice?text=...
电视端内置语音助手 ──intent──► VoiceReceiver(广播)
手机遥控App / Home Assistant ──广播或HTTP──► 同上
网页遥控(http://<IP>:9978/) ──语音指令输入框──► POST /action?do=voice
```

### 1. HTTP API(推荐, 云技能服务端调用)

```bash
# GET
curl "http://192.168.1.100:9978/voice?text=%E6%90%9C%E7%B4%A2%E7%8B%82%E9%A3%99"

# POST(表单)
curl -X POST "http://192.168.1.100:9978/action" -d "do=voice&text=换台到CCTV5"
```

响应(JSON):

```json
{
  "code": 0,
  "msg": "正在切换到频道: CCTV5",
  "command": { "type": "live_channel", "keyword": "CCTV5" }
}
```

`command.type` 取值: `search` / `live_channel` / `live_next` / `live_prev` / `episode` / `episode_next` / `episode_prev` / `pause` / `resume` / `seek_forward` / `seek_back` / `volume_up` / `volume_down` / `mute` / `unknown`。

> 端口: 默认 `9978`, 被占用时自动递增到 9999; 电视与音箱/服务端需在同一局域网。

### 2. 广播入口(adb / 手机App / Home Assistant)

```bash
adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "搜索狂飙"
adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "换台到CCTV5"
adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "第12集"
```

extras 兼容字段: `text` / `query` / `word` / `keyword` / `voice_text` / `command` / `query(SearchManager.QUERY)`。
同时兼容系统搜索 intent: `android.intent.action.SEARCH`、`android.search.action.GLOBAL_SEARCH`、`android.intent.action.WEB_SEARCH`(部分电视端内置语音助手会以这类 intent 唤起第三方应用)。

## 三、三大音箱接入步骤(云技能)

三家音箱厂商均要求通过各自的开放平台注册技能, 由技能服务端接收语音文本后转发给 TVBox 的 HTTP 接口。

### 小爱音箱

1. 登录 [小爱技能开放平台](https://developers.xiaoai.mi.com), 创建**自定义技能**;
2. 配置意图(如: 搜片/换台/换剧集), 槽位为识别文本 `query`, 服务地址填你的技能服务端;
3. 技能服务端收到请求后, 把用户说的原句转发到 TVBox:

```python
# Flask 示例: 小爱自定义技能服务端
from flask import Flask, request, jsonify
import requests

app = Flask(__name__)
TVBOX = "http://192.168.1.100:9978/voice"   # 电视盒子的局域网地址

@app.route("/xiaoai", methods=["POST"])
def xiaoai():
    req = request.get_json(force=True)
    query = req.get("request", {}).get("query", "")
    if query:
        requests.get(TVBOX, params={"text": query}, timeout=3)
    return jsonify({"version": "1.0", "response": {
        "to_speak": {"type": 0, "text": "好的, 已在电视上执行"},
        "open_mic": False
    }})

app.run(host="0.0.0.0", port=8080)
```

### 天猫精灵

1. 登录 [天猫精灵开放平台(AliGenie)](https://www.aligenie.com), 创建**语音技能**(技能应用平台支持安卓生态应用/屏端设备接入);
2. 配置意图与槽位, 云端服务选择你部署的服务端;
3. 服务端把解析出的指令文本转发到 TVBox(与上面小爱示例同理):

```javascript
// Node.js 示例: AliGenie 云端服务
const express = require('express');
const app = express();
app.use(express.json());

app.post('/aligenie', async (req, res) => {
  const text = req.body.intentName || '';       // 或取槽位里的完整语句
  if (text) {
    await fetch('http://192.168.1.100:9978/voice?text=' + encodeURIComponent(text));
  }
  res.json({ returnCode: 'SUCCESS', returnErrorSolution: '', returnMsg: '' });
});
app.listen(8080);
```

### 小度智能音箱

1. 登录 [DuerOS 开放平台](https://dueros.baidu.com), 创建**自定义技能**;
2. 配置意图(搜索/换台/换剧集), 会话服务地址填你的技能服务端;
3. 服务端取出 `request.content.query`(用户原话)后转发到 TVBox, 处理逻辑与小爱/天猫精灵一致。

> 三家平台均需实名/审核, 并按各自规范填写服务端地址与返回报文; 服务端只需做一件事——把语音文本原样转给 `http://<TVBox IP>:9978/voice?text=<文本>`。

## 四、执行逻辑说明

| 指令 | 条件 | 行为 |
| --- | --- | --- |
| 搜索 | 任意界面 | 复用远程搜索链路(SearchReceiver), 打开搜索页 |
| 换台(指定频道) | 直播页在栈内 | 直接切换并播放 |
| 换台(指定频道) | 不在直播页 | 自动拉起直播页, 频道列表加载完成后切换 |
| 换台(上下台) | 需已在直播页 | 上/下一个台 |
| 换剧集/快进快退 | 需已在详情/播放页 | PlayFragment 切换 |
| 暂停/继续 | 直播或点播页 | VideoView 暂停/恢复 |
| 音量/静音 | 任意界面 | AudioManager 调节 |

## 五、网页遥控测试

无需音箱即可联调: 手机浏览器打开 `http://<TVBox IP>:9978/`, 搜索栏下方新增了**语音指令**输入框, 输入 `换台到CCTV5`、`第12集` 等点"发送"即可走与音箱完全相同的链路。

## 六、源码位置

- 解析器: `app/src/main/java/com/github/tvbox/osc/voice/VoiceCommandParser.java`
- 调度中心: `app/src/main/java/com/github/tvbox/osc/voice/VoiceCommandManager.java`
- 指令模型/事件: `voice/VoiceCommand.java`、`event/VoiceEvent.java`
- 直播语音执行: `ui/activity/LivePlayActivity.java`(`onVoiceCommand`)
- 点播语音执行: `ui/fragment/PlayFragment.java`(`onVoiceCommand`)
- HTTP 入口: `server/RemoteServer.java`(`/voice`)、`server/InputRequestProcess.java`(`do=voice`)
- 广播入口: `receiver/VoiceReceiver.java`
