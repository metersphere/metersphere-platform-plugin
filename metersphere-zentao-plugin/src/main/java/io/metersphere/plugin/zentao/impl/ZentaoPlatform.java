package io.metersphere.plugin.zentao.impl;

import io.metersphere.plugin.platform.dto.PlatformAttachment;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.reponse.PlatformBugDTO;
import io.metersphere.plugin.platform.dto.reponse.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.reponse.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.reponse.PlatformDemandDTO;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import io.metersphere.plugin.platform.enums.SyncAttachmentType;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginBeanUtils;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.zentao.client.ZentaoClient;
import io.metersphere.plugin.zentao.client.ZentaoFactory;
import io.metersphere.plugin.zentao.client.ZentaoRestClient;
import io.metersphere.plugin.zentao.constants.ZentaoDemandCustomField;
import io.metersphere.plugin.zentao.domain.ZentaoIntegrationConfig;
import io.metersphere.plugin.zentao.domain.ZentaoPlatformUserInfo;
import io.metersphere.plugin.zentao.domain.ZentaoProjectConfig;
import io.metersphere.plugin.zentao.domain.request.rest.ZentaoRestBugEditRequest;
import io.metersphere.plugin.zentao.domain.response.json.ZentaoBugResponse;
import io.metersphere.plugin.zentao.domain.response.rest.*;
import io.metersphere.plugin.zentao.enums.ZentaoBugPlatformStatus;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author song-cc-rock
 */
@Extension
public class ZentaoPlatform extends AbstractPlatform {

	protected ZentaoClient zentaoClient;

	protected ZentaoRestClient zentaoRestClient;

	protected ZentaoProjectConfig projectConfig;

	protected SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	protected static final String DATE_PREFIX = "0000-00-00";

	protected static final String MS_RICH_TEXT_PREVIEW_SRC_PREFIX = "/bug/attachment/preview/md";

	protected static final String ZENTAO_RICH_TEXT_IMG_SRC_PREFIX = "/file-read-";

	public ZentaoPlatform(PlatformRequest request) {
		super(request);
		ZentaoIntegrationConfig zentaoConfig = getIntegrationConfig(request.getIntegrationConfig(), ZentaoIntegrationConfig.class);
		zentaoClient = ZentaoFactory.getInstance(zentaoConfig.getAddress(), zentaoConfig.getRequestType());
		zentaoRestClient = new ZentaoRestClient(zentaoConfig.getAddress());
		setUserConfig(request.getIntegrationConfig(), false);
	}

	/**
	 * 校验集成配置
	 */
	@Override
	public void validateIntegrationConfig() {
		zentaoRestClient.auth();
	}

