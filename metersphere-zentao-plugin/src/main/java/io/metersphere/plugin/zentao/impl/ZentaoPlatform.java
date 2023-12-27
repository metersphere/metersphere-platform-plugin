package io.metersphere.plugin.zentao.impl;

import io.metersphere.plugin.platform.dto.PlatformAttachment;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.reponse.DemandDTO;
import io.metersphere.plugin.platform.dto.reponse.PlatformBugDTO;
import io.metersphere.plugin.platform.dto.reponse.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.reponse.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.enums.SyncAttachmentType;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.zentao.client.ZentaoClient;
import io.metersphere.plugin.zentao.client.ZentaoFactory;
import io.metersphere.plugin.zentao.domain.ZentaoAddBugResponse;
import io.metersphere.plugin.zentao.domain.ZentaoIntegrationConfig;
import io.metersphere.plugin.zentao.domain.ZentaoProjectConfig;
import io.metersphere.plugin.zentao.enums.ZentaoBugPlatformStatus;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Extension
public class ZentaoPlatform extends AbstractPlatform {

    protected ZentaoClient zentaoClient;

    protected ZentaoProjectConfig projectConfig;

    protected SimpleDateFormat sdfWithZone = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    public ZentaoPlatform(PlatformRequest request) {
        super(request);
        ZentaoIntegrationConfig zentaoConfig = getIntegrationConfig(request.getIntegrationConfig(), ZentaoIntegrationConfig.class);
        zentaoClient = ZentaoFactory.getInstance(zentaoConfig.getAddress(), zentaoConfig.getRequestType());
    }

    /**
     * 校验集成配置
     */
    @Override
    public void validateIntegrationConfig() {
        zentaoClient.auth();
    }

    /**
     * 校验项目配置
     *
     * @param projectConfigStr 项目配置信息
     */
    @Override
    public void validateProjectConfig(String projectConfigStr) {
        try {
            ZentaoProjectConfig projectConfig = getProjectConfig(projectConfigStr);
            zentaoClient.checkProject(projectConfig.getZentaoId());
        } catch (Exception e) {
            throw new MSPluginException(e.getMessage());
        }
    }

    /**
     * 校验需求/缺陷项目KEY
     */
    public void validateProjectKey() {
        if (StringUtils.isBlank(projectConfig.getZentaoId())) {
            throw new MSPluginException("请在项目中配置Zentao项目ID!");
        }
    }

    /**
     * 校验配置
     *
     * @param userPlatformConfig 用户平台配置
     * @param projectConfig      项目配置
     */
    private void validateConfig(String userPlatformConfig, String projectConfig) {
        setUserConfig(userPlatformConfig);
        this.projectConfig = getProjectConfig(projectConfig);
        validateProjectKey();
    }

    /**
     * 设置用户平台配置
     *
     * @param userPlatformConfig 用户平台配置
     */
    public void setUserConfig(String userPlatformConfig) {
        ZentaoIntegrationConfig config = getIntegrationConfig(userPlatformConfig, ZentaoIntegrationConfig.class);
        validateAndSetConfig(config);
    }

    /**
     * 校验并设置集成配置
     *
     * @param config 集成配置
     */
    private void validateAndSetConfig(ZentaoIntegrationConfig config) {
        zentaoClient.initConfig(config);
    }

    /**
     * 是否支持第三方模板
     *
     * @return 支持第三方模板的平台才会在MS平台存在默认模板
     */
    @Override
    public boolean isSupportDefaultTemplate() {
        return false;
    }

    /**
     * 获取第三方平台缺陷的自定义字段
     *
     * @param projectConfigStr 项目配置信息
     * @return 自定义字段集合
     */
    @Override
    public List<PlatformCustomFieldItemDTO> getDefaultTemplateCustomField(String projectConfigStr) {
        // isSupportDefaultTemplate 方法返回为True时, 实现;
        return null;
    }

