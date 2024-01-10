package io.metersphere.plugin.zentao.impl;

import io.metersphere.plugin.platform.dto.PlatformAttachment;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.reponse.DemandRelatePageResponse;
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
import io.metersphere.plugin.zentao.constants.ZentaoDemandCustomField;
import io.metersphere.plugin.zentao.domain.*;
import io.metersphere.plugin.zentao.enums.ZentaoBugPlatformStatus;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Extension
public class ZentaoPlatform extends AbstractPlatform {

    protected ZentaoClient zentaoClient;

    protected ZentaoProjectConfig projectConfig;

    protected SimpleDateFormat sdfWithZone = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    protected static final String DATE_PREFIX = "0000-00-00";

    public ZentaoPlatform(PlatformRequest request) {
        super(request);
        ZentaoIntegrationConfig zentaoConfig = getIntegrationConfig(request.getIntegrationConfig(), ZentaoIntegrationConfig.class);
        zentaoClient = ZentaoFactory.getInstance(zentaoConfig.getAddress(), zentaoConfig.getRequestType());
        setUserConfig(request.getIntegrationConfig(), false);
    }

    /**
     * 校验集成配置
     */
    @Override
    public void validateIntegrationConfig() {
        zentaoClient.auth();
    }

    @Override
    public void validateUserConfig(String userConfig) {
        setUserConfig(userConfig, true);
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
        setUserConfig(userPlatformConfig, true);
        this.projectConfig = getProjectConfig(projectConfig);
        validateProjectKey();
    }


