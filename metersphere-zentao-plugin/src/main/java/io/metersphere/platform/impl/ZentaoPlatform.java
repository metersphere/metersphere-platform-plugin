package io.metersphere.platform.impl;

import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.platform.api.AbstractPlatform;
import io.metersphere.platform.client.ZentaoClient;
import io.metersphere.platform.client.ZentaoFactory;
import io.metersphere.platform.client.ZentaoGetClient;
import io.metersphere.platform.constants.AttachmentSyncType;
import io.metersphere.platform.domain.*;
import io.metersphere.platform.utils.DateUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ZentaoPlatform extends AbstractPlatform {

    protected final ZentaoClient zentaoClient;

    protected final String[] imgArray = {
            "bmp", "jpg", "png", "tif", "gif", "jpeg"
    };

    protected Map<String, String> buildMap;

    public ZentaoPlatform(PlatformRequest request) {
        super.key = ZentaoPlatformMetaInfo.KEY;
        super.request = request;
        ZentaoConfig zentaoConfig = getIntegrationConfig(ZentaoConfig.class);
        this.zentaoClient = ZentaoFactory.getInstance(zentaoConfig.getUrl(), zentaoConfig.getRequest());
        zentaoClient.setConfig(zentaoConfig);
    }

    public ZentaoProjectConfig getProjectConfig(String configStr) {
        if (StringUtils.isBlank(configStr)) {
            MSPluginException.throwException("请在项目中添加项目配置！");
        }
        ZentaoProjectConfig projectConfig = JSON.parseObject(configStr, ZentaoProjectConfig.class);
        return projectConfig;
    }

    @Override
    public IssuesWithBLOBs addIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());

        MultiValueMap<String, Object> param = buildUpdateParam(request);
        AddIssueResponse.Issue issue = zentaoClient.addIssue(param);
        request.setPlatformStatus(issue.getStatus());

        String id = issue.getId();
        if (StringUtils.isNotBlank(id)) {
            request.setPlatformId(id);
            request.setId(UUID.randomUUID().toString());
        } else {
            MSPluginException.throwException("请确认该Zentao账号是否开启超级model调用接口权限");
        }
        return request;
    }

    @Override
    public IssuesWithBLOBs updateIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());
        MultiValueMap<String, Object> param = buildUpdateParam(request);
        if (request.getTransitions() != null) {
            request.setPlatformStatus(request.getTransitions().getValue());
        }
        this.handleZentaoBugStatus(param);
        zentaoClient.updateIssue(request.getPlatformId(), param);
        return request;
    }

    /**
     * 更新缺陷数据
     *
     * @param issue 待更新缺陷数据
     * @param bug   平台缺陷数据
     * @return
     */
    public IssuesWithBLOBs getUpdateIssues(PlatformIssuesDTO issue, Map bug) {

        GetIssueResponse.Issue bugObj = JSON.parseObject(JSON.toJSONString(bug), GetIssueResponse.Issue.class);
        String description = bugObj.getSteps();
        String steps = description;
        try {
            steps = htmlDesc2MsDesc(zentao2MsDescription(description));
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        if (issue == null) {
            issue = new PlatformIssuesDTO();
            if (StringUtils.isNotBlank(defaultCustomFields)) {
                issue.setCustomFieldList(JSON.parseArray(defaultCustomFields, PlatformCustomFieldItemDTO.class));
            } else {
                issue.setCustomFieldList(new ArrayList<>());
            }
        } else {
            mergeCustomField(issue, defaultCustomFields);
        }
        issue.setPlatformStatus(bugObj.getStatus());
        if (StringUtils.equals(bugObj.getDeleted(), "1")) {
            issue.setPlatformStatus("DELETE");
        }
        issue.setTitle(bugObj.getTitle());
        issue.setDescription(steps);
        issue.setReporter(bugObj.getOpenedBy());
        issue.setPlatform(key);
        try {
            String openedDate = bug.get("openedDate").toString();
            String lastEditedDate = bug.get("lastEditedDate").toString();
            if (StringUtils.isNotBlank(openedDate) && !openedDate.startsWith("0000-00-00"))
                issue.setCreateTime(DateUtils.getTime(openedDate).getTime());
            if (StringUtils.isNotBlank(lastEditedDate) && !lastEditedDate.startsWith("0000-00-00"))
                issue.setUpdateTime(DateUtils.getTime(lastEditedDate).getTime());
        } catch (Exception e) {
            LogUtil.error("update zentao time" + e.getMessage());
        }
        if (issue.getUpdateTime() == null) {
            issue.setUpdateTime(System.currentTimeMillis());
        }
        List<PlatformCustomFieldItemDTO> customFieldList = syncIssueCustomFieldList(issue.getCustomFieldList(), bug);
        handleSpecialField(customFieldList);
        issue.setCustomFields(JSON.toJSONString(customFieldList));
        return issue;
    }

    private void handleSpecialField(List<PlatformCustomFieldItemDTO> customFieldList) {
        for (PlatformCustomFieldItemDTO item : customFieldList) {
            if (StringUtils.equals(item.getId(), "openedBuild") && StringUtils.isNotBlank(item.getValue().toString())) {
                String[] split = item.getValue().toString().split(",");
                if (buildMap != null) {
                    for (int i = 0; i < split.length; i++) {
                        String s = split[i];
                        if (StringUtils.isNotBlank(buildMap.get(s))) {
                            split[i] = buildMap.get(s);
                        }
                    }
                }
                ArrayList<String> arrayList = new ArrayList<>(Arrays.asList(split));
                item.setValue(arrayList);
                break;
            }
        }
    }

    @Override
    public List<SelectOption> getFormOptions(GetOptionRequest request)  {
        return getFormOptions(this, request);
    }

    @Override
    public void deleteIssue(String platformId) {
        zentaoClient.deleteIssue(platformId);
    }

    @Override
    public void validateIntegrationConfig() {
        zentaoClient.login();
    }

    @Override
    public void validateProjectConfig(String projectConfig) {
        zentaoClient.checkProjectExist(getProjectConfig(projectConfig).getZentaoId());
    }

    public ZentaoConfig setUserConfig(String userPlatformInfo) {
        ZentaoConfig zentaoConfig = getIntegrationConfig(ZentaoConfig.class);
        ZentaoPlatformUserInfo userInfo = getZentaoPlatformUserInfo(userPlatformInfo);
        if (StringUtils.isNotBlank(userInfo.getZentaoUserName())
                && StringUtils.isNotBlank(userInfo.getZentaoPassword())) {
            zentaoConfig.setAccount(userInfo.getZentaoUserName());
            zentaoConfig.setPassword(userInfo.getZentaoPassword());
        }
        zentaoClient.setConfig(zentaoConfig);
        return zentaoConfig;
    }

    private ZentaoPlatformUserInfo getZentaoPlatformUserInfo(String userPlatformInfo) {
        return StringUtils.isBlank(userPlatformInfo) ? new ZentaoPlatformUserInfo()
                : JSON.parseObject(userPlatformInfo, ZentaoPlatformUserInfo.class);
    }

    @Override
    public void validateUserConfig(String userConfig) {
        ZentaoPlatformUserInfo userInfo = getZentaoPlatformUserInfo(userConfig);
        if (StringUtils.isBlank(userInfo.getZentaoUserName())
                || StringUtils.isBlank(userInfo.getZentaoPassword())) {
            MSPluginException.throwException("请填写账号信息");
        }
        setUserConfig(userConfig);
        zentaoClient.login();
    }

    @Override
    public boolean isAttachmentUploadSupport() {
        return true;
    }

//    public IssuesDao getZentaoAssignedAndBuilds(IssuesDao issue) {
//        Map zentaoIssue = (Map) zentaoClient.getBugById(issue.getPlatformId());
//        String assignedTo = zentaoIssue.get("assignedTo").toString();
//        String openedBuild = zentaoIssue.get("openedBuild").toString();
//        List<String> zentaoBuilds = new ArrayList<>();
//        if (Strings.isNotBlank(openedBuild)) {
//            zentaoBuilds = Arrays.asList(openedBuild.split(","));
//        }
//        issue.setZentaoAssigned(assignedTo);
//        issue.setZentaoBuilds(zentaoBuilds);
//        return issue;
//    }

    /**
     * 反射调用，勿删
     * @param request
     * @return
     */
    public List<SelectOption> getBuilds(GetOptionRequest request) {
        ZentaoProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        Map<String, Object> builds = null;
        try {
            builds = zentaoClient.getBuildsByCreateMetaData(projectConfig.getZentaoId());
            if (builds == null || builds.isEmpty()) {
                builds = zentaoClient.getBuilds(projectConfig.getZentaoId());
            }
        } catch (Exception e) {
            builds = zentaoClient.getBuildsV17(projectConfig.getZentaoId());
        }

        List<SelectOption> res = new ArrayList<>();
        if (builds != null) {
            builds.forEach((k, v) -> {
                if (StringUtils.isNotBlank(k)) {
                    res.add(new SelectOption(v.toString(), k));
                }
            });
        }
        return res;
    }

    /**
     * 反射调用，勿删
     * @param request
     * @return
     */
    public List<SelectOption> getUsers(GetOptionRequest request) {
        Map<String, Object> obj = zentaoClient.getUsers();

        LogUtil.info("zentao user " + obj);

        List data = JSON.parseArray(obj.get("data").toString());

        List<SelectOption> users = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Map o = (Map) data.get(i);
            users.add(new SelectOption(o.get("realname").toString(), o.get("account").toString()));
        }
        return users;
    }

    @Override
    public SyncIssuesResult syncIssues(SyncIssuesRequest request) {
        List<PlatformIssuesDTO> issues = request.getIssues();
        SyncIssuesResult syncIssuesResult = new SyncIssuesResult();
        this.defaultCustomFields = request.getDefaultCustomFields();
        issues.forEach(item -> {
            Map bug = zentaoClient.getBugById(item.getPlatformId());
            getUpdateIssues(item, bug);
            syncIssuesResult.getUpdateIssues().add(item);
            syncZentaoIssueAttachments(syncIssuesResult, item);
        });
        return syncIssuesResult;
    }

    @Override
    public void syncAllIssues(SyncAllIssuesRequest syncRequest) {
        int pageNum = 1;
        int pageSize = 200;
        List<Map> zentaoIssues;
        int currentSize;

        ZentaoProjectConfig projectConfig = getProjectConfig(syncRequest.getProjectConfig());
        this.defaultCustomFields = syncRequest.getDefaultCustomFields();

        setBuildOptions(syncRequest);

        try {
            do {
                SyncAllIssuesResult syncIssuesResult = new SyncAllIssuesResult();

                // 获取禅道平台缺陷
                Map response = zentaoClient.getBugsByProjectId(projectConfig.getZentaoId(), pageNum, pageSize);
                zentaoIssues = (List) response.get("bugs");
                currentSize = zentaoIssues.size();

                List<String> allIds = zentaoIssues.stream().map(i -> i.get("id").toString()).collect(Collectors.toList());
                syncIssuesResult.setAllIds(allIds);

                if (syncRequest != null) {
                    zentaoIssues = filterSyncZentaoIssuesByCreated(zentaoIssues, syncRequest);
                }

                if (CollectionUtils.isNotEmpty(zentaoIssues)) {
                    for (Map zentaoIssue : zentaoIssues) {
                        String platformId = (String) zentaoIssue.get("id");
                        IssuesWithBLOBs issue = getUpdateIssues(null, zentaoIssue);

                        // 设置临时UUID，同步附件时需要用
                        issue.setId(UUID.randomUUID().toString());

                        issue.setPlatformId(platformId);
                        syncIssuesResult.getUpdateIssues().add(issue);

                        //同步第三方平台系统附件字段
                        syncZentaoIssueAttachments(syncIssuesResult, issue);
                    }
                }

                pageNum++;

                HashMap<Object, Object> syncParam = buildSyncAllParam(syncIssuesResult);
                syncRequest.getHandleSyncFunc().accept(syncParam);

                if (pageNum > (Integer)((Map)response.get("pager")).get("pageTotal")) {
                    // 禅道接口有点恶心，pageNum 超过了总页数，还是会返回最后一页的数据，当缺陷总数是pageSize的时候会死循环
                    break;
                }
            } while (currentSize >= pageSize);
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e);
        }
    }

    private void setBuildOptions(SyncAllIssuesRequest syncRequest) {
        try {
            GetOptionRequest request = new GetOptionRequest();
            request.setProjectConfig(syncRequest.getProjectConfig());
            this.buildMap = getBuilds(request).stream().collect(Collectors.toMap(SelectOption::getText, SelectOption::getValue));
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    public List<Map> filterSyncZentaoIssuesByCreated(List<Map> zentaoIssues, SyncAllIssuesRequest syncRequest) {
        List<Map> filterIssues = zentaoIssues.stream().filter(item -> {
            long createTimeMills = 0;
            try {
                createTimeMills = DateUtils.getTime((String) item.get("openedDate")).getTime();
                if (syncRequest.isPre()) {
                    return createTimeMills <= syncRequest.getCreateTime().longValue();
                } else {
                    return createTimeMills >= syncRequest.getCreateTime().longValue();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }).collect(Collectors.toList());
        return filterIssues;
    }

    @Override
    public List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfig) {
        return null;
    }

    @Override
    public void syncIssuesAttachment(SyncIssuesAttachmentRequest request) {
        String syncType = request.getSyncType();
        File file = request.getFile();
        String platformId = request.getPlatformId();
        if (StringUtils.equals(AttachmentSyncType.UPLOAD.syncOperateType(), syncType)) {
            // 上传附件
            zentaoClient.uploadAttachment("bug", platformId, file);
        } else if (StringUtils.equals(AttachmentSyncType.DELETE.syncOperateType(), syncType)) {
            Map bugInfo = zentaoClient.getBugById(platformId);
            Map<String, Object> zenFiles = (Map) bugInfo.get("files");
            for (String fileId : zenFiles.keySet()) {
                Map fileInfo = (Map) zenFiles.get(fileId);
                if (file.getName().equals(fileInfo.get("title"))) {
                    zentaoClient.deleteAttachment(fileId);
                    break;
                }
            }
        }
    }

    @Override
    public List<PlatformStatusDTO> getStatusList(String projectConfig) {
        List<PlatformStatusDTO> platformStatusDTOS = new ArrayList<>();
        for (ZentaoIssuePlatformStatus status : ZentaoIssuePlatformStatus.values()) {
            PlatformStatusDTO platformStatusDTO = new PlatformStatusDTO();
            platformStatusDTO.setValue(status.name());
            platformStatusDTO.setLabel(status.getName());

            platformStatusDTOS.add(platformStatusDTO);
        }
        return platformStatusDTOS;
    }

    @Override
    public List<DemandDTO> getDemands(String projectConfigStr) {
        List<DemandDTO> list = new ArrayList<>();
        try {
            ZentaoProjectConfig projectConfig = getProjectConfig(projectConfigStr);

            Map<String, Object> obj = zentaoClient.getDemands(projectConfig.getZentaoId());

            if (obj != null) {
                String data = obj.get("data").toString();
                if (StringUtils.isBlank(data)) {
                    return list;
                }
                // 兼容处理11.5版本格式 [{obj},{obj}]
                if (data.charAt(0) == '[') {
                    List array = JSON.parseArray(data);
                    for (int i = 0; i < array.size(); i++) {
                        Map o = (Map) array.get(i);
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(o.get("id").toString());
                        demandDTO.setName(o.get("title").toString());
                        demandDTO.setPlatform(key);
                        list.add(demandDTO);
                    }
                }
                // {"5": {"children": {"51": {}}}, "6": {}}
                else if (data.startsWith("{\"")) {
                    Map<String, Map<String, String>> dataMap = JSON.parseMap(data);
                    Collection<Map<String, String>> values = dataMap.values();
                    values.forEach(v -> {
                        Map jsonObject = JSON.parseMap(JSON.toJSONString(v));
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(jsonObject.get("id").toString());
                        demandDTO.setName(jsonObject.get("title").toString());
                        demandDTO.setPlatform(key);
                        list.add(demandDTO);
                        if (jsonObject.get("children") != null) {
                            LinkedHashMap<String, Map<String, String>> children = (LinkedHashMap<String, Map<String, String>>) jsonObject.get("children");
                            Collection<Map<String, String>> childrenMap = children.values();
                            childrenMap.forEach(ch -> {
                                DemandDTO dto = new DemandDTO();
                                dto.setId(ch.get("id"));
                                dto.setName(ch.get("title"));
                                dto.setPlatform(key);
                                list.add(dto);
                            });
                        }
                    });
                }
                // 处理格式 {{"id": {obj}},{"id",{obj}}}
                else if (data.charAt(0) == '{') {
                    Map dataObject = (Map) obj.get("data");
                    String s = JSON.toJSONString(dataObject);
                    Map<String, Object> map = JSON.parseMap(s);
                    Collection<Object> values = map.values();
                    values.forEach(v -> {
                        Map jsonObject = JSON.parseMap(JSON.toJSONString(v));
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(jsonObject.get("id").toString());
                        demandDTO.setName(jsonObject.get("title").toString());
                        demandDTO.setPlatform(key);
                        list.add(demandDTO);
                    });
                }
            }
        } catch (Exception e) {
            LogUtil.error("get zentao demand fail: ", e);
        }
        return list;
    }

    private String ms2ZentaoDescription(String msDescription) {
        String imgUrlRegex = "!\\[.*?]\\(/resource/md/get(.*?\\..*?)\\)";
        String zentaoSteps = msDescription.replaceAll(imgUrlRegex, zentaoClient.requestUrl.getReplaceImgUrl());
        Matcher matcher = zentaoClient.requestUrl.getImgPattern().matcher(zentaoSteps);
        while (matcher.find()) {
            // get file name
            String originSubUrl = matcher.group(1);
            if (originSubUrl.contains("/url?url=") || originSubUrl.contains("/path?")) {
                String path = URLDecoder.decode(originSubUrl, StandardCharsets.UTF_8);
                String fileName;
                if (path.indexOf("fileID") > 0) {
                    fileName = path.substring(path.indexOf("fileID") + 7);
                } else {
                    fileName = path.substring(path.indexOf("file-read-") + 10);
                }
                zentaoSteps = zentaoSteps.replaceAll(Pattern.quote(originSubUrl), fileName);
            } else {
                String fileName = originSubUrl.substring(10);
                // upload zentao
                String id = zentaoClient.uploadFile(new File(MD_IMAGE_DIR + "/" + fileName));
                // todo delete local file
                int index = fileName.lastIndexOf(".");
                String suffix = "";
                if (index != -1) {
                    suffix = fileName.substring(index);
                }
                // replace id
                zentaoSteps = zentaoSteps.replaceAll(Pattern.quote(originSubUrl), id + suffix);
            }
        }
        // image link
        String netImgRegex = "!\\[(.*?)]\\((http.*?)\\)";
        return zentaoSteps.replaceAll(netImgRegex, "<img src=\"$2\" alt=\"$1\"/>");
    }

    private String zentao2MsDescription(String ztDescription) {
        String imgRegex = "<img src.*?/>";
        Pattern pattern = Pattern.compile(imgRegex);
        Matcher matcher = pattern.matcher(ztDescription);
        while (matcher.find()) {
            if (StringUtils.isNotEmpty(matcher.group())) {
                // img标签内容
                String imgPath = matcher.group();
                // 解析标签内容为图片超链接格式，进行替换，
                String src = getMatcherResultForImg("src\\s*=\\s*\"?(.*?)(\"|>|\\s+)", imgPath);
                String alt = getMatcherResultForImg("alt\\s*=\\s*\"?(.*?)(\"|>|\\s+)", imgPath);
                String hyperLinkPath = packageDescriptionByPathAndName(src, alt);
                imgPath = transferSpecialCharacter(imgPath);
                ztDescription = ztDescription.replaceAll(imgPath, hyperLinkPath);
            }
        }

        return ztDescription;
    }

    /**
     * 转译字符串中的特殊字符
     *
     * @param str
     * @return
     */
    protected String transferSpecialCharacter(String str) {
        String regEx = "[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            CharSequence cs = str;
            int j = 0;
            for (int i = 0; i < cs.length(); i++) {
                String temp = String.valueOf(cs.charAt(i));
                Matcher m2 = pattern.matcher(temp);
                if (m2.find()) {
                    StringBuilder sb = new StringBuilder(str);
                    str = sb.insert(j, "\\").toString();
                    j++;
                }
                j++; //转义完成后str的长度增1
            }
        }
        return str;
    }

    private String packageDescriptionByPathAndName(String path, String name) {
        String result = "";

        if (StringUtils.isNotEmpty(path)) {
            if (!path.startsWith("http")) {
                if (path.startsWith("{") && path.endsWith("}")) {
                    String srcContent = path.substring(1, path.length() - 1);
                    if (StringUtils.isEmpty(name)) {
                        name = srcContent;
                    }

                    if (Arrays.stream(imgArray).anyMatch(imgType -> StringUtils.equals(imgType, srcContent.substring(srcContent.indexOf('.') + 1)))) {
                        if (zentaoClient instanceof ZentaoGetClient) {
                            path = "/index.php?m=file&f=read&fileID=" + srcContent;
                        } else {
                            // 禅道开源版
                            path = "/file-read-" + srcContent;
                        }
                    } else {
                        return result;
                    }
                } else {
                    name = name.replaceAll("&amp;", "&");
                    path = path.replaceAll("&amp;", "&");
                    if (path.contains("/")) {
                        String[] split = path.split("/");
                        path = "/" + split[split.length - 1];
                    }
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (String item : path.split("&")) {
                    // 去掉多余的参数
                    if (!StringUtils.containsAny(item, "platform", "workspaceId")) {
                        stringBuilder.append(item);
                        stringBuilder.append("&");
                    }
                }
                path = getProxyPath(stringBuilder.toString());
            }
            // 图片与描述信息之间需换行，否则无法预览图片
            result = "\n\n![" + name + "](" + path + ")";
        }

        return result;
    }

    private String getMatcherResultForImg(String regex, String targetStr) {
        String result = "";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(targetStr);
        while (matcher.find()) {
            result = matcher.group(1);
        }

        return result;
    }

    @Override
    public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
        zentaoClient.getAttachmentBytes(fileKey, inputStreamHandler);
    }

    public void syncZentaoIssueAttachments(SyncIssuesResult syncIssuesResult, IssuesWithBLOBs issue) {
        Map bugInfo = zentaoClient.getBugById(issue.getPlatformId());
        Object files = bugInfo.get("files");
        Map<String, Object> zenFiles;
        if (files instanceof List && ((List) files).size() == 0) {
            zenFiles = null;
        } else {
            zenFiles = (Map) files;
        }
        if (zenFiles != null) {
            Map<String, List<PlatformAttachment>> attachmentMap = syncIssuesResult.getAttachmentMap();
            attachmentMap.put(issue.getId(), new ArrayList<>());
            for (String fileId : zenFiles.keySet()) {
                Map fileInfo = (Map) zenFiles.get(fileId);
                String filename = fileInfo.get("title").toString();
                try {
                    PlatformAttachment syncAttachment = new PlatformAttachment();
                    // name 用于查重
                    syncAttachment.setFileName(filename);
                    // key 用于获取附件内容
                    syncAttachment.setFileKey(fileId);
                    attachmentMap.get(issue.getId()).add(syncAttachment);
                } catch (Exception e) {
                    LogUtil.error(e);
                }

            }
        }
    }

    private MultiValueMap<String, Object> buildUpdateParam(PlatformIssuesUpdateRequest issuesRequest) {
        issuesRequest.setPlatform(key);
        ZentaoProjectConfig projectConfig = getProjectConfig(issuesRequest.getProjectConfig());
        String projectId = projectConfig.getZentaoId();
        if (StringUtils.isBlank(projectId)) {
            MSPluginException.throwException("未关联禅道项目ID.");
        }
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("product", projectId);
        paramMap.add("title", issuesRequest.getTitle());
        if (issuesRequest.getTransitions() != null) {
            paramMap.add("status", issuesRequest.getTransitions().getValue());
        }

        addCustomFields(issuesRequest, paramMap);

        String description = issuesRequest.getDescription();
        String zentaoSteps = description;

        // transfer description
        try {
            zentaoSteps = ms2ZentaoDescription(description);
            zentaoSteps = zentaoSteps.replaceAll("\\n", "<br/>");
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        LogUtil.info("zentao description transfer: " + zentaoSteps);

        paramMap.add("steps", zentaoSteps);
        handleBuildParam(paramMap);
        return paramMap;
    }

    private void handleBuildParam(MultiValueMap<String, Object> paramMap) {
        try {
            List<Object> buildValue = paramMap.get("openedBuild");
            paramMap.remove("openedBuild");
            if (CollectionUtils.isNotEmpty(buildValue)) {
                List<String> builds= JSON.parseArray(buildValue.get(0).toString(), String.class);
                if (CollectionUtils.isNotEmpty(builds)) {
                    builds.forEach(build -> paramMap.add("openedBuild[]", build));
                } else {
                    paramMap.add("openedBuild", "trunk");
                }
            } else {
                paramMap.add("openedBuild", "trunk");
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    private void handleZentaoBugStatus(MultiValueMap<String, Object> param) {
        if (!param.containsKey("status")) {
            return;
        }
        List<Object> status = param.get("status");
        if (CollectionUtils.isEmpty(status)) {
            return;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String str = (String) status.get(0);
            if (StringUtils.equals(str, "resolved")) {
                param.add("resolvedDate", format.format(new Date()));
            } else if (StringUtils.equals(str, "closed")) {
                param.add("closedDate", format.format(new Date()));
                if (!param.containsKey("resolution")) {
                    // 解决方案默认为已解决
                    param.add("resolution", "fixed");
                }
            }
        } catch (Exception e) {
            //
        }
    }

    @Override
    public ResponseEntity proxyForGet(String path, Class responseEntityClazz) {
        return zentaoClient.proxyForGet(path, responseEntityClazz);
    }
}
