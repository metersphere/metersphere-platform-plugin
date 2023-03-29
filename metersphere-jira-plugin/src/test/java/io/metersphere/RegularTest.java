package io.metersphere;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
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

    private void addJiraImageFileName(Set<String> fileNames, String input) {
        addFileName(fileNames,  "\\!\\[(.*?)\\]\\(/resource/md/get/path", input);
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
