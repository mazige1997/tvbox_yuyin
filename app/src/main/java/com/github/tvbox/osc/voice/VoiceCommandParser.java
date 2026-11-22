package com.github.tvbox.osc.voice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文语音指令解析器。
 * 把小爱同学/天猫精灵/小度等语音助手识别出的文本解析为结构化的 {@link VoiceCommand}。
 * 解析不到专属指令时, 默认按"搜索"处理(与远程搜索的交互习惯一致, 直接说片名即可搜索)。
 * 本类为纯Java实现, 不依赖Android运行环境。
 *
 * @author voice
 */
public class VoiceCommandParser {

    // ---- 播放控制(整句全匹配) ----
    private static final Pattern P_PAUSE = Pattern.compile("^(暂停|暂停播放|暂停一下|暂停片刻|别播了|停止播放|停止|关闭播放)$");
    private static final Pattern P_RESUME = Pattern.compile("^(继续|继续播放|接着播|接着播放|恢复播放|播放)$");
    private static final Pattern P_SEEK = Pattern.compile("^(快进|快退|倒退|后退|前进)([0-9]+|[零一二三四五六七八九十百两]+)?(个)?(秒|分钟|分|小时|时)?(钟)?(一下|一会儿)?(可以吗|行吗)?$");
    private static final Pattern P_VOLUME_UP = Pattern.compile("^(音量(调|加|增|升)?(大|高)点?|大声点|声音(大|高)点|调大音量|加大音量)$");
    private static final Pattern P_VOLUME_DOWN = Pattern.compile("^(音量(调|减|降|缩)?(小|低)点?|小声点|声音(小|低)点|调小音量|减小音量)$");
    private static final Pattern P_MUTE = Pattern.compile("^(静音|取消静音|闭嘴|静音切换)$");

    // ---- 直播换台 ----
    private static final Pattern P_LIVE_PREV = Pattern.compile("^(上一个台|上个台|上一台|上个频道|上一频道|后退一个台|换回上一个台)$");
    private static final Pattern P_LIVE_NEXT = Pattern.compile("^(下一个台|下个台|下一台|下个频道|下一频道|换台|切台|换个台|切下一个台)$");
    private static final Pattern P_LIVE_NUMBER = Pattern.compile("^第([0-9]+)台$");
    private static final Pattern P_DIGITS = Pattern.compile("^[0-9]+$");

    // ---- 剧集 ----
    private static final Pattern P_EPISODE_NEXT = Pattern.compile("^(下一个?集|下个集|下一话|下个话|下一期|下个期|跳集)$");
    private static final Pattern P_EPISODE_PREV = Pattern.compile("^(上一个?集|上个集|上一话|上个话|上一期|上个期|跳回上集)$");
    private static final Pattern P_EPISODE_GOTO = Pattern.compile("^(我想看|我要看|我想观看|我要观看|播放|看|切到|切换到|跳到|跳转|跳转到|转到|换到|换台到|换集到|快进到|跳集到)?第([0-9]+|[零一二三四五六七八九十百两]+)(集|话|期)$");

    // ---- 换台动词: 显式指定直播的(即使后面不是标准频道名也按频道名处理) ----
    private static final String[] EXPLICIT_LIVE_VERBS = {
            "换台到", "换频道到", "切换频道到", "换个台到", "切换频道", "换频道",
            "换台", "切台", "换个台", "换个频道"
    };
    // ---- 泛化切换动词: 后面像频道名才走直播, 否则留给搜索 ----
    private static final String[] GENERIC_SWITCH_VERBS = {
            "切换到", "切换", "换到", "切到", "转到", "调到", "跳转到", "跳转", "跳到"
    };
    // ---- 搜索动词(长词优先) ----
    private static final String[] SEARCH_VERBS = {
            "帮我搜索一下", "帮我搜索", "帮我搜一下", "帮我搜", "帮我查找", "帮我找",
            "搜索一下", "我要搜索", "我要搜", "我要找", "查找一下", "搜寻", "搜一下",
            "搜搜", "查找", "搜索", "找一下", "搜", "找"
    };
    // ---- 观看动词(长词优先): 后面像频道名→直播换台, 否则→搜索 ----
    private static final String[] PLAY_VERBS = {
            "我想观看", "我想播放", "我想看", "我想听", "我要观看", "我要播放", "我要看",
            "我要听", "想观看", "想播放", "想看", "想听", "观看", "播放", "来一部",
            "来一个", "来一集", "看"
    };

