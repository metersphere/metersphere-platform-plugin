package io.metersphere.plugin.zentao.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public class UnicodeConvertUtils {

    /**
     * 单个字符的正则表达式
     */
    private static final String SINGLE_PATTERN = "[0-9|a-f|A-F]";
    /**
     * 4个字符的正则表达式
     */
    private static final String PATTERN = SINGLE_PATTERN + SINGLE_PATTERN + SINGLE_PATTERN + SINGLE_PATTERN;

    /**
     * unicode字符最小长度
     */
    public static final int UNICODE_MIN_LENGTH = 6;

    /**
     * unicode字符前缀
     */
    public static final String UNICODE_PREFIX = "\\u";


    /**
     * 把 \\u 开头的单字转成汉字，如 \\u6B65 ->　步
     *
     * @param str 字符
     * @return 中文字符
     */
    private static String strToCn(final String str) {
        int code = Integer.decode("0x" + str.substring(2, 6));
        char c = (char) code;
        return String.valueOf(c);
    }

    /**
     * 字符串是否以Unicode字符开头。约定Unicode字符以 \\u开头。
     *
     * @param str 字符串
     * @return true表示以Unicode字符开头.
     */
    private static boolean isStartWithUnicode(final String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if (!str.startsWith(UNICODE_PREFIX)) {
            return false;
        }
        if (str.length() < UNICODE_MIN_LENGTH) {
            return false;
        }
        String content = str.substring(2, 6);

        return Pattern.matches(PATTERN, content);
    }

    /**
     * 字符串中，所有以 \\u 开头的UNICODE字符串，全部替换成汉字
     *
     * @return 中文字符
     */
    public static String unicodeToCn(final String str) {
        if (StringUtils.isEmpty(str)) {
            return StringUtils.EMPTY;
        }
        // 用于构建新的字符串
        StringBuilder sb = new StringBuilder();
        // 从左向右扫描字符串。tmpStr是还没有被扫描的剩余字符串。
        // 下面有两个判断分支：
        // 1. 如果剩余字符串是Unicode字符开头，就把Unicode转换成汉字，加到StringBuilder中。然后跳过这个Unicode字符。
        // 2.反之， 如果剩余字符串不是Unicode字符开头，把普通字符加入StringBuilder，向右跳过1.
        int length = str.length();
        for (int i = 0; i < length; ) {
            String tmpStr = str.substring(i);
            if (isStartWithUnicode(tmpStr)) {
                // 分支1
                sb.append(strToCn(tmpStr));
                i += 6;
            } else { // 分支2
                sb.append(str.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