    /**
     * 获取表单项
     *
     * @param request 表单项请求参数
     * @return 下拉项
     */
    @Override
    public List<SelectOption> getFormOptions(GetOptionRequest request) {
        String method = request.getOptionMethod();
        try {
            // 这里反射调用 getIssueTypes 等方法，获取下拉框选项
            // noinspection unchecked
            return (List<SelectOption>) this.getClass().getMethod(method, request.getClass()).invoke(this, request);
        } catch (InvocationTargetException e) {
            PluginLogUtils.error(e.getTargetException());
            throw new MSPluginException(e.getTargetException());
        } catch (Exception e) {
            PluginLogUtils.error(e);
            throw new MSPluginException(e);
        }
    }

    /**
     * 获取状态下拉转换
     *
     * @param projectConfig 项目配置
     * @param issueKey      缺陷ID
     * @return 状态选项
     */
    @Override
    public List<SelectOption> getStatusTransitions(String projectConfig, String issueKey) {
        // 禅道没有状态流, 每次查询全部下拉状态选项
        List<SelectOption> statusOptions = new ArrayList<>();
        for (ZentaoBugPlatformStatus status : ZentaoBugPlatformStatus.values()) {
            SelectOption option = new SelectOption();
            option.setText(status.getName());
            option.setValue(status.name());
            statusOptions.add(option);
        }
        return statusOptions;
    }

    /**
     * 分页获取需求
     *
     * @param request 需求分页参数
     * @return 需求分页集合
     */
    @Override
    public PluginPager<List<DemandDTO>> pageDemand(DemandPageRequest request) {
        projectConfig = getProjectConfig(request.getProjectConfig());
        validateProjectKey();
        return null;
    }

    /**
     * 获取关联的需求
     *
     * @param request 关联需求查询参数
     * @return 需求集合
     */
    @Override
    public List<DemandDTO> getDemands(DemandRelateQueryRequest request) {
        List<DemandDTO> list = new ArrayList<>();
        try {
            projectConfig = getProjectConfig(request.getProjectConfig());
            Map<String, Object> demandMap = zentaoClient.getDemands(projectConfig.getZentaoId());
            if (CollectionUtils.isEmpty(demandMap)) {
                return list;
            }
            String demandStr = demandMap.get("data").toString();
            if (StringUtils.isBlank(demandStr)) {
                return list;
            }

            // TODO: 组装需求数据返回
            // 兼容处理11.5版本格式 [{obj},{obj}]
            // if (data.charAt(0) == '[') {
            //     List array = JSON.parseArray(data);
            //     for (int i = 0; i < array.size(); i++) {
            //         Map o = (Map) array.get(i);
            //         DemandDTO demandDTO = new DemandDTO();
            //         demandDTO.setId(o.get("id").toString());
            //         demandDTO.setName(o.get("title").toString());
            //         demandDTO.setPlatform(key);
            //         list.add(demandDTO);
            //     }
            // }
            // // {"5": {"children": {"51": {}}}, "6": {}}
            // else if (data.startsWith("{\"")) {
            //     Map<String, Map<String, String>> dataMap = JSON.parseMap(data);
            //     Collection<Map<String, String>> values = dataMap.values();
            //     values.forEach(v -> {
            //         Map jsonObject = JSON.parseMap(JSON.toJSONString(v));
            //         DemandDTO demandDTO = new DemandDTO();
            //         demandDTO.setId(jsonObject.get("id").toString());
            //         demandDTO.setName(jsonObject.get("title").toString());
            //         demandDTO.setPlatform(key);
            //         list.add(demandDTO);
            //         if (jsonObject.get("children") != null) {
            //             LinkedHashMap<String, Map<String, String>> children = (LinkedHashMap<String, Map<String, String>>) jsonObject.get("children");
            //             Collection<Map<String, String>> childrenMap = children.values();
            //             childrenMap.forEach(ch -> {
            //                 DemandDTO dto = new DemandDTO();
            //                 dto.setId(ch.get("id"));
            //                 dto.setName(ch.get("title"));
            //                 dto.setPlatform(key);
            //                 list.add(dto);
            //             });
            //         }
            //     });
            // }
            // // 处理格式 {{"id": {obj}},{"id",{obj}}}
            // else if (data.charAt(0) == '{') {
            //     Map dataObject = (Map) obj.get("data");
            //     String s = JSON.toJSONString(dataObject);
            //     Map<String, Object> map = JSON.parseMap(s);
            //     Collection<Object> values = map.values();
            //     values.forEach(v -> {
            //         Map jsonObject = JSON.parseMap(JSON.toJSONString(v));
            //         DemandDTO demandDTO = new DemandDTO();
            //         demandDTO.setId(jsonObject.get("id").toString());
            //         demandDTO.setName(jsonObject.get("title").toString());
            //         demandDTO.setPlatform(key);
            //         list.add(demandDTO);
            //     });
            // }
        } catch (Exception e) {
            PluginLogUtils.error("get zentao related demands fail: ", e);
        }
        return list;
    }