    /**
     * 解析语音文本为指令。解析不到专属指令时返回TYPE_SEARCH(以原始文本为关键词)。
     */
    public static VoiceCommand parse(String raw) {
        VoiceCommand cmd = new VoiceCommand();
        cmd.rawText = raw == null ? "" : raw.trim();
        String text = normalize(cmd.rawText);
        if (text.isEmpty()) {
            cmd.type = VoiceCommand.TYPE_UNKNOWN;
            return cmd;
        }

        VoiceCommand parsed;
        // 1. 播放控制
        if ((parsed = parsePlayControl(text)) != null) return finish(parsed, cmd);
        // 2. 直播上下台
        if (P_LIVE_PREV.matcher(text).matches()) return finish(new VoiceCommand(VoiceCommand.TYPE_LIVE_PREV), cmd);
        if (P_LIVE_NEXT.matcher(text).matches()) return finish(new VoiceCommand(VoiceCommand.TYPE_LIVE_NEXT), cmd);
        // 3. 剧集跳集(先于换台/搜索, 避免被"切换到第3集"截走)
        if ((parsed = parseEpisode(text)) != null) return finish(parsed, cmd);
        // 4. 第N台
        if ((parsed = parseLiveNumber(text)) != null) return finish(parsed, cmd);
        // 5. 显式换台动词: "换台到CCTV5" / "换频道湖南卫视" / "换台"
        if ((parsed = parseExplicitLive(text)) != null) return finish(parsed, cmd);
        // 6. 泛化切换动词 + 频道名: "切换到CCTV5"
        if ((parsed = parseGenericSwitch(text)) != null) return finish(parsed, cmd);
        // 7. 搜索动词: "搜索狂飙"
        String keyword = stripAnyPrefix(text, SEARCH_VERBS);
        if (keyword != null) {
            return finish(buildSearchOrLive(keyword), cmd);
        }
        // 8. 观看动词: "看CCTV5"→换台; "看狂飙"→搜索
        keyword = stripAnyPrefix(text, PLAY_VERBS);
        if (keyword != null) {
            return finish(buildSearchOrLive(keyword), cmd);
        }
        // 9. 直接说频道名: "CCTV5" / "湖南卫视"
        if (looksLikeChannelName(text)) {
            return finish(buildLiveByName(text), cmd);
        }
        // 10. 兜底: 整句当搜索词(与远程搜索习惯一致), 去掉残留的切换动词("换到狂飙"→"狂飙")
        String bareKeyword = stripAnyPrefix(text, GENERIC_SWITCH_VERBS);
        if (bareKeyword == null || bareKeyword.isEmpty()) bareKeyword = text;
        return finish(buildSearchOrLive(bareKeyword), cmd);
    }

    private static VoiceCommand finish(VoiceCommand parsed, VoiceCommand target) {
        target.type = parsed.type;
        target.keyword = parsed.keyword;
        target.number = parsed.number;
        return target;
    }