	/**
	 * 校验用户配置
	 *
	 * @param userConfig 用户配置
	 */
	@Override
	public void validateUserConfig(String userConfig) {
		setUserConfig(userConfig, true);
		zentaoRestClient.auth();
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
			zentaoRestClient.validateProject(projectConfig.getZentaoKey(), projectConfig.getType());
		} catch (Exception e) {
			throw new MSPluginException(e.getMessage());
		}
	}

	/**
	 * 校验需求/缺陷项目KEY
	 */
	public void validateProjectKey() {
		if (StringUtils.isBlank(projectConfig.getZentaoKey())) {
			throw new MSPluginException("请在项目中配置禅道的项目Key!");
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
			if (userConfig != null) {
				integrationConfig.setAccount(userConfig.getZentaoAccount());
				integrationConfig.setPassword(userConfig.getZentaoPassword());
			}
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
		zentaoRestClient.initConfig(config);
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
	public PluginPager<PlatformDemandDTO> pageDemand(DemandPageRequest request) {
		List<PlatformDemandDTO.Demand> demands = queryDemandList(request, null);
		int total = demands.size();
		if (request.isSelectAll()) {
			// no pager
			// set demand response
			PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
			demandRelatePageData.setList(demands);
			demandRelatePageData.setCustomHeaders(getDemandCustomField());
			return new PluginPager<>(demandRelatePageData, total, Integer.MAX_VALUE, request.getStartPage());
		} else {
			// pager
			demands = demands.stream().skip((long) (request.getStartPage() - 1) * request.getPageSize()).limit(request.getPageSize()).collect(Collectors.toList());
			// set demand response
			PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
			demandRelatePageData.setList(demands);
			demandRelatePageData.setCustomHeaders(getDemandCustomField());
			return new PluginPager<>(demandRelatePageData, total, request.getPageSize(), request.getStartPage());
		}
	}

	/**
	 * 根据关联的需求ID查询平台需求信息
	 *
	 * @param request 需求关联查询参数
	 * @return 平台需求信息
	 */
	@Override
	public PlatformDemandDTO getDemands(DemandRelateQueryRequest request) {
		DemandPageRequest requestParam = new DemandPageRequest();
		requestParam.setProjectConfig(request.getProjectConfig());
		List<PlatformDemandDTO.Demand> demands = queryDemandList(requestParam, request.getRelateDemandIds());
		// set demand response
		PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
		demandRelatePageData.setList(demands);
		demandRelatePageData.setCustomHeaders(getDemandCustomField());
		return demandRelatePageData;
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
		ZentaoRestBugEditRequest editRequest = buildUpdateParam(request, platformBug);
		if (StringUtils.equals("projects", projectConfig.getType())) {
			// 项目型项目, 需设置所属项目
			editRequest.setProject(projectConfig.getZentaoKey());
		}
		ZentaoBugRestEditResponse zentaoBug = zentaoRestClient.add(editRequest, projectConfig.getZentaoKey());
		if (zentaoBug != null && StringUtils.isNotBlank(zentaoBug.getId())) {
			platformBug.setPlatformBugKey(zentaoBug.getId());
			platformBug.setPlatformStatus(statusField.getValue().toString());
		} else {
			throw new MSPluginException("创建禅道缺陷失败!");
		}

		new Thread(() -> {
			// transition zentao bug status
			transitionStatus(statusField, zentaoBug.getId(), editRequest.getAssignedTo());
		}).start();

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
		ZentaoRestBugEditRequest editParam = buildUpdateParam(request, platformBug);

		ZentaoBugRestEditResponse zentaoBug = zentaoRestClient.update(editParam, request.getPlatformBugId());
		platformBug.setPlatformBugKey(zentaoBug.getId());
		// transition zentao bug status
		platformBug.setPlatformStatus(statusField.getValue().toString());

		new Thread(() -> {
			transitionStatus(statusField, zentaoBug.getId(), editParam.getAssignedTo());
		}).start();
		return platformBug;
	}

	/**
	 * 删除缺陷
	 *
	 * @param platformBugId 平台缺陷ID
	 */
	@Override
	public void deleteBug(String platformBugId) {
		zentaoRestClient.delete(platformBugId);
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
		String syncType = request.getSyncType();
		File file = request.getFile();
		if (StringUtils.equals(SyncAttachmentType.UPLOAD.syncOperateType(), syncType)) {
			// upload attachment
			zentaoClient.uploadAttachment("bug", request.getPlatformKey(), file);
		} else if (StringUtils.equals(SyncAttachmentType.DELETE.syncOperateType(), syncType)) {
			// delete attachment
			ZentaoRestBugDetailResponse response = zentaoRestClient.get(request.getPlatformKey());
			Object files = response.getFiles();
			if (files instanceof Map) {
				// noinspection unchecked
				Map<String, LinkedHashMap<String, Object>> zenFiles = (Map<String, LinkedHashMap<String, Object>>) files;
				for (String fileId : zenFiles.keySet()) {
					LinkedHashMap<String, Object> zenFileMap = zenFiles.get(fileId);
					if (StringUtils.equals(file.getName(), zenFileMap.get("title").toString())) {
						zentaoClient.deleteAttachment(fileId);
						break;
					}
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
				parseAttachmentOrBuildToMsBug(syncResult, bug);
				syncResult.getUpdateBug().add(bug);
			} else {
				// not found, delete it
				syncResult.getDeleteBugIds().add(bug.getId());
			}
		});
		return syncResult;
	}

	/**
	 * 同步缺陷(全量)
	 *
	 * @param request 同步全量缺陷请求参数
	 */
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
				Map<String, Object> bugResponseMap = zentaoClient.getBugsByProjectId(pageNum, pageSize, projectConfig.getZentaoKey());
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
						parseAttachmentOrBuildToMsBug(syncBugResult, bug);
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

	/**
	 * 获取附件内容
	 *
	 * @param fileKey            附件ID
	 * @param inputStreamHandler 处理方法
	 */
	@Override
	public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
		zentaoClient.getAttachmentBytes(fileKey, inputStreamHandler);
	}

	/**
	 * 查询需求列表
	 *
	 * @param request   需求请求参数
	 * @param filterIds 过滤的需求ID
	 * @return 需求列表
	 */
	private List<PlatformDemandDTO.Demand> queryDemandList(DemandPageRequest request, List<String> filterIds) {
		// validate demand config
		projectConfig = getProjectConfig(request.getProjectConfig());
		validateProjectKey();
		// query demand list no limit
		ZentaoRestDemandResponse response = zentaoRestClient.pageDemands(projectConfig.getZentaoKey(), projectConfig.getType(), request.getStartPage(), Integer.MAX_VALUE);
		// handle empty data
		if (response == null || CollectionUtils.isEmpty(response.getStories())) {
			return List.of();
		}

		// prepare demand list
		List<PlatformDemandDTO.Demand> demands = new ArrayList<>();
		response.getStories().forEach(story -> {
			PlatformDemandDTO.Demand demand = new PlatformDemandDTO.Demand();
			demand.setDemandId(story.getId());
			demand.setDemandName(story.getTitle());
			demand.setDemandUrl(zentaoRestClient.getBaseUrl() + "story-view-" + story.getId() + ".html");
			// add plan to custom fields
			demand.getCustomFields().put(ZentaoDemandCustomField.PLAN_FIELD_ID, story.getPlan());
			boolean isParentDemandShow = StringUtils.isBlank(request.getQuery()) || demand.getDemandName().contains(request.getQuery()) || demand.getDemandId().contains(request.getQuery());
			if (!CollectionUtils.isEmpty(story.getChildren())) {
				List<PlatformDemandDTO.Demand> childrenDemands = new ArrayList<>();
				// handle children demand list
				story.getChildren().forEach(childStory -> {
					PlatformDemandDTO.Demand childDemand = new PlatformDemandDTO.Demand();
					childDemand.setDemandId(childStory.getId());
					childDemand.setDemandName(childStory.getTitle());
					childDemand.setDemandUrl(zentaoRestClient.getBaseUrl() + "story-view-" + childStory.getId() + ".html");
					childDemand.setParent(demand.getDemandId());
					// add plan to custom fields
					childDemand.getCustomFields().put(ZentaoDemandCustomField.PLAN_FIELD_ID, childStory.getPlan());
					boolean isChildDemandShow = StringUtils.isBlank(request.getQuery()) || childDemand.getDemandName().contains(request.getQuery()) || childDemand.getDemandId().contains(request.getQuery());
					if (isChildDemandShow) {
						// 满足过滤条件的子需求, 才展示
						childrenDemands.add(childDemand);
					}
				});
				demand.setChildren(childrenDemands);
			}
			if (isParentDemandShow || !CollectionUtils.isEmpty(demand.getChildren())) {
				// 满足过滤条件的父需求, 或者有满足过滤条件的子需求, 才展示
				demands.add(demand);
			}
		});
		// sort by demand id
		demands.sort(Comparator.comparing(PlatformDemandDTO.Demand::getDemandId));
		// filter by condition
		List<PlatformDemandDTO.Demand> filterDemands = demands;
		if (!CollectionUtils.isEmpty(request.getFilter())) {
			filterDemands = filterDemands.stream().filter(demand -> {
				boolean pass = true;
				for (String key : request.getFilter().keySet()) {
					if (demand.getCustomFields().get(key) == null) {
						pass = false;
						break;
					}
					if (!CollectionUtils.isEmpty(request.getFilter().get(key)) && !request.getFilter().get(key).contains(demand.getCustomFields().get(key).toString())) {
						pass = false;
						break;
					}
				}
				return pass;
			}).collect(Collectors.toList());
		}
		// filter by ids
		if (!CollectionUtils.isEmpty(filterIds)) {
			filterDemands = filterDemands.stream().filter(demand -> filterIds.contains(demand.getDemandId())).collect(Collectors.toList());
		}
		if (!CollectionUtils.isEmpty(request.getExcludeIds()) && request.isSelectAll()) {
			filterDemands = filterDemands.stream().filter(demand -> !request.getExcludeIds().contains(demand.getDemandId())).collect(Collectors.toList());
		}
		return filterDemands;
	}

	/**
	 * 获取需求自定义字段
	 *
	 * @return 自定义字段集合
	 */
	private List<PlatformCustomFieldItemDTO> getDemandCustomField() {
		// prepare custom headers
		List<PlatformCustomFieldItemDTO> customHeaders = new ArrayList<>();
		// set plan to custom headers, and support search
		PlatformCustomFieldItemDTO iterationField = new PlatformCustomFieldItemDTO();
		iterationField.setId(ZentaoDemandCustomField.PLAN_FIELD_ID);
		iterationField.setName(ZentaoDemandCustomField.PLAN_FIELD_NAME);
		iterationField.setSupportSearch(true);
		if (StringUtils.equals(projectConfig.getType(), "products")) {
			// 产品计划
			iterationField.setOptions(PluginUtils.toJSONString(getProductPlanOption()));
		}
		customHeaders.add(iterationField);
		return customHeaders;
	}

	/**
	 * 获取产品计划的表头下拉选项
	 *
	 * @return 产品计划下拉选项
	 */
	private List<SelectOption> getProductPlanOption() {
		ZentaoRestPlanResponse response = zentaoRestClient.getProductPlans(projectConfig.getZentaoKey(), 1, Integer.MAX_VALUE);
		if (response == null || CollectionUtils.isEmpty(response.getPlans())) {
			return List.of();
		}
		List<ZentaoRestPlanResponse.Plan> plans = new ArrayList<>();
		response.getPlans().forEach(plan -> {
			plans.add(plan);
			if (!CollectionUtils.isEmpty(plan.getChildren())) {
				plans.addAll(plan.getChildren());
			}
		});
		return plans.stream().map(plan -> new SelectOption(plan.getTitle(), plan.getId())).collect(Collectors.toList());
	}

	/**
	 * 获取项目配置
	 *
	 * @param configStr 项目配置JSON
	 * @return 项目配置对象
	 */
	private ZentaoProjectConfig getProjectConfig(String configStr) {
		if (StringUtils.isBlank(configStr)) {
			throw new MSPluginException("请在项目中添加禅道Key相关的配置!");
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
		ZentaoRestUserResponse users = zentaoRestClient.getUsers();
		return users.getUsers().stream().map(user -> new SelectOption(user.getRealname(), user.getAccount())).collect(Collectors.toList());
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
		msBug.setDescription(parseZentaoPicToMsRichText(zenBug.getSteps(), msBug));
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
				msBug.setCreateTime(sdfDateTime.parse(openedDate).getTime());
			} else {
				msBug.setCreateTime(System.currentTimeMillis());
			}
			if (StringUtils.isNotBlank(lastEditedDate) && !lastEditedDate.startsWith(DATE_PREFIX)) {
				msBug.setUpdateTime(sdfDateTime.parse(openedDate).getTime());
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
						if (CollectionUtils.isEmpty((List<?>) value)) {
							field.setValue(null);
						} else {
							List<Object> values = new ArrayList<>();
							((List<?>) value).forEach(attr -> {
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
		// noinspection unchecked
		Map<String, Object> valObj = (Map<String, Object>) value;
		// noinspection unchecked
		Map<String, Object> child = (Map<String, Object>) valObj.get("child");
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
	private List<Object> getCascadeValues(String idValue, Map<String, Object> child) {
		List<Object> values = new ArrayList<>();
		if (StringUtils.isNotBlank(idValue)) {
			values.add(idValue);
		}
		if (child.get("id") != null && StringUtils.isNotBlank(child.get("id").toString())) {
			values.add(child.get("id"));
		}
		return values;
	}

	/**
	 * 根据同步参数过滤缺陷集合
	 *
	 * @param zentaoBugs 禅道缺陷集合
	 * @param request    同步全量参数
	 * @return 过滤后的缺陷集合
	 */
	private List<?> filterBySyncCondition(List<?> zentaoBugs, SyncAllBugRequest request) {
		if (request.getPre() == null || request.getCreateTime() == null) {
			return zentaoBugs;
		}
		return zentaoBugs.stream().filter(bug -> {
			// noinspection unchecked
			Map<String, Object> bugMap = (Map<String, Object>) bug;
			long createTimeMills;
			try {
				createTimeMills = sdfDateTime.parse(bugMap.get("openedDate").toString()).getTime();
				if (request.getPre()) {
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
	public void parseAttachmentOrBuildToMsBug(SyncBugResult syncResult, PlatformBugDTO bug) {
		try {
			ZentaoRestBugDetailResponse response = zentaoRestClient.get(bug.getPlatformBugId());
			if (!CollectionUtils.isEmpty(response.getOpenedBuild())) {
				List<String> buildIds = new ArrayList<>();
				response.getOpenedBuild().forEach(build -> buildIds.add("\"" + build.get("id") + "\""));
				bug.getCustomFieldList().forEach(field -> {
					if (StringUtils.equals(field.getId(), "openedBuild")) {
						field.setValue(buildIds);
					}
				});
			}
			Object files = response.getFiles();
			Map<String, List<PlatformAttachment>> attachmentMap = syncResult.getAttachmentMap();
			attachmentMap.put(bug.getId(), new ArrayList<>());
			if (files instanceof Map) {
				// noinspection unchecked
				Map<String, LinkedHashMap<String, Object>> zenFiles = (Map<String, LinkedHashMap<String, Object>>) files;
				if (!CollectionUtils.isEmpty(zenFiles)) {
					for (String fileId : zenFiles.keySet()) {
						LinkedHashMap<String, Object> zenFileMap = zenFiles.get(fileId);
						PlatformAttachment syncAttachment = new PlatformAttachment();
						// key for get attachment content
						syncAttachment.setFileKey(zenFileMap.get("id").toString());
						// name for check
						syncAttachment.setFileName(zenFileMap.get("title").toString());
						attachmentMap.get(bug.getId()).add(syncAttachment);
					}
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
	 * @return 参数
	 */
	private ZentaoRestBugEditRequest buildUpdateParam(PlatformBugUpdateRequest request, PlatformBugUpdateDTO platformBug) {
		ZentaoRestBugEditRequest zentaoEditParam = new ZentaoRestBugEditRequest();
		zentaoEditParam.setTitle(request.getTitle());
		// 目前只处理禅道步骤内的图片文本
		zentaoEditParam.setSteps(parseRichTextPicToZentao(request.getDescription(), projectConfig.getZentaoKey(), request.getRichFileMap(), platformBug));
		parseCustomFields(request, zentaoEditParam, platformBug);
		return zentaoEditParam;
	}

	/**
	 * 解析自定义字段
	 *
	 * @param request         请求参数
	 * @param zentaoEditParam 参数
	 */
	protected void parseCustomFields(PlatformBugUpdateRequest request, ZentaoRestBugEditRequest zentaoEditParam, PlatformBugUpdateDTO platformBug) {
		try {
			List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
			if (!CollectionUtils.isEmpty(customFields)) {
				for (PlatformCustomFieldItemDTO item : customFields) {
					if (StringUtils.isNotBlank(item.getCustomData())) {
						if (StringUtils.equals(item.getCustomData(), "assignedTo")) {
							// 指派给
							platformBug.setPlatformHandleUser(item.getValue().toString());
							zentaoEditParam.setAssignedTo(item.getValue().toString());
						} else if (StringUtils.equalsAnyIgnoreCase(item.getType(), PlatformCustomFieldType.MULTIPLE_SELECT.name())) {
							// 多选字段
							PluginBeanUtils.setFieldValueByName(zentaoEditParam, item.getCustomData(), PluginUtils.parseArray(item.getValue().toString(), String.class), List.class);
						} else {
							// 其他字段
							PluginBeanUtils.setFieldValueByName(zentaoEditParam, item.getCustomData(), item.getValue(), item.getValue().getClass());
						}
					}
				}
			}
		} catch (Exception e) {
			throw new MSPluginException("解析禅道自定义字段失败: " + e.getMessage());
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
	 * 解析不同状态项, 流转状态
	 *
	 * @param status 状态
	 */
	private void transitionStatus(PlatformCustomFieldItemDTO status, String zentaoKey, String assignedTo) {
		if (status != null) {
			if (StringUtils.equals(status.getValue().toString(), ZentaoBugPlatformStatus.resolved.name())) {
				zentaoRestClient.resolveBug(zentaoKey, assignedTo);
			} else if (StringUtils.equals(status.getValue().toString(), ZentaoBugPlatformStatus.closed.name())) {
				zentaoRestClient.closeBug(zentaoKey);
			} else {
				zentaoRestClient.activeBug(zentaoKey, assignedTo);
			}
		}
	}

	private String parseRichTextPicToZentao(String content, String projectKey, Map<String, File> msFileMap, PlatformBugUpdateDTO platformBug) {
		// psrc => src
		if (content.contains("psrc")) {
			// eg: <img psrc="/file-read-zFid.png" src=/bug/attachment/preview/md/pid/fid/true">
			// => <img src="/file-read-zFid.png" src="/bug/attachment/preview/md/pid/fid/true"/>
			// 图片双向同步过, 直接替换URL即可
			content = content.replaceAll("psrc", "src");
		}
		if (!CollectionUtils.isEmpty(msFileMap)) {
			for (String key : msFileMap.keySet()) {
				if (content.contains("permalinksrc")) {
					// eg: <img src="/attachment/download/file/pid/fid/true" permalinksrc="/attachment/download/file/pid/fid/true">
					// => <img src="/file-read-zFid.png" alt="/attachment/download/file/pid/fid/true"/>
					// 还未双向同步的图片, 上传附件(图片)至禅道
					String fileId = zentaoClient.uploadFile(msFileMap.get(key), "bug", projectKey);
					// 替换的目标禅道URL
					String zentaoImgUrl = "<img src=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX + fileId + ".jpg";
					// 替换的源MS-URL正则
					String sourceRegex = "(<img src=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX + "/)(\\d+)(/" + key + "/true)";
					// 保留permalinksrc链接, 同步至MS时备用
					content = content.replaceAll(sourceRegex, zentaoImgUrl);
				}
			}
			content = content.replaceAll("permalinksrc", "alt");
		}
		// 保留MS-URL中的一些参数{src}
		content = content.replaceAll("src=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX, "alt=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX);

		// MS-URL, 需同步修改为禅道可识别的URL
		String msUrl = content.replaceAll("src=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX, "psrc=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX)
				.replaceAll("alt=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX, "src=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX);
		platformBug.setPlatformDescription(msUrl);
		// 图片链接中存在HTTP-URL, 不用替换
		return content;
	}

	private String parseZentaoPicToMsRichText(String content, PlatformBugDTO msBug) {
		// 图片链接中存在本地上传的URL, 及已经双向同步的URL, 网络链接的URL
		// eg: <img src="/file-read-zFid.png" alt="/attachment/download/file/pid/fid/true" 需处理, 已双向同步无需下载
		// eg: <img src="/file-read-51.jpg" alt /> 需替换图片URL, 并提供下载流, 供MS下载
		// eg: <img src="https.pic.s" alt /> 不用处理
		if (StringUtils.isBlank(content)) {
			return null;
		}
		content = content
				.replaceAll("<img src=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX, "<img psrc=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX)
				.replaceAll("<img src=\"\\{", "<img psrc=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX).replaceAll("}", StringUtils.EMPTY)
				.replaceAll("alt=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX, "src=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX);
		String zentaoLocalRegex = "(<img psrc=\"" + ZENTAO_RICH_TEXT_IMG_SRC_PREFIX + ")(.*?)(alt=\"\" />)";
		Matcher matcher = Pattern.compile(zentaoLocalRegex).matcher(content);
		Map<String, String> richFileMap = new HashMap<>(16);
		while (matcher.find()) {
			String matchLocalFileUrl = matcher.group(0);
			String fileRegex = "\\d+";
			Matcher fileMatch = Pattern.compile(fileRegex).matcher(matchLocalFileUrl);
			while (fileMatch.find()) {
				String fileId = fileMatch.group(0);
				String replaceTmpUrl = matchLocalFileUrl.replaceAll("alt=\"\" />", "alt=\"" + fileId + "\" />");
				content = content.replaceAll(matchLocalFileUrl, replaceTmpUrl);
				// 禅道富文本中的图片默认命名为*.jpg, *:唯一文件ID, 标识, 整数
				richFileMap.put(fileId, fileId + ".jpg");
			}
		}
		msBug.setRichTextImageMap(richFileMap);
		return content;
	}
}