    /**
     * 添加缺陷
     *
     * @param request 请求参数
     * @return MS缺陷
     */
    @Override
    public PlatformBugUpdateDTO addBug(PlatformBugUpdateRequest request) {
        // validate config
        validateConfig(request.getUserPlatformConfig(), request.getProjectConfig());

        // prepare and init zentao param
        PlatformBugUpdateDTO platformBug = new PlatformBugUpdateDTO();
        // filter status field
        PlatformCustomFieldItemDTO statusField = filterStatusTransition(request);
        // set param
        MultiValueMap<String, Object> param = buildUpdateParam(request, statusField == null ? null : statusField.getValue().toString());
        ZentaoAddBugResponse.Bug bug = zentaoClient.addBug(param);
        if (StringUtils.isNotBlank(bug.getId())) {
            platformBug.setPlatformBugKey(bug.getId());
        } else {
            throw new MSPluginException("禅道BUG同步失败, 请确认该集成账号是否开启超级model权限!");
        }
        return platformBug;
    }

    /**
     * 更新缺陷
     *
     * @param request 请求参数
     * @return MS缺陷
     */
    @Override
    public PlatformBugUpdateDTO updateBug(PlatformBugUpdateRequest request) {
        // validate config
        validateConfig(request.getUserPlatformConfig(), request.getProjectConfig());

        // prepare and init zentao param
        PlatformBugUpdateDTO platformBug = new PlatformBugUpdateDTO();
        // filter status field
        PlatformCustomFieldItemDTO statusField = filterStatusTransition(request);
        // set param
        MultiValueMap<String, Object> param = buildUpdateParam(request, statusField == null ? null : statusField.getValue().toString());

        zentaoClient.updateBug(request.getPlatformBugId(), param);
        return platformBug;
    }

    /**
     * 删除缺陷
     *
     * @param platformBugId 平台缺陷ID
     */
    @Override
    public void deleteBug(String platformBugId) {
        zentaoClient.deleteBug(platformBugId);
    }

    /**
     * 是否支持附件
     *
     * @return 是否支持附件
     */
    @Override
    public boolean isSupportAttachment() {
        // Zentao支持附件API
        return true;
    }

    /**
     * 同步附件至平台
     *
     * @param request 同步请求参数
     */
    @Override
    public void syncAttachmentToPlatform(SyncAttachmentToPlatformRequest request) {
        if (!isSupportAttachment()) {
            return;
        }
        String syncType = request.getSyncType();
        File file = request.getFile();
        if (StringUtils.equals(SyncAttachmentType.UPLOAD.syncOperateType(), syncType)) {
            // 上传附件
            zentaoClient.uploadAttachment("bug", request.getPlatformKey(), file);
        } else if (StringUtils.equals(SyncAttachmentType.DELETE.syncOperateType(), syncType)) {
            // 删除附件
            Map<String, Object> bugInfo = zentaoClient.getBugById(request.getPlatformKey());
            // noinspection unchecked
            Map<String, Object> zenFiles = (Map<String, Object>) bugInfo.get("files");
            for (String fileId : zenFiles.keySet()) {
                // noinspection unchecked
                Map<String, Object> fileInfo = (Map<String, Object>) zenFiles.get(fileId);
                if (file.getName().equals(fileInfo.get("title"))) {
                    zentaoClient.deleteAttachment(fileId);
                    break;
                }
            }
        }
    }

