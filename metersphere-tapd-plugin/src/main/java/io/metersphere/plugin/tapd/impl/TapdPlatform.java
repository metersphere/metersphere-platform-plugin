package io.metersphere.plugin.tapd.impl;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.dto.response.PlatformBugDTO;
import io.metersphere.plugin.platform.dto.response.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.response.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.response.PlatformDemandDTO;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.tapd.client.TapdClient;
import io.metersphere.plugin.tapd.constants.TapdSystemType;
import io.metersphere.plugin.tapd.domain.TapdIntegrationConfig;
import io.metersphere.plugin.tapd.domain.TapdProject;
import io.metersphere.plugin.tapd.domain.TapdProjectConfig;
import io.metersphere.plugin.tapd.domain.TapdUserPlatformInfo;
import io.metersphere.plugin.tapd.domain.response.TapdBugResponse;
import io.metersphere.plugin.tapd.domain.response.TapdStoryResponse;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author song-cc-rock
 */
@Extension
@SuppressWarnings("unused")
public class TapdPlatform extends AbstractPlatform {

	protected TapdClient tapdClient;

	protected static final String MS_RICH_TEXT_PREVIEW_SRC_PREFIX = "/bug/attachment/preview/md";

	protected static final String TAPD_RICH_TEXT_PIC_SRC_PREFIX = "/tfl";

	protected static final String MS_RICH_TEXT_PIC_KEY_WORD = "permalinksrc";

	protected static final String SEMICOLON = ";";

	protected SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@SuppressWarnings("unused")
	public TapdPlatform(PlatformRequest request) {
		super(request);
		TapdIntegrationConfig config = getIntegrationConfig(request.getIntegrationConfig(), TapdIntegrationConfig.class);
		tapdClient = new TapdClient(config);
	}

	/**
	 * 校验集成配置
	 */
	@Override
	public void validateIntegrationConfig() {
		tapdClient.auth();
	}

	/**
	 * 校验用户配置
	 *
	 * @param userConfig 用户配置
	 */
	@Override
	public void validateUserConfig(String userConfig) {
		TapdUserPlatformInfo platformConfig = PluginUtils.parseObject(userConfig, TapdUserPlatformInfo.class);
		if (StringUtils.isBlank(platformConfig.getTapdAccount()) && StringUtils.isBlank(platformConfig.getTapdPassword())) {
			throw new MSPluginException("TAPD认证失败: 账号或密码为空");
		}
		setUserConfig(userConfig, true);
		tapdClient.auth();
	}

	/**
	 * 校验项目配置
	 *
	 * @param projectConfigStr 项目配置信息
	 */
	@Override
	public void validateProjectConfig(String projectConfigStr) {
		try {
			TapdProjectConfig projectConfig = getProjectConfig(projectConfigStr);
			if (StringUtils.isBlank(projectConfig.getTapdKey())) {
				throw new MSPluginException("TAPD项目校验参数不能为空!");
			}
			TapdProject project = tapdClient.getProject(projectConfig.getTapdKey());
			if (project == null || StringUtils.isBlank(project.getId())) {
				throw new MSPluginException("项目不存在");
			}
		} catch (Exception e) {
			throw new MSPluginException(e.getMessage());
		}
	}

	/**
	 * 是否支持第三方模板
	 *
	 * @return 支持第三方模板的平台才会在MS平台存在默认模板
	 */
	@Override
	public boolean isSupportDefaultTemplate() {
		// Tapd currently does not support default templates
		return false;
	}