    /**
     * 设置用户平台配置(集成信息)
     *
     * @param userPlatformConfig 用户平台配置
     */
    public void setUserConfig(String userPlatformConfig, Boolean isUserConfig) {
        ZentaoIntegrationConfig integrationConfig;
        if (isUserConfig) {
            // 如果是用户配置, 则直接从平台参数中获取集成信息, 并替换用户账号配置
            integrationConfig = getIntegrationConfig(ZentaoIntegrationConfig.class);
            ZentaoPlatformUserInfo userConfig = PluginUtils.parseObject(userPlatformConfig, ZentaoPlatformUserInfo.class);
            if (userConfig == null) {
                throw new MSPluginException("三方平台账号配置为空!");
            }
            integrationConfig.setAccount(userConfig.getZentaoAccount());
            integrationConfig.setPassword(userConfig.getZentaoPassword());
        } else {
            // 如果是集成配置, 则直接从参数中获取集成信息
            integrationConfig = getIntegrationConfig(userPlatformConfig, ZentaoIntegrationConfig.class);
        }
        validateAndSetConfig(integrationConfig);
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
        // Zentao don't Support Default Template
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
        // when isSupportDefaultTemplate get true, implement this method;
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
            // get form option by reflection
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
        // Zentao don't support status flow, query all status item and return
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
    public PluginPager<DemandRelatePageResponse> pageDemand(DemandPageRequest request) {
        // validate demand config
        projectConfig = getProjectConfig(request.getProjectConfig());
        validateProjectKey();
        // query demand list
        Map<String, Object> demandMapResponse = zentaoClient.pageDemands(projectConfig.getZentaoId(),
                request.getStartPage(), request.getPageSize(), request.getQuery(), request.getFilter());
        // handle empty data
        if (demandMapResponse == null) {
            return new PluginPager<>(request.getStartPage(), request.getPageSize());
        }
        String demandData = demandMapResponse.get("data").toString();
        if (StringUtils.isBlank(demandData)) {
            return new PluginPager<>(request.getStartPage(), request.getPageSize());
        }

        // prepare custom headers
        List<PlatformCustomFieldItemDTO> customHeaders = new ArrayList<>();
        // set plan to custom headers, and support search
        PlatformCustomFieldItemDTO iterationField = new PlatformCustomFieldItemDTO();
        iterationField.setId(ZentaoDemandCustomField.PLAN_FIELD_ID);
        iterationField.setName(ZentaoDemandCustomField.PLAN_FIELD_NAME);
        iterationField.setSupportSearch(true);
        iterationField.setOptions(PluginUtils.toJSONString(zentaoClient.getProductPlanOption(projectConfig.getZentaoId())));
        customHeaders.add(iterationField);
        // prepare demand list
        List<DemandRelatePageResponse.Demand> demands = new ArrayList<>();
        // noinspection unchecked
        Map<String, Map<String, String>> demandMap = PluginUtils.parseMap(demandData);
        Collection<Map<String, String>> values = demandMap.values();
        values.forEach(v -> {
            // noinspection unchecked
            Map<String, Object> demandObj = PluginUtils.parseMap(PluginUtils.toJSONString(v));
            DemandRelatePageResponse.Demand demand = new DemandRelatePageResponse.Demand();
            demand.setDemandId(demandObj.get("id").toString());
            demand.setDemandName(demandObj.get("title").toString());
            demand.setDemandUrl(zentaoClient.getBaseUrl() + "/story-view-" + demand.getDemandId() + ".html");
            // add plan to custom fields
            Map<String, Object> customFields = new HashMap<>(1);
            customFields.put(ZentaoDemandCustomField.PLAN_FIELD_ID, demandObj.get("plan").toString());
            demand.setCustomFields(customFields);
            demands.add(demand);
            if (demandObj.get("children") != null) {
                // handle children demand list
                // noinspection unchecked
                LinkedHashMap<String, Map<String, String>> children = (LinkedHashMap<String, Map<String, String>>) demandObj.get("children");
                Collection<Map<String, String>> childrenMap = children.values();
                childrenMap.forEach(childrenDemandObj -> {
                    DemandRelatePageResponse.Demand childDemand = new DemandRelatePageResponse.Demand();
                    childDemand.setDemandId(childrenDemandObj.get("id"));
                    childDemand.setDemandName(childrenDemandObj.get("title"));
                    childDemand.setDemandUrl(zentaoClient.getBaseUrl() + "/story-view-" + childDemand.getDemandId() + ".html");
                    childDemand.setParent(demand.getDemandId());
                    // add plan to custom fields
                    Map<String, Object> childCustomFields = new HashMap<>(1);
                    childCustomFields.put(ZentaoDemandCustomField.PLAN_FIELD_ID, demandObj.get("plan").toString());
                    childDemand.setCustomFields(childCustomFields);
                    demands.add(childDemand);
                });
            }
        });
        // sort by demand id
        demands.sort(Comparator.comparing(DemandRelatePageResponse.Demand::getDemandId));
        // filter by condition
        List<DemandRelatePageResponse.Demand> filterDemands = demands;
        // filter by query
        if (StringUtils.isNotBlank(request.getQuery())) {
            filterDemands = filterDemands.stream().filter(demand -> demand.getDemandName().contains(request.getQuery())).collect(Collectors.toList());
        }
        if (!CollectionUtils.isEmpty(request.getFilter())) {
            filterDemands = filterDemands.stream().filter(demand -> {
                boolean pass = true;
                for (String key : request.getFilter().keySet()) {
                    if (demand.getCustomFields().get(key) == null || !StringUtils.equals(demand.getCustomFields().get(key).toString(), request.getFilter().get(key).toString())) {
                        pass = false;
                        break;
                    }
                }
                return pass;
            }).collect(Collectors.toList());
        }
        // pager
        filterDemands = filterDemands.stream().skip((long) (request.getStartPage() - 1) * request.getPageSize()).limit(request.getPageSize()).collect(Collectors.toList());
        // set demand response
        DemandRelatePageResponse demandRelatePageData = new DemandRelatePageResponse();
        demandRelatePageData.setDemandList(filterDemands);
        demandRelatePageData.setCustomHeaders(customHeaders);
        return new PluginPager<>(demandRelatePageData, demands.size(), request.getPageSize(), request.getStartPage());
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
        MultiValueMap<String, Object> param = buildUpdateParam(request, statusField == null ? null : statusField.getValue().toString(), platformBug);
        ZentaoAddBugResponse.Bug bug = zentaoClient.addBug(param);
        if (StringUtils.isNotBlank(bug.getId())) {
            platformBug.setPlatformBugKey(bug.getId());
        } else {
            throw new MSPluginException("禅道BUG同步新增失败, 请确认该集成账号是否开启超级model权限!");
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
        MultiValueMap<String, Object> param = buildUpdateParam(request, statusField == null ? null : statusField.getValue().toString(), platformBug);

        zentaoClient.updateBug(request.getPlatformBugId(), param);
        platformBug.setPlatformBugKey(request.getPlatformBugId());
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
        // Zentao JSON-API Support Attachment
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
            // upload attachment
            zentaoClient.uploadAttachment("bug", request.getPlatformKey(), file);
        } else if (StringUtils.equals(SyncAttachmentType.DELETE.syncOperateType(), syncType)) {
            // delete attachment
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
            if (!StringUtils.equals(zenBugInfo.get("deleted").toString(), "1")) {
                syncZentaoFieldToMsBug(bug, zenBugInfo, false);
                parseAttachmentToMsBug(syncResult, bug);
                syncResult.getUpdateBug().add(bug);
            } else {
                // not found, delete it
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
                        Map<String, Object> zenBugInfo = (Map<String, Object>) bugObj;
                        PlatformBugDTO bug = new PlatformBugDTO();
                        bug.setId(UUID.randomUUID().toString());
                        bug.setPlatformBugId(zenBugInfo.get("id").toString());
                        syncZentaoFieldToMsBug(bug, zenBugInfo, true);
                        // handle attachment
                        parseAttachmentToMsBug(syncBugResult, bug);
                        needSyncBugs.add(bug);
                    }
                }

                // set post process func param
                // common sync post param {syncBugs: all need sync bugs, attachmentMap: all bug attachment}
                SyncPostParamRequest syncPostParamRequest = new SyncPostParamRequest();
                syncPostParamRequest.setNeedSyncBugs(needSyncBugs);
                syncPostParamRequest.setAttachmentMap(syncBugResult.getAttachmentMap());
                request.getSyncPostProcessFunc().accept(syncPostParamRequest);

                // next page
                pageNum++;
                // noinspection unchecked
                Map<String, Object> pagerMap = (Map<String, Object>) bugResponseMap.get("pager");
                if (pageNum > (Integer) (pagerMap).get("pageTotal")) {
                    // if page num > page total, break loop; avoid loop forever
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
     * @param msBug      平台缺陷
     * @param zenBugInfo 禅道缺陷
     */
    private void syncZentaoFieldToMsBug(PlatformBugDTO msBug, Map<String, Object> zenBugInfo, boolean useCustomAllFields) {
        try {
            // 处理基础字段
            parseBaseFieldToMsBug(msBug, zenBugInfo);
            // 处理自定义字段
            parseCustomFieldToMsBug(msBug, zenBugInfo, useCustomAllFields);
        } catch (Exception e) {
            PluginLogUtils.error(e);
        }
    }

    /**
     * 解析基础字段到平台缺陷字段
     *
     * @param msBug      平台缺陷
     * @param zenBugInfo 禅道字段集合
     */
    private void parseBaseFieldToMsBug(PlatformBugDTO msBug, Map<String, Object> zenBugInfo) {
        ZentaoBugResponse.Bug zenBug = PluginUtils.parseObject(PluginUtils.toJSONString(zenBugInfo), ZentaoBugResponse.Bug.class);
        // 处理基础字段(TITLE, DESCRIPTION, HANDLE_USER, STATUS)
        msBug.setTitle(zenBug.getTitle());
        // TODO: 描述中图片文本暂时未处理
        msBug.setDescription(zenBug.getSteps());
        if (StringUtils.isEmpty(zenBug.getAssignedTo())) {
            msBug.setHandleUser(StringUtils.EMPTY);
        } else {
            if (!StringUtils.equals(msBug.getHandleUser(), zenBug.getAssignedTo())) {
                msBug.setHandleUser(zenBug.getAssignedTo());
                msBug.setHandleUsers(StringUtils.isBlank(msBug.getHandleUsers()) ? zenBug.getAssignedTo() : msBug.getHandleUsers() + "," + zenBug.getAssignedTo());
            }
        }
        msBug.setStatus(zenBug.getStatus());
        msBug.setCreateUser("admin");
        msBug.setUpdateUser("admin");
        try {
            String openedDate = zenBugInfo.get("openedDate").toString();
            String lastEditedDate = zenBugInfo.get("lastEditedDate").toString();
            if (StringUtils.isNotBlank(openedDate) && !openedDate.startsWith(DATE_PREFIX)) {
                msBug.setCreateTime(sdfWithZone.parse(openedDate).getTime());
            } else {
                msBug.setCreateTime(System.currentTimeMillis());
            }
            if (StringUtils.isNotBlank(lastEditedDate) && !lastEditedDate.startsWith(DATE_PREFIX)) {
                msBug.setUpdateTime(sdfWithZone.parse(openedDate).getTime());
            } else {
                msBug.setUpdateTime(System.currentTimeMillis());
            }
        } catch (Exception e) {
            PluginLogUtils.error("parse zentao bug time error: " + e.getMessage());
        }
    }

    /**
     * 解析自定义字段到平台缺陷字段
     *
     * @param msBug  平台缺陷
     * @param zenBug 禅道字段集合
     */
    private void parseCustomFieldToMsBug(PlatformBugDTO msBug, Map<String, Object> zenBug, boolean useCustomAllFields) {
        List<PlatformCustomFieldItemDTO> needSyncCustomFields = new ArrayList<>();
        if (useCustomAllFields) {
            // 同步全量的时候, 需要同步所有自定义字段
            zenBug.keySet().forEach(fieldKey -> {
                PlatformCustomFieldItemDTO field = new PlatformCustomFieldItemDTO();
                field.setId(fieldKey);
                field.setValue(zenBug.get(fieldKey));
                needSyncCustomFields.add(field);
            });
        } else {
            // 同步存量缺陷时, 只需同步MS配置的API自定义字段
            for (PlatformCustomFieldItemDTO field : msBug.getNeedSyncCustomFields()) {
                needSyncCustomFields.add(SerializationUtils.clone(field));
            }
            if (CollectionUtils.isEmpty(needSyncCustomFields)) {
                return;
            }
            needSyncCustomFields.forEach(field -> {
                Object value = zenBug.get(field.getCustomData());
                if (value != null) {
                    if (value instanceof Map) {
                        field.setValue(getSyncJsonParamValue(value));
                    } else if (value instanceof List) {
                        if (CollectionUtils.isEmpty((List) value)) {
                            field.setValue(null);
                        } else {
                            List<Object> values = new ArrayList<>();
                            // noinspection unchecked
                            ((List) value).forEach(attr -> {
                                if (attr instanceof Map) {
                                    values.add(getSyncJsonParamValue(attr));
                                } else {
                                    values.add(attr);
                                }
                            });
                            field.setValue(values);
                        }
                    } else {
                        field.setValue(value.toString());
                    }
                } else {
                    field.setValue(null);
                }
            });
        }
        msBug.setCustomFieldList(needSyncCustomFields);
    }

    /**
     * 获取同步的JSON值
     *
     * @param value 对象值
     * @return JSON字符串
     */
    private String getSyncJsonParamValue(Object value) {
        Map valObj = ((Map) value);
        Map child = (Map) valObj.get("child");
        String idValue = Optional.ofNullable(valObj.get("id")).orElse(StringUtils.EMPTY).toString();

        if (child != null) {// 级联框
            return PluginUtils.toJSONString(getCascadeValues(idValue, child));
        } else {
            if (StringUtils.isNotBlank(idValue)) {
                return idValue;
            } else {
                return valObj.get("key") == null ? null : valObj.get("key").toString();
            }
        }
    }

    /**
     * 获取级联框的值
     *
     * @param idValue id值
     * @param child   子级元素值
     * @return 级联框的值
     */
    private List<Object> getCascadeValues(String idValue, Map child) {
        List<Object> values = new ArrayList<>();
        if (StringUtils.isNotBlank(idValue)) {
            values.add(idValue);
        }
        if (child.get("id") != null && StringUtils.isNotBlank(child.get("id").toString())) {
            values.add(child.get("id"));
        }
        return values;
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
     */
    public void parseAttachmentToMsBug(SyncBugResult syncResult, PlatformBugDTO bug) {
        try {
            Map<String, Object> zenBugInfo = zentaoClient.getBugById(bug.getPlatformBugId());
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
                    // name for check
                    syncAttachment.setFileName(filename);
                    // key for get attachment content
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
    private MultiValueMap<String, Object> buildUpdateParam(PlatformBugUpdateRequest request, String status, PlatformBugUpdateDTO platformBug) {
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
        parseCustomFields(request, paramMap, platformBug);
        setSpecialField(paramMap);
        return paramMap;
    }

    /**
     * 解析自定义字段
     *
     * @param request  请求参数
     * @param paramMap 参数
     */
    protected void parseCustomFields(PlatformBugUpdateRequest request, MultiValueMap<String, Object> paramMap, PlatformBugUpdateDTO platformBug) {
        List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
        if (!CollectionUtils.isEmpty(customFields)) {
            Iterator<PlatformCustomFieldItemDTO> iterator = customFields.iterator();
            while (iterator.hasNext()) {
                PlatformCustomFieldItemDTO item = iterator.next();
                if (StringUtils.isNotBlank(item.getCustomData())) {
                    if (StringUtils.equals(item.getCustomData(), "assignedTo")) {
                        // 默认将禅道指派人设置到MS处理人, 并移除平台缺陷中的指派人字段
                        platformBug.setPlatformHandleUser(item.getValue().toString());
                        iterator.remove();
                    }
                    if (item.getValue() instanceof String) {
                        paramMap.add(item.getCustomData(), ((String) item.getValue()).trim());
                    } else {
                        paramMap.add(item.getCustomData(), item.getValue());
                    }
                }
            }
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
            // filter and return bug status by custom fields, then remove it;
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
                    // set resolution fixed
                    param.add("resolution", "fixed");
                }
            }
        } catch (Exception e) {
            PluginLogUtils.error(e.getMessage());
        }
    }
}