    /**
     * 同步缺陷
     *
     * @param request 同步参数
     * @return 同步结果
     */
    @Override
    public SyncBugResult syncBugs(SyncBugRequest request) {
        SyncBugResult syncResult = new SyncBugResult();
        List<PlatformBugDTO> bugs = request.getBugs();
        bugs.forEach(bug -> {
            Map<String, Object> zenBugInfo = zentaoClient.getBugById(bug.getPlatformBugId());
            // 缺陷未找到, 同步删除
            if (!StringUtils.equals(zenBugInfo.get("deleted").toString(), "1")) {
                syncZentaoFieldToMsBug(bug, zenBugInfo);
                parseAttachmentToMsBug(syncResult, bug, zenBugInfo);
                syncResult.getUpdateBug().add(bug);
            } else {
                syncResult.getDeleteBugIds().add(bug.getId());
            }
        });
        return syncResult;
    }

    @Override
    public void syncAllBugs(SyncAllBugRequest request) {
        // validate config
        projectConfig = getProjectConfig(request.getProjectConfig());
        validateProjectKey();

        // prepare page param
        int pageNum = 0, pageSize = 200, currentSize;
        try {
            do {
                // prepare post process func param
                List<PlatformBugDTO> needSyncBugs = new ArrayList<>();
                SyncBugResult syncBugResult = new SyncBugResult();

                // query zentao bug by page
                Map<String, Object> bugResponseMap = zentaoClient.getBugsByProjectId(pageNum, pageSize, projectConfig.getZentaoId());
                List<?> zentaoBugs = (List<?>) bugResponseMap.get("bugs");
                currentSize = zentaoBugs.size();
                zentaoBugs = filterBySyncCondition(zentaoBugs, request);
                if (!CollectionUtils.isEmpty(zentaoBugs)) {
                    for (Object bugObj : zentaoBugs) {
                        // transfer zentao bug field to ms
                        // noinspection unchecked
                        Map<String, Object> bugInfo = (Map<String, Object>) bugObj;
                        PlatformBugDTO bug = new PlatformBugDTO();
                        bug.setPlatformBugId(bugInfo.get("id").toString());
                        syncZentaoFieldToMsBug(bug, bugInfo);
                        parseAttachmentToMsBug(syncBugResult, bug, bugInfo);
                        needSyncBugs.add(bug);
                    }
                }

                // set post process func param
                // common sync post param {syncBugs: all need sync bugs, attachmentMap: all bug attachment}
                SyncPostParamRequest syncPostParamRequest = new SyncPostParamRequest();
                syncPostParamRequest.setNeedSyncBugs(needSyncBugs);
                syncPostParamRequest.setAttachmentMap(syncBugResult.getAttachmentMap());
                request.getSyncPostProcessFunc().accept(syncPostParamRequest);

                // 下一页
                pageNum++;
                // noinspection unchecked
                Map<String, Object> pagerMap = (Map<String, Object>) bugResponseMap.get("pager");
                if (pageNum > (Integer) (pagerMap).get("pageTotal")) {
                    // 禅道分页的页码超过总页数, 结束循环, 不然会一直死循环;
                    break;
                }
            } while (currentSize >= pageSize);
        } catch (Exception e) {
            PluginLogUtils.error(e);
            throw new MSPluginException(e);
        }
    }