    private static VoiceCommand buildSearchOrLive(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return new VoiceCommand(VoiceCommand.TYPE_UNKNOWN);
        }
        // 观看动词+频道名: "看CCTV5" → 语音换台
        if (looksLikeChannelName(keyword)) {
            return buildLiveByName(keyword);
        }
        VoiceCommand search = new VoiceCommand(VoiceCommand.TYPE_SEARCH);
        search.keyword = keyword;
        return search;
    }

    private static VoiceCommand buildLiveByName(String keyword) {
        VoiceCommand live = new VoiceCommand(VoiceCommand.TYPE_LIVE_CHANNEL_BY_NAME);
        live.keyword = normalizeChannelKeyword(keyword);
        return live;
    }

    private static VoiceCommand parsePlayControl(String text) {
        if (P_PAUSE.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_PLAY_PAUSE);
        if (P_RESUME.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_PLAY_RESUME);
        Matcher m = matches(P_SEEK, text);
        if (m != null) {
            boolean forward = !text.startsWith("快退") && !text.startsWith("倒退") && !text.startsWith("后退");
            VoiceCommand cmd = new VoiceCommand(forward ? VoiceCommand.TYPE_SEEK_FORWARD : VoiceCommand.TYPE_SEEK_BACK);
            long seconds = 30;
            if (m.group(2) != null) {
                long num = parseNumberToken(m.group(2));
                if (num > 0) {
                    String unit = m.group(4);
                    if ("分钟".equals(unit) || "分".equals(unit)) num *= 60;
                    else if ("小时".equals(unit) || "时".equals(unit)) num *= 3600;
                    seconds = num;
                }
            }
            cmd.number = (int) Math.min(seconds, Integer.MAX_VALUE);
            return cmd;
        }
        if (P_VOLUME_UP.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_VOLUME_UP);
        if (P_VOLUME_DOWN.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_VOLUME_DOWN);
        if (P_MUTE.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_VOLUME_MUTE);
        return null;
    }

    private static VoiceCommand parseEpisode(String text) {
        if (P_EPISODE_NEXT.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_EPISODE_NEXT);
        if (P_EPISODE_PREV.matcher(text).matches()) return new VoiceCommand(VoiceCommand.TYPE_EPISODE_PREV);
        Matcher m = matches(P_EPISODE_GOTO, text);
        if (m != null) {
            int number = parseNumberToken(m.group(2));
            if (number > 0) {
                VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_EPISODE_GOTO);
                cmd.number = number;
                return cmd;
            }
        }
        return null;
    }

    private static VoiceCommand parseLiveNumber(String text) {
        Matcher m = matches(P_LIVE_NUMBER, text);
        if (m != null) {
            int number = parseNumberToken(m.group(1));
            if (number > 0) {
                VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_LIVE_CHANNEL_BY_NUMBER);
                cmd.number = number;
                return cmd;
            }
        }
        return null;
    }

    private static VoiceCommand parseExplicitLive(String text) {
        String rest = stripAnyPrefix(text, EXPLICIT_LIVE_VERBS);
        if (rest == null) return null;
        return buildLiveTarget(rest);
    }

    private static VoiceCommand parseGenericSwitch(String text) {
        String rest = stripAnyPrefix(text, GENERIC_SWITCH_VERBS);
        if (rest == null || !looksLikeChannelName(rest)) return null;
        return buildLiveTarget(rest);
    }

    /** 显式换台动词后的目标: 空→下一台; 第N台/数字→按台号; 其余→按频道名 */
    private static VoiceCommand buildLiveTarget(String rest) {
        if (rest.isEmpty()) return new VoiceCommand(VoiceCommand.TYPE_LIVE_NEXT);
        // 先把中文数字转成阿拉伯数字, "第三台"→"第3台"
        rest = convertChineseNumbers(rest);
        Matcher m = matches(P_LIVE_NUMBER, rest);
        if (m != null || P_DIGITS.matcher(rest).matches()) {
            int number = m != null ? parseNumberToken(m.group(1)) : parseNumberToken(rest);
            if (number > 0) {
                VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_LIVE_CHANNEL_BY_NUMBER);
                cmd.number = number;
                return cmd;
            }
            return new VoiceCommand(VoiceCommand.TYPE_LIVE_NEXT);
        }
        return buildLiveByName(rest);
    }

    // ==================== 工具方法 ====================

    /** 整句全匹配正则, 匹配返回Matcher否则null */
    private static Matcher matches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.matches() ? matcher : null;
    }

    /** 去掉第一个匹配的前缀动词, 未匹配返回null, 匹配后剩余可能为空串 */
    private static String stripAnyPrefix(String text, String[] verbs) {
        for (String verb : verbs) {
            if (text.startsWith(verb)) {
                return text.substring(verb.length());
            }
        }
        return null;
    }

    /**
     * 文本归一化: 全角转半角、大写、去空白与常见标点。
     */
    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '０' && c <= '９') c = (char) (c - '０' + '0');
            else if (c >= 'Ａ' && c <= 'Ｚ') c = (char) (c - 'Ａ' + 'A');
            else if (c >= 'ａ' && c <= 'ｚ') c = (char) (c - 'ａ' + 'a');
            if (c >= 'a' && c <= 'z') c = (char) (c - 'a' + 'A');
            if (Character.isWhitespace(c)) continue;
            if (isNoiseChar(c)) continue;
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isNoiseChar(char c) {
        return "，。？！、,.!?~～#@$%^&*()（）「」『』《》〈〉|/\\<>".indexOf(c) >= 0
                || "：:；;…—·-_=+[]【】｛｝{}\"'`".indexOf(c) >= 0;
    }

    /**
     * 频道关键词归一化: 统一别名(中央/央视→CCTV)、中文数字转阿拉伯数字、去掉"台/套/频道"后缀。
     * 仅用于频道匹配, 不用于搜索关键词(避免误改片名, 如"八佰")。
     */
    public static String normalizeChannelKeyword(String keyword) {
        String s = normalize(keyword);
        if (s.isEmpty()) return "";
        s = s.replace("中国中央电视台", "CCTV")
                .replace("中央电视台", "CCTV")
                .replace("中国中央", "CCTV")
                .replace("中央", "CCTV")
                .replace("央视", "CCTV");
        s = convertChineseNumbers(s);
        if (s.endsWith("频道")) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("台") || s.endsWith("套")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * 判断是否像频道名: CCTV/CGTN/中央/央视/卫视/电视台/台/频道结尾。
     */
    public static boolean looksLikeChannelName(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.contains("CCTV") || s.contains("CGTN")) return true;
        if (s.startsWith("中央") || s.startsWith("央视")) return true;
        if (s.endsWith("卫视") || s.endsWith("台") || s.endsWith("频道") || s.endsWith("电视台")) return true;
        return s.matches(".*[0-9]+台");
    }

    /**
     * 把字符串中的连续中文数字转成阿拉伯数字: "一台"→"1台"、"十二"→"12"、"一百零五"→"105"。
     */
    public static String convertChineseNumbers(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (isChineseDigit(c)) {
                int j = i;
                while (j < len && isChineseDigit(s.charAt(j))) j++;
                long val = parseChineseNumberRun(s.substring(i, j));
                if (val >= 0) out.append(val);
                else out.append(s, i, j);
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 解析纯中文数字串为数值(支持十/百进位), 非法返回-1 */
    public static long parseChineseNumberRun(String run) {
        if (run == null || run.isEmpty()) return -1;
        long result = 0;
        long current = 0;
        boolean has = false;
        for (int i = 0; i < run.length(); i++) {
            char c = run.charAt(i);
            if (c == '十') {
                result += (current == 0 ? 1 : current) * 10;
                current = 0;
                has = true;
            } else if (c == '百') {
                result += (current == 0 ? 1 : current) * 100;
                current = 0;
                has = true;
            } else {
                int d = charDigitValue(c);
                if (d < 0) return -1;
                current = current * 10 + d;
                has = true;
            }
        }
        return has ? result + current : -1;
    }

    private static int charDigitValue(char c) {
        switch (c) {
            case '零':
                return 0;
            case '一':
                return 1;
            case '二':
            case '两':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }

    private static boolean isChineseDigit(char c) {
        return "零一二三四五六七八九十百两".indexOf(c) >= 0;
    }

    /** 数字token: 支持阿拉伯数字与中文数字, 非法返回-1 */
    private static int parseNumberToken(String token) {
        if (token == null || token.isEmpty()) return -1;
        if (P_DIGITS.matcher(token).matches()) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        long val = parseChineseNumberRun(token);
        if (val < 0 || val > Integer.MAX_VALUE) return -1;
        return (int) val;
    }
}