	/**
	 * 获取第三方平台缺陷的自定义字段
	 *
	 * @param projectConfig 项目配置信息
	 * @return 自定义字段集合
	 */
	@Override
	public List<PlatformCustomFieldItemDTO> getDefaultTemplateCustomField(String projectConfig) {
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
	 * 获取状态工作流
	 *
	 * @param projectConfig  项目配置
	 * @param issueKey       缺陷ID
	 * @param previousStatus 当前状态
	 * @return 状态流选项
	 */
	@Override
	public List<SelectOption> getStatusTransitions(String projectConfig, String issueKey, String previousStatus) {
		TapdProjectConfig config = getProjectConfig(projectConfig);
		List<SelectOption> statusOptions = new ArrayList<>();
		if (StringUtils.isBlank(issueKey)) {
			// issueKey is null, get previous transitions
			SelectOption firstStepWorkFlow = tapdClient.getFirstStepWorkFlow(TapdSystemType.BUG, config.getTapdKey(), null);
			statusOptions.add(firstStepWorkFlow);
		} else {
			statusOptions.addAll(tapdClient.getWorkFlowTransition(TapdSystemType.BUG, config.getTapdKey(), previousStatus));
		}
		return statusOptions;
	}

	/**
	 * 需求分页查询
	 *
	 * @param request 请求参数
	 * @return 需求列表
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
			return new PluginPager<>(demandRelatePageData, total, Integer.MAX_VALUE, request.getStartPage());
		} else {
			// pager
			demands = demands.stream().skip((long) (request.getStartPage() - 1) * request.getPageSize()).limit(request.getPageSize()).collect(Collectors.toList());
			// set demand response
			PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
			demandRelatePageData.setList(demands);
			return new PluginPager<>(demandRelatePageData, total, request.getPageSize(), request.getStartPage());
		}
	}

	/**
	 * 根据ID获取需求
	 *
	 * @param request 请求参数
	 * @return 需求结果
	 */
	@Override
	public PlatformDemandDTO getDemands(DemandRelateQueryRequest request) {
		DemandPageRequest requestParam = new DemandPageRequest();
		requestParam.setProjectConfig(request.getProjectConfig());
		List<PlatformDemandDTO.Demand> demands = queryDemandList(requestParam, request.getRelateDemandIds());
		// set demand response
		PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
		demandRelatePageData.setList(demands);
		return demandRelatePageData;
	}

	/**
	 * 新增缺陷
	 *
	 * @param request 请求参数
	 * @return 平台缺陷内容
	 */
	@Override
	public PlatformBugUpdateDTO addBug(PlatformBugUpdateRequest request) {
		return editBug(request, false);
	}

	/**
	 * 更新缺陷
	 *
	 * @param request 请求参数
	 * @return 平台缺陷内容
	 */
	@Override
	public PlatformBugUpdateDTO updateBug(PlatformBugUpdateRequest request) {
		return editBug(request, true);
	}

	/**
	 * 编辑缺陷
	 *
	 * @param request  请求参数
	 * @param isUpdate 是否更新操作
	 * @return 平台缺陷内容
	 */
	private PlatformBugUpdateDTO editBug(PlatformBugUpdateRequest request, boolean isUpdate) {
		// validate config
		TapdProjectConfig config = validateAndSetUserConfig(request.getUserPlatformConfig(), request.getProjectConfig());
		// prepare and init tapd param
		PlatformBugUpdateDTO platformBug = new PlatformBugUpdateDTO();
		// filter status field
		PlatformCustomFieldItemDTO statusField = filterStatusTransition(request);
		// set param
		MultiValueMap<String, Object> editParam = buildUpdateParam(request, platformBug);
		if (statusField != null) {
			editParam.add("status", statusField.getValue());
		}
		if (isUpdate) {
			editParam.add("id", request.getPlatformBugId());
		}
		TapdBugResponse tapdBug = tapdClient.editBug(editParam, config.getTapdKey());
		if (tapdBug != null && StringUtils.isNotBlank(tapdBug.getId())) {
			platformBug.setPlatformBugKey(tapdBug.getId());
			if (statusField != null) {
				platformBug.setPlatformStatus(statusField.getValue().toString());
			}
		} else {
			throw new MSPluginException(isUpdate ? "更新Tapd缺陷失败!" : "创建Tapd缺陷失败!");
		}

		return platformBug;
	}

	@Override
	public void deleteBug(PlatformBugDeleteRequest request) {
		// TODO: Tapd-API currently does not support delete bug
	}

	@Override
	public boolean isSupportAttachment() {
		// TODO: Tapd-API currently does not support attachment upload or delete
		// https://o.tapd.cn/document/api-doc/API%E6%96%87%E6%A1%A3/api_reference/attachment/get_attachments.html
		return false;
	}

	@Override
	public void syncAttachmentToPlatform(SyncAttachmentToPlatformRequest request) {
		// TODO: when isSupportAttachment get true, implement this method;
	}

	/**
	 * 同步存量缺陷
	 *
	 * @param request 同步请求参数
	 * @return 同步结果
	 */
	@Override
	public SyncBugResult syncBugs(SyncBugRequest request) {
		// validate config
		TapdProjectConfig config = validateConfig(request.getProjectConfig());

		// prepare param
		SyncBugResult syncResult = new SyncBugResult();
		List<PlatformBugDTO> bugs = request.getBugs();

		// query bug list by page
		int page = 1, limit = 200, querySize;
		List<Map> totalQueryBugs = new ArrayList<>();
		do {
			List<Map> queryPageBugs = tapdClient.getBugForPage(config.getTapdKey(), page, limit);
			querySize = queryPageBugs.size();
			if (querySize > 0) {
				totalQueryBugs.addAll(queryPageBugs);
			}
			page++;
		} while (querySize >= limit);

		Map<String, Map> queryBugMap = new HashMap<>(16);
		totalQueryBugs.forEach(queryBug -> queryBugMap.put(queryBug.get("id").toString(), queryBug));
		// Handle bug that require sync
		bugs.forEach(bug -> {
			Map findBug = queryBugMap.get(bug.getPlatformBugId());
			if (findBug != null) {
				syncTapdFieldToMsBug(bug, findBug, false, config.getTapdKey());
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
		TapdProjectConfig config = validateConfig(request.getProjectConfig());

		// prepare page param
		int page = 1, limit = 200, querySize;
		try {
			do {
				// prepare post process func param
				List<PlatformBugDTO> needSyncBugs = new ArrayList<>();
				SyncBugResult syncBugResult = new SyncBugResult();

				// query tapd bug by page
				List<Map> tapdBugs = tapdClient.getBugForPage(config.getTapdKey(), page, limit);
				querySize = tapdBugs.size();
				tapdBugs = filterBySyncCondition(tapdBugs, request);
				if (!CollectionUtils.isEmpty(tapdBugs)) {
					for (Map bugMap : tapdBugs) {
						// transfer tapd bug field to ms
						PlatformBugDTO bug = new PlatformBugDTO();
						bug.setId(UUID.randomUUID().toString());
						bug.setPlatformBugId(bugMap.get("id").toString());
						syncTapdFieldToMsBug(bug, bugMap, true, config.getTapdKey());
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
				page++;
			} while (querySize >= limit);
		} catch (Exception e) {
			PluginLogUtils.error(e);
			throw new MSPluginException(e);
		}
	}

	/**
	 * 获取附件下载流
	 *
	 * @param fileKey            文件Key
	 * @param inputStreamHandler 文件流处理
	 */
	@Override
	public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
		tapdClient.getAttachmentBytes(fileKey, inputStreamHandler);
	}

	/**
	 * 根据同步参数过滤缺陷集合
	 *
	 * @param tapdBugs tapd缺陷集合
	 * @param request  同步全量参数
	 * @return 过滤后的缺陷集合
	 */
	private List<Map> filterBySyncCondition(List<Map> tapdBugs, SyncAllBugRequest request) {
		if (request.getPre() == null || request.getCreateTime() == null) {
			return tapdBugs;
		}
		return tapdBugs.stream().filter(bug -> {
			long createTimeMills;
			try {
				createTimeMills = sdfDateTime.parse(bug.get("created").toString()).getTime();
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
	 * 同步Tapd缺陷字段 => MS字段值
	 *
	 * @param msBug              MS缺陷
	 * @param tapdBug            Tapd缺陷
	 * @param useCustomAllFields 是否同步全量自定义字段
	 */
	private void syncTapdFieldToMsBug(PlatformBugDTO msBug, Map tapdBug, boolean useCustomAllFields, String projectKey) {
		try {
			// 处理基础字段
			parseBaseFieldToMsBug(msBug, tapdBug, projectKey);
			// 处理自定义字段
			parseCustomFieldToMsBug(msBug, tapdBug, useCustomAllFields);
		} catch (Exception e) {
			PluginLogUtils.error(e);
		}
	}

	/**
	 * 解析基础字段到平台缺陷字段
	 *
	 * @param msBug       平台缺陷
	 * @param tapdBugInfo Tapd缺陷内容
	 * @param projectKey  项目Key
	 */
	private void parseBaseFieldToMsBug(PlatformBugDTO msBug, Map tapdBugInfo, String projectKey) {
		// 处理基础字段(TITLE, DESCRIPTION, HANDLE_USER, STATUS)
		msBug.setTitle(tapdBugInfo.get("title") == null ? StringUtils.EMPTY : tapdBugInfo.get("title").toString());
		msBug.setDescription(parseTapdPicToMsRichText(tapdBugInfo.get("description") == null ?
				StringUtils.EMPTY : tapdBugInfo.get("description").toString(), msBug, projectKey));
		Object ownerObj = tapdBugInfo.get("current_owner");
		if (ownerObj == null || StringUtils.isBlank(ownerObj.toString())) {
			msBug.setHandleUser(StringUtils.EMPTY);
		} else {
			String ownerStr;
			if (ownerObj.toString().contains(SEMICOLON)) {
				ownerStr = List.of(ownerObj.toString().split(";")).getFirst();
			} else {
				ownerStr = ownerObj.toString();
			}
			if (!StringUtils.equals(msBug.getHandleUser(), ownerStr)) {
				msBug.setHandleUser(ownerStr);
				msBug.setHandleUsers(StringUtils.isBlank(msBug.getHandleUsers()) ? ownerStr : msBug.getHandleUsers() + "," + ownerStr);
			}
		}
		msBug.setStatus(tapdBugInfo.get("status") == null ? null : tapdBugInfo.get("status").toString());
		msBug.setCreateUser("admin");
		msBug.setUpdateUser("admin");
		try {
			String created = tapdBugInfo.get("created").toString();
			String modified = tapdBugInfo.get("modified") == null ? StringUtils.EMPTY : tapdBugInfo.get("modified").toString();
			if (StringUtils.isNotBlank(created)) {
				msBug.setCreateTime(sdfDateTime.parse(created).getTime());
			} else {
				msBug.setCreateTime(System.currentTimeMillis());
			}
			if (StringUtils.isNotBlank(modified)) {
				msBug.setUpdateTime(sdfDateTime.parse(modified).getTime());
			} else {
				msBug.setUpdateTime(System.currentTimeMillis());
			}
		} catch (Exception e) {
			throw new MSPluginException("parse tapd bug time error: " + e.getMessage());
		}
	}

	/**
	 * 解析自定义字段到平台缺陷字段
	 *
	 * @param msBug              平台缺陷
	 * @param tapdBugInfo        Tapd缺陷内容
	 * @param useCustomAllFields 是否同步全量自定义字段
	 */
	@SuppressWarnings("unchecked")
	private void parseCustomFieldToMsBug(PlatformBugDTO msBug, Map tapdBugInfo, boolean useCustomAllFields) {
		List<PlatformCustomFieldItemDTO> needSyncCustomFields = new ArrayList<>();
		if (useCustomAllFields) {
			// 同步全量的时候, 需要同步所有自定义字段
			tapdBugInfo.keySet().forEach(fieldKey -> {
				PlatformCustomFieldItemDTO field = new PlatformCustomFieldItemDTO();
				field.setId(fieldKey.toString());
				field.setValue(tapdBugInfo.get(fieldKey));
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
			needSyncCustomFields.forEach(field -> field.setValue(tapdBugInfo.get(field.getCustomData())));
		}
		msBug.setCustomFieldList(needSyncCustomFields);
	}

	/**
	 * 生成新增, 更新参数
	 *
	 * @param request     请求参数
	 * @param platformBug 平台缺陷
	 * @return 参数
	 */
	private MultiValueMap<String, Object> buildUpdateParam(PlatformBugUpdateRequest request, PlatformBugUpdateDTO platformBug) {
		MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
		paramMap.add("title", request.getTitle());
		paramMap.add("description", parseRichTextPicToTapd(request.getDescription(), platformBug));
		parseCustomFields(request, paramMap, platformBug);
		return paramMap;
	}

	/**
	 * 解析自定义字段
	 *
	 * @param request       请求参数
	 * @param tapdEditParam 参数
	 * @param platformBug   平台缺陷
	 */
	protected void parseCustomFields(PlatformBugUpdateRequest request, MultiValueMap<String, Object> tapdEditParam, PlatformBugUpdateDTO platformBug) {
		try {
			List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
			if (!CollectionUtils.isEmpty(customFields)) {
				for (PlatformCustomFieldItemDTO item : customFields) {
					if (StringUtils.isNotBlank(item.getCustomData())) {
						if (StringUtils.equals(item.getCustomData(), "currentOwner")) {
							// Tapd处理人/创建人
							platformBug.setPlatformHandleUser(item.getValue().toString());
							tapdEditParam.add("current_owner", item.getValue());
						} else {
							// 其他字段
							tapdEditParam.add(item.getCustomData(), item.getValue());
						}
					}
				}
			}
		} catch (Exception e) {
			throw new MSPluginException("解析Tapd自定义字段失败: " + e.getMessage());
		}
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
		TapdProjectConfig config = validateConfig(request.getProjectConfig());

		// query demand list no limit
		List<TapdStoryResponse> storyList = tapdClient.getProjectStories(config.getTapdKey(), request.getStartPage(), Integer.MAX_VALUE);
		// handle empty data
		if (CollectionUtils.isEmpty(storyList)) {
			return List.of();
		}

		// prepare demand list
		List<PlatformDemandDTO.Demand> demands = new ArrayList<>();
		storyList.forEach(story -> {
			PlatformDemandDTO.Demand demand = new PlatformDemandDTO.Demand();
			demand.setDemandId(story.getId());
			demand.setDemandName(story.getName());
			demand.setDemandUrl(tapdClient.getBaseUrl() + "/" + config.getTapdKey() + "/prong/stories/view/" + story.getId());
			boolean isParentDemandShow = StringUtils.isBlank(request.getQuery()) || StringUtils.containsIgnoreCase(demand.getDemandName(), request.getQuery()) || StringUtils.containsIgnoreCase(demand.getDemandId(), request.getQuery()) &&
					(CollectionUtils.isEmpty(request.getExcludeIds()) || !request.getExcludeIds().contains(demand.getDemandId()));
			if (!CollectionUtils.isEmpty(story.getChildren())) {
				List<PlatformDemandDTO.Demand> childrenDemands = new ArrayList<>();
				// handle children demand list
				story.getChildren().forEach(childStory -> {
					PlatformDemandDTO.Demand childDemand = new PlatformDemandDTO.Demand();
					childDemand.setDemandId(childStory.getId());
					childDemand.setDemandName(childStory.getName());
					childDemand.setDemandUrl(tapdClient.getBaseUrl() + "/" + config.getTapdKey() + "/prong/stories/view/" + childStory.getId());
					childDemand.setParent(demand.getDemandId());
					boolean isChildDemandShow = StringUtils.isBlank(request.getQuery()) || StringUtils.containsIgnoreCase(childDemand.getDemandName(), request.getQuery()) || StringUtils.equalsIgnoreCase(childDemand.getDemandId(), request.getQuery()) &&
							(CollectionUtils.isEmpty(request.getExcludeIds()) || !request.getExcludeIds().contains(demand.getDemandId()));
					if (isChildDemandShow) {
						// When child story meet the condition, show it
						childrenDemands.add(childDemand);
					}
				});
				demand.setChildren(childrenDemands);
			}
			if (isParentDemandShow || !CollectionUtils.isEmpty(demand.getChildren())) {
				// When parent story meet the condition, it's child story meet the condition, show the story
				demands.add(demand);
			}
		});
		// sort by demand id
		demands.sort(Comparator.comparing(PlatformDemandDTO.Demand::getDemandId));
		// filter by condition
		List<PlatformDemandDTO.Demand> filterDemands = demands;
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
	 * 表单反射调用
	 *
	 * @param request 表单项请求参数
	 * @return 用户下拉选项
	 */
	@SuppressWarnings("unused")
	public List<SelectOption> getOwnerList(GetOptionRequest request) {
		TapdProjectConfig config = getProjectConfig(request.getProjectConfig());
		return tapdClient.getProjectUsers(config.getTapdKey());
	}

	/**
	 * 设置用户平台配置(集成信息)
	 *
	 * @param userPlatformConfig 用户平台配置
	 */
	public void setUserConfig(String userPlatformConfig, Boolean isUserConfig) {
		TapdIntegrationConfig integrationConfig;
		if (isUserConfig) {
			// 如果是用户配置, 则直接从平台参数中获取集成信息, 并替换用户账号配置
			integrationConfig = getIntegrationConfig(TapdIntegrationConfig.class);
			TapdUserPlatformInfo userConfig = PluginUtils.parseObject(userPlatformConfig, TapdUserPlatformInfo.class);
			if (userConfig != null) {
				integrationConfig.setAccount(userConfig.getTapdAccount());
				integrationConfig.setPassword(userConfig.getTapdPassword());
			}
		} else {
			// 如果是集成配置, 则直接从参数中获取集成信息
			integrationConfig = getIntegrationConfig(userPlatformConfig, TapdIntegrationConfig.class);
		}
		validateAndSetConfig(integrationConfig);
	}

	/**
	 * 校验并设置集成配置
	 *
	 * @param config 集成配置
	 */
	private void validateAndSetConfig(TapdIntegrationConfig config) {
		tapdClient.initConfig(config);
	}

	/**
	 * 获取项目配置
	 *
	 * @param configStr 项目配置JSON
	 * @return 项目配置对象
	 */
	private TapdProjectConfig getProjectConfig(String configStr) {
		if (StringUtils.isBlank(configStr)) {
			throw new MSPluginException("请在项目中添加项目配置！");
		}
		return PluginUtils.parseObject(configStr, TapdProjectConfig.class);
	}

	/**
	 * 校验并设置用户配置
	 *
	 * @param userPlatformConfig 用户配置
	 * @param projectConfig      项目配置
	 * @return 项目配置
	 */
	private TapdProjectConfig validateAndSetUserConfig(String userPlatformConfig, String projectConfig) {
		setUserConfig(userPlatformConfig, true);
		return validateConfig(projectConfig);
	}

	/**
	 * 校验配置
	 *
	 * @param projectConfig 项目配置
	 */
	private TapdProjectConfig validateConfig(String projectConfig) {
		TapdProjectConfig config = getProjectConfig(projectConfig);
		if (StringUtils.isBlank(config.getTapdKey())) {
			throw new MSPluginException("请在项目中配置Tapd的项目Key!");
		}
		return config;
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
			return statusList.getFirst();
		} else {
			return null;
		}
	}

	/**
	 * 解析MS富文本图片内容至Tapd
	 *
	 * @param content     富文本内容
	 * @param platformBug 平台缺陷内容
	 * @return 解析后的内容
	 */
	private String parseRichTextPicToTapd(String content, PlatformBugUpdateDTO platformBug) {
		if (StringUtils.isBlank(content)) {
			return null;
		}
		platformBug.setPlatformDescription(content);
		if (StringUtils.contains(content, MS_RICH_TEXT_PIC_KEY_WORD)) {
			// 不保留permalinksrc链接, 不支持双向同步
			String permalinkRegex = "(permalinksrc=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX + "/)(\\d+)(/(\\d+)" + "/true)";
			content = content.replaceAll(permalinkRegex, "alt =\"暂不支持图片同步");
		}
		// 图片链接中存在HTTP-URL, 不用替换
		return content;
	}

	/**
	 * 解析Tapd富文本图片至MS
	 *
	 * @param content 富文本内容
	 * @param msBug   MS缺陷
	 * @return 解析后的内容
	 */
	private String parseTapdPicToMsRichText(String content, PlatformBugDTO msBug, String projectKey) {
		// 图片链接中存在本地上传的URL, 及已经双向同步的URL, 网络链接的URL
		// eg: <img src="/base-url/attachment/download/file/pid/fid/true" alt="/attachment/download/file/pid/fid/true" 需处理, 已双向同步无需下载
		// eg: <img src="/tfl/*" alt /> Tapd本地上传的图片, 获取下载URL
		// eg: <img src="https.pic.s" alt /> 不用处理
		if (StringUtils.isBlank(content)) {
			return null;
		}
		try {
			String[] splitStr = content.split("<img");
			Map<String, String> richFileMap = new HashMap<>(16);
			for (String imgStr : splitStr) {
				if (imgStr.contains(TAPD_RICH_TEXT_PIC_SRC_PREFIX)) {
					String tapdUrlKey = imgStr.substring(imgStr.indexOf("src=\"") + 5, imgStr.indexOf("\" "));
					String picTmpDownUrl = tapdClient.getPicTmpDownUrl(projectKey, tapdUrlKey);
					if (StringUtils.isNotBlank(picTmpDownUrl)) {
						String replaceTmpUrl = imgStr.replaceAll("src", "psrc").replaceAll("/>", "alt=\"" + picTmpDownUrl + "\" />");
						content = content.replaceAll(imgStr, replaceTmpUrl);
						// Tapd的图片默认命名为*.jpg, *: 唯一文件ID, 标识, 整数
						richFileMap.put(picTmpDownUrl, UUID.randomUUID() + ".jpg");
					}
				}
			}
			msBug.setRichTextImageMap(richFileMap);
			return content;
		} catch (Exception e) {
			PluginLogUtils.error("Parse tapd bug description error: " + e.getMessage());
		}
		return null;
	}
}