    @Override
    public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
        zentaoClient.getAttachmentBytes(fileKey, inputStreamHandler);
    }

    /**
     * 获取项目配置
     *
     * @param configStr 项目配置JSON
     * @return 项目配置对象
     */
    private ZentaoProjectConfig getProjectConfig(String configStr) {
        if (StringUtils.isBlank(configStr)) {
            throw new MSPluginException("请在项目中添加Zentao项目ID配置！");
        }
        return PluginUtils.parseObject(configStr, ZentaoProjectConfig.class);
    }

    /**
     * 表单反射调用
     *
     * @param request 表单项请求参数
     * @return 用户下拉选项
     */
    public List<SelectOption> getAssignUsers(GetOptionRequest request) {
        Map<String, Object> obj = zentaoClient.getUsers();
        PluginLogUtils.info("zentao user " + obj);
        List<?> userList = PluginUtils.parseArray(obj.get("data").toString(), Map.class);
        List<SelectOption> users = new ArrayList<>();
        for (Object userObj : userList) {
            // noinspection unchecked
            Map<String, Object> user = (Map<String, Object>) userObj;
            users.add(new SelectOption(user.get("realname").toString(), user.get("account").toString()));
        }
        return users;
    }

    /**
     * 同步禅道平台字段到MS缺陷
     *
     * @param msBug  平台缺陷
     * @param zenBug 禅道缺陷
     */
    private void syncZentaoFieldToMsBug(PlatformBugDTO msBug, Map<String, Object> zenBug) {
        // TODO: 同步禅道字段到MS缺陷
        // ZentaoBugResponse.Bug bug = PluginUtils.parseObject(PluginUtils.toJSONString(zenBug), ZentaoBugResponse.Bug.class);
        // String description = bug.getSteps();
        // String steps = description;
        // try {
        //     steps = htmlDesc2MsDesc(zentao2MsDescription(description));
        // } catch (Exception e) {
        //     LogUtil.error(e.getMessage(), e);
        // }
        // if (issue == null) {
        //     issue = new PlatformIssuesDTO();
        //     if (StringUtils.isNotBlank(defaultCustomFields)) {
        //         issue.setCustomFieldList(JSON.parseArray(defaultCustomFields, PlatformCustomFieldItemDTO.class));
        //     } else {
        //         issue.setCustomFieldList(new ArrayList<>());
        //     }
        // } else {
        //     mergeCustomField(issue, defaultCustomFields);
        // }
        // issue.setPlatformStatus(bugObj.getStatus());
        // if (StringUtils.equals(bugObj.getDeleted(), "1")) {
        //     issue.setPlatformStatus("DELETE");
        // }
        // issue.setTitle(bugObj.getTitle());
        // issue.setDescription(steps);
        // issue.setReporter(bugObj.getOpenedBy());
        // issue.setPlatform(key);
        // try {
        //     String openedDate = bug.get("openedDate").toString();
        //     String lastEditedDate = bug.get("lastEditedDate").toString();
        //     if (StringUtils.isNotBlank(openedDate) && !openedDate.startsWith("0000-00-00"))
        //         issue.setCreateTime(DateUtils.getTime(openedDate).getTime());
        //     if (StringUtils.isNotBlank(lastEditedDate) && !lastEditedDate.startsWith("0000-00-00"))
        //         issue.setUpdateTime(DateUtils.getTime(lastEditedDate).getTime());
        // } catch (Exception e) {
        //     LogUtil.error("update zentao time" + e.getMessage());
        // }
        // if (issue.getUpdateTime() == null) {
        //     issue.setUpdateTime(System.currentTimeMillis());
        // }
        // List<PlatformCustomFieldItemDTO> customFieldList = syncIssueCustomFieldList(issue.getCustomFieldList(), bug);
        // handleSpecialField(customFieldList);
        // issue.setCustomFields(JSON.toJSONString(customFieldList));
        // return issue;
    }


    private List<?> filterBySyncCondition(List<?> zentaoBugs, SyncAllBugRequest request) {
        if (request.getCreateTime() == null) {
            return zentaoBugs;
        }
        return zentaoBugs.stream().filter(bug -> {
            // noinspection unchecked
            Map<String, Object> bugMap = (Map<String, Object>) bug;
            long createTimeMills;
            try {
                createTimeMills = sdfWithZone.parse(bugMap.get("openedDate").toString()).getTime();
                if (request.isPre()) {
                    return createTimeMills <= request.getCreateTime();
                } else {
                    return createTimeMills >= request.getCreateTime();
                }
            } catch (Exception e) {
                PluginLogUtils.error(e.getMessage());
                return false;
            }
        }).collect(Collectors.toList());
    }


    /**
     * 同步禅道附件
     *
     * @param syncResult 同步结果
     * @param bug        缺陷
     * @param zenBugInfo 禅道缺陷
     */
    public void parseAttachmentToMsBug(SyncBugResult syncResult, PlatformBugDTO bug, Map<String, Object> zenBugInfo) {
        try {
            Object files = zenBugInfo.get("files");
            Map<String, Object> zenFiles;
            if (files instanceof List && ((List<?>) files).isEmpty()) {
                zenFiles = null;
            } else {
                // noinspection unchecked
                zenFiles = (Map<String, Object>) files;
            }
            if (!CollectionUtils.isEmpty(zenFiles)) {
                Map<String, List<PlatformAttachment>> attachmentMap = syncResult.getAttachmentMap();
                attachmentMap.put(bug.getId(), new ArrayList<>());
                for (String fileId : zenFiles.keySet()) {
                    // noinspection unchecked
                    Map<String, Object> fileInfo = (Map<String, Object>) zenFiles.get(fileId);
                    String filename = fileInfo.get("title").toString();
                    PlatformAttachment syncAttachment = new PlatformAttachment();
                    // name 用于查重
                    syncAttachment.setFileName(filename);
                    // key 用于获取附件内容
                    syncAttachment.setFileKey(fileId);
                    attachmentMap.get(bug.getId()).add(syncAttachment);
                }
            }
        } catch (Exception e) {
            PluginLogUtils.error(e);
        }
    }

    /**
     * 生成新增, 更新参数
     *
     * @param request 请求参数
     * @param status  状态
     * @return 参数
     */
    private MultiValueMap<String, Object> buildUpdateParam(PlatformBugUpdateRequest request, String status) {
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("product", this.projectConfig.getZentaoId());
        paramMap.add("title", request.getTitle());
        if (!StringUtils.isEmpty(status)) {
            paramMap.add("status", status);
            // 状态为不同项时需设置时间
            parseStatusItemParam(paramMap);
        }
        // TODO: 处理缺陷中的富文本图片
        paramMap.add("steps", request.getDescription());
        parseCustomFields(request, paramMap);
        setSpecialField(paramMap);
        return paramMap;
    }

    /**
     * 解析自定义字段
     *
     * @param request  请求参数
     * @param paramMap 参数
     */
    protected void parseCustomFields(PlatformBugUpdateRequest request, MultiValueMap<String, Object> paramMap) {
        List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
        if (!CollectionUtils.isEmpty(customFields)) {
            customFields.forEach(item -> {
                if (StringUtils.isNotBlank(item.getCustomData())) {
                    if (item.getValue() instanceof String) {
                        paramMap.add(item.getCustomData(), ((String) item.getValue()).trim());
                    } else {
                        paramMap.add(item.getCustomData(), item.getValue());
                    }
                }
            });
        }
    }

    /**
     * 设置特殊字段 {openedBuild}
     *
     * @param paramMap 参数
     */
    private void setSpecialField(MultiValueMap<String, Object> paramMap) {
        try {
            // handle opened-build field
            List<Object> buildValue = paramMap.get("openedBuild");
            paramMap.remove("openedBuild");
            if (!CollectionUtils.isEmpty(buildValue)) {
                List<String> builds = PluginUtils.parseArray(buildValue.get(0).toString(), String.class);
                if (!CollectionUtils.isEmpty(builds)) {
                    builds.forEach(build -> paramMap.add("openedBuild[]", build));
                } else {
                    paramMap.add("openedBuild", "trunk");
                }
            } else {
                paramMap.add("openedBuild", "trunk");
            }
        } catch (Exception e) {
            PluginLogUtils.error(e);
        }
    }

    /**
     * 过滤出自定义字段中的状态字段
     *
     * @param request 请求参数
     * @return 状态自定义字段
     */
    private PlatformCustomFieldItemDTO filterStatusTransition(PlatformBugUpdateRequest request) {
        if (!CollectionUtils.isEmpty(request.getCustomFieldList())) {
            // remove and return issue status
            List<PlatformCustomFieldItemDTO> statusList = request.getCustomFieldList().stream().filter(item ->
                    StringUtils.equals(item.getCustomData(), "status")).toList();
            request.getCustomFieldList().removeAll(statusList);
            return statusList.get(0);
        } else {
            return null;
        }
    }


    /**
     * 解析不同状态项, 设置状态时间及解决方案字段{resolvedDate, closedDate, resolution}
     *
     * @param param 请求参数
     */
    private void parseStatusItemParam(MultiValueMap<String, Object> param) {
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
            PluginLogUtils.error(e.getMessage());
        }
    }
}
