package io.metersphere.plugin.zentao.domain;

import lombok.Data;

import java.util.regex.Pattern;

@Data
public class ZentaoApiUrl {
    private String login;
    private String sessionGet;
    private String bugCreate;
    private String createMetaData;
    private String bugUpdate;
    private String bugList;
    private String bugDelete;
    private String bugGet;
    private String storyGet;
    private String userGet;
    private String buildsGet;
    private String buildsGetV17;
    private String fileUpload;
    private String fileDelete;
    private String fileDownload;
    private String replaceImgUrl;
    private String productGet;
    private Pattern imgPattern;
    private String productPlanUrl;
}
