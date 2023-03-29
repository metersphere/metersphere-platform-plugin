package io.metersphere;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegularTest {

    /**
     * 获取从 jira 同步后的图片名称 d4cfd42c.png
     * ![d4cfd42c.png](/resource/md/get/path?platform=Jira&workspaceId=xxx&path=xx)
     */
    @Test
    public void getJiraImageFileName() {
        Set<String> fileNames = new HashSet<>();
        // ![d4cfd42c.png](/resource/md/get/path?platform=Jira&workspaceId=xxx&path=xx)
        String input = "![d4cfd42c.png](/resource/md/get/path?platform=Jira&workspaceId=xxx&path=xx)fdsfdsfsd![ccddd.png](/resource/md/get/path?platform=Jira&workspaceId=xxx&path=xx";
        addJiraImageFileName(fileNames, input);
        System.out.println(fileNames);
    }

    /**
     * 获取 Ms 的图片名称 f2aef1f3.png
     * ![Stamp.png](/resource/md/get?fileName=f2aef1f3.png)
     */
    @Test
    public void getMsImageFileName() {
        Set<String> fileNames = new HashSet<>();
        // ![d4cfd42c.png](/resource/md/get/path?platform=Jira&workspaceId=xxx&path=xx)
        String input = "![Stamp.png](/resource/md/get?fileName=f2aef1f3.png)fdsfdsfsd![Stamp.png](/resource/md/get?fileName=aaa.png)";
        addMsImageFileName(fileNames, input);
        System.out.println(fileNames);
    }


    @Test
    public void testParseJiraImgLink2MsImgLink2() {
        String input = "![ddddd]([http://static.runoob.com/images/demo/demo2.jpg|http://static.runoob.com/images/demo/demo2.jpg])\n [GGG]([http://static.runoob.com/images/demo/demo2.jpg|http://static.runoob.com/images/demo/demo2.jpg])\n\n\n\njira\n\n [http://static.runoob.com/images/demo/demo2.jpg|http://static.runoob.com/images/demo/demo2.jpg]\n\n [https://www.json.cn/json/jsononline.html|https://www.json.cn/json/jsononline.html|smart-link] ";
        System.out.println(input);
        System.out.println("================");
        String s = parseJiraLink2MsLink(input);
        System.out.println(s);
        System.out.println("================");
        System.out.println(parseSimpleJiraLink2MsLink(s));
    }

    @Test
    public void testParseMsImgLink2JiraImgLink() {
        String input = "[GGG](http://aa.com) dsfdf [VVV](http://aa.comddd) dd![GG](http://aa.com) ";
        System.out.println(parseMsLink2JiraLink(input));
    }

    /**
     * ms 的链接转成 jira 格式
     * [GGG](http://aa.com) -> [http://aa.com|http://aa.com]
     * @param input
     * @return
     */
    private String parseMsLink2JiraLink(String input) {
        String pattern = "\\!?\\[.*?\\]\\((.*?)\\)";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String group = matcher.group(0);
            String url = matcher.group(1);
            if (url.startsWith("http")) {
                String jiraFormat = "[" + url + "|" + url + "]";
                input = input.replace(group, jiraFormat);
            }
        }
        return input;
    }

    /**
     * 这个格式是 ms 创建后同步到 jira 的
     * [GGG]([http://aa.com|http://aa.com]) -> [GGG](http://aa.com)
     * @param input
     * @return
     */
    private String parseJiraLink2MsLink(String input) {
        String pattern = "(\\(\\[.*?\\]\\))";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String group = matcher.group(1);
            if (StringUtils.isNotEmpty(group) && group.startsWith("([http")) {
                String[] split = group.split("\\|");
                String msFormat = split[0].replaceFirst("\\[", "") + ")";
                input = input.replace(group, msFormat);
            }
        }
        return input;
    }

    /**
     * 这个格式是 jira 的
     * [http://aa.com|http://aa.com] -> [asd](http://aa.com)
     * @param input
     * @return
     */
    private String parseSimpleJiraLink2MsLink(String input) {
        String pattern = "(\\[.*?\\])";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String group = matcher.group(1);
            if (StringUtils.isNotEmpty(group) && group.startsWith("[http")) {
                String[] split = group.split("\\|");
                String msFormat = split[0].replaceFirst("\\[", "");
                msFormat = "[" + UUID.randomUUID().toString().substring(0, 8) + "]" + "(" + msFormat + ")";
                input = input.replace(group, msFormat);
            }
        }
        return input;
    }

    private void addJiraImageFileName(Set<String> fileNames, String input) {
        addFileName(fileNames, "\\!\\[(.*?)\\]\\(/resource/md/get/path", input);
    }

    private void addMsImageFileName(Set<String> fileNames, String input) {
        addFileName(fileNames, "\\!\\[.*?\\]\\(/resource/md/get\\?fileName=(.*?)\\)", input);
    }

    private void addFileName(Set<String> fileNames, String pattern, String input) {
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (StringUtils.isNotEmpty(path)) {
                fileNames.add(matcher.group(1));
            }
        }
    }

}
