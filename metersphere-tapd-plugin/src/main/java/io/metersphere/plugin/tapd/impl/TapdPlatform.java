package io.metersphere.plugin.tapd.impl;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.dto.response.PlatformBugDTO;
import io.metersphere.plugin.platform.dto.response.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.response.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.response.PlatformDemandDTO;
import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.tapd.client.TapdClient;
import io.metersphere.plugin.tapd.constants.TapdSpecialCustomField;
import io.metersphere.plugin.tapd.constants.TapdSystemType;
import io.metersphere.plugin.tapd.constants.TapdTemplateSystemField;
import io.metersphere.plugin.tapd.domain.*;
import io.metersphere.plugin.tapd.domain.response.TapdBugResponse;
import io.metersphere.plugin.tapd.domain.response.TapdStoryResponse;
import io.metersphere.plugin.tapd.enums.TapdFieldType;
import io.metersphere.plugin.tapd.enums.TapdOptionKey;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
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

	protected TapdProjectConfig projectConfig;

	protected static final String MS_RICH_TEXT_PREVIEW_SRC_PREFIX = "/bug/attachment/preview/md";

	protected static final String TAPD_RICH_TEXT_PIC_SRC_PREFIX = "/tfl";

	protected static final String MS_RICH_TEXT_PIC_KEY_WORD = "permalinksrc";

	protected static final String MS_RICH_TEXT_REPLACE_WORD = "psrc";

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
				throw new MSPluginException("TAPD项目Key不能为空!");
			}
			TapdProject project = tapdClient.getProject(projectConfig.getTapdKey());
			if (project == null || StringUtils.isBlank(project.getId())) {
				throw new MSPluginException("项目不存在!");
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
		return true;
	}

	/**
	 * 获取第三方平台缺陷的自定义字段
	 *
	 * @param projectConfigStr 项目配置信息
	 * @return 自定义字段集合
	 */
	@Override
	public List<PlatformCustomFieldItemDTO> getDefaultTemplateCustomField(String projectConfigStr) {
		// 项目配置校验
		projectConfig = getProjectConfig(projectConfigStr);
		// 准备一些选项值
		Map<String, String> optionData = prepareOptionData();
		// 获取默认模板
		Map<String, String> projectDefaultTemplate = tapdClient.getBugDefaultTemplate(projectConfig.getTapdKey());
		if (CollectionUtils.isEmpty(projectDefaultTemplate)) {
			return new ArrayList<>();
		}

		// 获取默认模板的字段及所有字段详情和待选值
		List<TapdTemplateField> defaultTemplateFields = tapdClient.getDefaultTemplateFields(projectDefaultTemplate.get("id"), projectConfig.getTapdKey());
		Map<String, TapdTemplateFieldDetail> allFieldsMap = tapdClient.getAllFieldsMap(projectConfig.getTapdKey());
		if (CollectionUtils.isEmpty(defaultTemplateFields) || CollectionUtils.isEmpty(allFieldsMap)) {
			return new ArrayList<>();
		}

		List<TapdTemplateField> sortFields = sortCustomField(defaultTemplateFields);
		List<PlatformCustomFieldItemDTO> fields = new ArrayList<>();
		Character filedKey = 'A';
		for (TapdTemplateField item : sortFields) {
			TapdTemplateFieldDetail fieldDetail = allFieldsMap.get(item.getField());
			if (fieldDetail == null) {
				continue;
			}
			PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
			setCustomFieldBaseProperty(item, fieldDetail, customField, filedKey);
			setCustomFieldTypeAndOption(fieldDetail, customField, optionData);
			setCustomFieldDefaultValue(item, customField, fieldDetail.getHtml_type());
			fields.add(customField);
			filedKey++;
		}

		// 类型为空的字段不展示
		fields = fields.stream().filter(i -> StringUtils.isNotBlank(i.getType())).collect(Collectors.toList());
		return fields;
	}

	/**
	 * 排序自定义字段
	 *
	 * @param fields 字段集合
	 * @return 排序后的字段集合
	 */
	private List<TapdTemplateField> sortCustomField(List<TapdTemplateField> fields) {
		// 默认按照sort字段排序 (title在最前面, 描述其次)
		fields.sort((a, b) -> {
			if (StringUtils.equals(a.getField(), TapdTemplateSystemField.DESCRIPTION)) {
				return 1;
			}
			if (StringUtils.equals(b.getField(), TapdTemplateSystemField.DESCRIPTION)) {
				return -1;
			}
			if (StringUtils.equals(a.getField(), TapdTemplateSystemField.TITLE)) {
				return 1;
			}
			if (StringUtils.equals(b.getField(), TapdTemplateSystemField.TITLE)) {
				return -1;
			}
			return a.getSort().compareTo(b.getSort());
		});
		return fields;
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
			throw new MSPluginException(e.getTargetException().getMessage());
		} catch (Exception e) {
			PluginLogUtils.error(e);
			throw new MSPluginException(e.getMessage());
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

	@Override
	public List<SelectOption> getStatusTransitionsLastSteps(String projectConfig) {
		TapdProjectConfig config = getProjectConfig(projectConfig);
		return tapdClient.getWorkFlowLastSteps(TapdSystemType.BUG, config.getTapdKey());
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
		TapdProjectConfig config = validateAndSetUserConfig(request.getProjectConfig());
		TapdUserPlatformInfo userPlatformInfo = getUserPlatformInfo(request.getUserPlatformConfig());
		// prepare and init tapd param
		PlatformBugUpdateDTO platformBug = new PlatformBugUpdateDTO();
		// filter status field
		PlatformCustomFieldItemDTO statusField = filterStatusTransition(request);
		// set param
		MultiValueMap<String, Object> editParam = buildUpdateParam(request, platformBug, userPlatformInfo);
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
		// TODO: Tapd-API currently does not support delete bug;
	}

	@Override
	public boolean isSupportAttachment() {
		// TODO: Tapd-API currently does not support attachment upload or delete;
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

		// 模板默认字段
		List<PlatformCustomFieldItemDTO> defaultTemplateCustomFields = getDefaultTemplateCustomField(request.getProjectConfig());
		// prepare param
		SyncBugResult syncResult = new SyncBugResult();
		List<PlatformBugDTO> bugs = request.getBugs();

		// query bug list by page
		int page = 1, limit = 200, querySize;
		List<Map> totalQueryBugs = new ArrayList<>();
		do {
			List<Map> queryPageBugs = tapdClient.getBugForPage(config.getTapdKey(), page, limit, null);
			page++;
			if (CollectionUtils.isEmpty(queryPageBugs)) {
				querySize = 0;
				continue;
			} else {
				querySize = queryPageBugs.size();
			}
			totalQueryBugs.addAll(queryPageBugs);
		} while (querySize >= limit);

		Map<String, Map> queryBugMap = new HashMap<>(16);
		totalQueryBugs.forEach(queryBug -> queryBugMap.put(queryBug.get("id").toString(), queryBug));
		// Handle bug that require sync
		bugs.forEach(bug -> {
			Map findBug = queryBugMap.get(bug.getPlatformBugId());
			if (findBug != null) {
				syncTapdFieldToMsBug(bug, findBug, defaultTemplateCustomFields, config.getTapdKey());
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
		// 模板默认字段
		List<PlatformCustomFieldItemDTO> defaultTemplateCustomFields = getDefaultTemplateCustomField(request.getProjectConfig());
		String createdQuery = getCreatedQuery(request);

		Integer total = tapdClient.getBugCount(config.getTapdKey(), createdQuery);
		// prepare page param
		int start = 1, limit = 200, size = total / limit + 1;
		try {
			do {
				// prepare post process func param
				List<PlatformBugDTO> needSyncBugs = new ArrayList<>();
				SyncBugResult syncBugResult = new SyncBugResult();

				// query tapd bug by page
				List<Map> tapdBugs = tapdClient.getBugForPage(config.getTapdKey(), start, limit, createdQuery);
				if (!CollectionUtils.isEmpty(tapdBugs)) {
					for (Map bugMap : tapdBugs) {
						// transfer tapd bug field to ms
						PlatformBugDTO bug = new PlatformBugDTO();
						bug.setId(UUID.randomUUID().toString());
						bug.setPlatformBugId(bugMap.get("id").toString());
						bug.setPlatformDefaultTemplate(true);
						syncTapdFieldToMsBug(bug, bugMap, defaultTemplateCustomFields, config.getTapdKey());
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
				start++;
			} while (start <= size);
		} catch (Exception e) {
			PluginLogUtils.warn(e);
			throw new MSPluginException(e.getMessage());
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
	 * @param request  同步全量参数
	 * @return 过滤后的缺陷集合
	 */
	private String getCreatedQuery(SyncAllBugRequest request) {
		if (request.getPre() == null || request.getCreateTime() == null) {
			return null;
		}
		if (request.getPre()) {
			return "created=<" + sdfDateTime.format(new Date(request.getCreateTime()));
		} else {
			return "created=>" + sdfDateTime.format(new Date(request.getCreateTime()));
		}
	}

	/**
	 * 同步Tapd缺陷字段 => MS字段值
	 *
	 * @param msBug              MS缺陷
	 * @param tapdBug            Tapd缺陷
	 * @param defaultTemplateFields 模板默认字段
	 * @param projectKey 	   项目Key
	 */
	private void syncTapdFieldToMsBug(PlatformBugDTO msBug, Map tapdBug, List<PlatformCustomFieldItemDTO> defaultTemplateFields, String projectKey) {
		try {
			// 处理基础字段
			parseBaseFieldToMsBug(msBug, tapdBug, projectKey);
			// 处理自定义字段
			parseCustomFieldToMsBug(msBug, tapdBug, defaultTemplateFields, projectKey);
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
		msBug.setTitle(tapdBugInfo.get(TapdTemplateSystemField.TITLE) == null ? StringUtils.EMPTY : tapdBugInfo.get(TapdTemplateSystemField.TITLE).toString());
		msBug.setDescription(parseTapdPicToMsRichText(tapdBugInfo.get(TapdTemplateSystemField.DESCRIPTION) == null ?
				StringUtils.EMPTY : tapdBugInfo.get(TapdTemplateSystemField.DESCRIPTION).toString(), msBug, projectKey));
		Object ownerObj = tapdBugInfo.get(TapdTemplateSystemField.HANDLER_USER);
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
	 * @param defaultTemplateFields 模板默认字段
	 */
	private void parseCustomFieldToMsBug(PlatformBugDTO msBug, Map tapdBugInfo, List<PlatformCustomFieldItemDTO> defaultTemplateFields, String projectKey) {
		List<PlatformCustomFieldItemDTO> needSyncCustomFields = new ArrayList<>();
		if (isSupportDefaultTemplate() && msBug.getPlatformDefaultTemplate()) {
			// 缺陷使用的平台默认模板, 使用平台默认模板字段
			for (PlatformCustomFieldItemDTO field : defaultTemplateFields) {
				needSyncCustomFields.add(SerializationUtils.clone(field));
			}
		} else {
			// 陷使用的非平台默认模板, 使用模板中配置的映射字段
			for (PlatformCustomFieldItemDTO field : msBug.getNeedSyncCustomFields()) {
				needSyncCustomFields.add(SerializationUtils.clone(field));
			}
		}
		if (CollectionUtils.isEmpty(needSyncCustomFields)) {
			return;
		}
		needSyncCustomFields.forEach(field -> {
			Object value = tapdBugInfo.get(field.getCustomData());
			if (value != null && StringUtils.isNotBlank(value.toString())) {
				if (StringUtils.equals(field.getType(), PlatformCustomFieldType.RICH_TEXT.name())) {
					if (!StringUtils.equals(field.getCustomData(), TapdTemplateSystemField.DESCRIPTION)) {
						field.setValue(parseTapdPicToMsRichText(value.toString(), msBug, projectKey));
					} else {
						field.setValue(msBug.getDescription());
					}
				} else if (StringUtils.equals(field.getType(), PlatformCustomFieldType.MULTIPLE_MEMBER.name())) {
					field.setValue(PluginUtils.toJSONString(StringUtils.split(value.toString(), ";")));
				} else if (StringUtils.equalsAnyIgnoreCase(field.getType(), PlatformCustomFieldType.MULTIPLE_SELECT.name(), PlatformCustomFieldType.CHECKBOX.name())) {
					field.setValue(PluginUtils.toJSONString(StringUtils.split(value.toString(), "|")));
				} else if (StringUtils.equalsIgnoreCase(field.getType(), PlatformCustomFieldType.CASCADER.name())) {
					field.setValue(PluginUtils.toJSONString(StringUtils.split(value.toString(), "/")));
				} else {
					field.setValue(value.toString());
				}
			} else {
				field.setValue(null);
			}
		});
		msBug.setCustomFieldList(needSyncCustomFields);
	}

	/**
	 * 生成新增, 更新参数
	 *
	 * @param request     请求参数
	 * @param platformBug 平台缺陷
	 * @return 参数
	 */
	private MultiValueMap<String, Object> buildUpdateParam(PlatformBugUpdateRequest request, PlatformBugUpdateDTO platformBug, TapdUserPlatformInfo userPlatformInfo) {
		MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
		parseCustomFields(request, paramMap, platformBug);
		// 替换MS-用户信息(昵称) => Tapd创建人
		if (userPlatformInfo != null && StringUtils.isNotEmpty(userPlatformInfo.getNickName())) {
			paramMap.add("reporter", userPlatformInfo.getNickName());
		}
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
					if (StringUtils.isEmpty(item.getCustomData()) || StringUtils.isEmpty(item.getType()) || ObjectUtils.isEmpty(item.getValue())) {
						continue;
					}
					if (StringUtils.equals(item.getType(), PlatformCustomFieldType.MULTIPLE_MEMBER.name())) {
						// 多选成员类型
						tapdEditParam.add(item.getCustomData(), StringUtils.join(PluginUtils.parseArray(item.getValue().toString(), String.class), ";"));
					} else if (StringUtils.equals(item.getCustomData(), TapdTemplateSystemField.HANDLER_USER)) {
						tapdEditParam.add(item.getCustomData(), item.getValue());
						platformBug.setPlatformHandleUser(item.getValue().toString());
					} else if (StringUtils.equalsAnyIgnoreCase(item.getType(), PlatformCustomFieldType.MULTIPLE_SELECT.name(), PlatformCustomFieldType.CHECKBOX.name())) {
						// 多选值类型
						tapdEditParam.add(item.getCustomData(), StringUtils.join(PluginUtils.parseArray(item.getValue().toString(), String.class), "|"));
					} else if (StringUtils.equalsIgnoreCase(item.getType(), PlatformCustomFieldType.CASCADER.name())) {
						// 级联类型, 通常默认为["父级", "子级"]这样的结构处理
						tapdEditParam.add(item.getCustomData(), StringUtils.join(PluginUtils.parseArray(item.getValue().toString(), String.class), "/"));
					} else {
						if (StringUtils.equals(item.getCustomData(), TapdTemplateSystemField.TITLE)) {
							// 标题
							tapdEditParam.add(TapdTemplateSystemField.TITLE, item.getValue());
							platformBug.setPlatformTitle(item.getValue().toString());
						} else if (StringUtils.equals(item.getCustomData(), TapdTemplateSystemField.DESCRIPTION)) {
							// 内容
							tapdEditParam.add(TapdTemplateSystemField.DESCRIPTION, parseRichTextPicToTapd(item.getValue().toString(), platformBug, request.getBaseUrl()));
						} else {
							// 其他字段
							tapdEditParam.add(item.getCustomData(), item.getValue());
						}
					}
				}
			}

			if (!tapdEditParam.containsKey(TapdTemplateSystemField.TITLE)) {
				tapdEditParam.add(TapdTemplateSystemField.TITLE, request.getTitle());
				platformBug.setPlatformTitle(request.getTitle());
			}
			if (!tapdEditParam.containsKey(TapdTemplateSystemField.DESCRIPTION)) {
				tapdEditParam.add(TapdTemplateSystemField.DESCRIPTION, parseRichTextPicToTapd(request.getDescription(), platformBug, request.getBaseUrl()));
			}

			if (tapdEditParam.containsKey(TapdTemplateSystemField.BEGIN_DATE) && tapdEditParam.containsKey(TapdTemplateSystemField.DUE_DATE)
					&& DateUtils.parseDate(tapdEditParam.get(TapdTemplateSystemField.BEGIN_DATE).getFirst().toString(), "yyyy-MM-dd")
					.after(DateUtils.parseDate(tapdEditParam.get(TapdTemplateSystemField.DUE_DATE).getFirst().toString(), "yyyy-MM-dd"))) {
				throw new MSPluginException("预计开始时间不能晚于预计结束时间!");
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
		Integer total = tapdClient.getStoriesCount(config.getTapdKey());
		if (total == null || total == 0) {
			return List.of();
		}
		// 由于tapd接口限制, 一次最多查询200条需求, 所以需要分页查询
		List<TapdStoryResponse> storyList = new ArrayList<>();
		for (int start = 1; start <= total / 200 + 1; start++) {
			List<TapdStoryResponse> pageStories = tapdClient.getProjectStories(config.getTapdKey(), start, 200);
			storyList.addAll(pageStories);
		}
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
	 * @param projectConfig      项目配置
	 * @return 项目配置
	 */
	private TapdProjectConfig validateAndSetUserConfig(String projectConfig) {
		return validateConfig(projectConfig);
	}

	/**
	 * 获取用户平台信息
	 * @param userPlatformConfig 用户平台信息
	 * @return 用户平台信息
	 */
	private TapdUserPlatformInfo getUserPlatformInfo(String userPlatformConfig) {
		return PluginUtils.parseObject(userPlatformConfig, TapdUserPlatformInfo.class);
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
	private String parseRichTextPicToTapd(String content, PlatformBugUpdateDTO platformBug, String baseUrl) {
		if (StringUtils.isBlank(content)) {
			return null;
		}
		StringBuilder msUrl = new StringBuilder();
		String[] splitText = content.split(">");
		for (String split : splitText) {
			msUrl.append(split);
			if (split.contains("<img") && !split.contains("http")) {
				String replaceTmpUrl;
				if (split.contains(MS_RICH_TEXT_REPLACE_WORD)) {
					// 双向同步过, 替换图片源和第三方图片源
					replaceTmpUrl = split.replaceAll("src", "alt").replaceAll(MS_RICH_TEXT_REPLACE_WORD, "src");
				} else {
					// 暂未双向同步, 替换成MS站点的图片链接, Tapd无法访问到MS站点时, 无法显示图片
					replaceTmpUrl = split.replaceAll(MS_RICH_TEXT_PIC_KEY_WORD, "alt").replaceAll("src=\"", "src=\"" + baseUrl);
				}
				content = content.replace(split, replaceTmpUrl);
			}
		}
		platformBug.setPlatformDescription(msUrl.toString());
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
		if (StringUtils.isBlank(content)) {
			return null;
		}
		try {
			String[] splitStr = content.split("<img");
			Map<String, String> richFileMap = new HashMap<>(16);
			for (String imgStr : splitStr) {
				/*
				 * 图片链接中存在本地上传的URL, 及已经双向同步的URL, 网络链接的URL
				 * eg: <img src="https.pic.s" alt /> 不用处理
				 */
				if (imgStr.contains(TAPD_RICH_TEXT_PIC_SRC_PREFIX)) {
					// eg: <img src="/tfl/*" alt /> Tapd本地上传的图片, 获取下载URL
					String targetUrl = imgStr.substring(imgStr.indexOf("src=\""), imgStr.indexOf("/>") + 2);
					String tapdUrlKey = targetUrl.substring(targetUrl.indexOf("/"), targetUrl.indexOf("\" "));
					String picTmpDownUrl = tapdClient.getPicTmpDownUrl(projectKey, tapdUrlKey);
					if (StringUtils.isNotBlank(picTmpDownUrl)) {
						String replaceTmpUrl = targetUrl.replaceAll("src", "psrc").replaceAll("/>", "alt=\"" + picTmpDownUrl + "\" />");
						content = content.replaceAll(targetUrl, replaceTmpUrl);
						// Tapd的图片默认命名为*.jpg, *: 唯一文件ID, 标识, 整数
						richFileMap.put(picTmpDownUrl, UUID.randomUUID() + ".jpg");
					}
				} else if (imgStr.contains("alt=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX)) {
					// eg: <img src="/base-url/attachment/download/file/pid/fid/true" alt="/attachment/download/file/pid/fid/true" 需处理, 已双向同步, 直接替换
					String replaceTmpUrl = imgStr.replaceAll("src", "psrc").replaceAll("alt", "src");
					content = content.replaceAll(imgStr, replaceTmpUrl);
				}
			}
			msBug.setRichTextImageMap(richFileMap);
			return content;
		} catch (Exception e) {
			PluginLogUtils.warn("Parse tapd bug description warn: " + e.getMessage());
		}
		return null;
	}

	/**
	 * 设置自定义字段基础属性
	 *
	 * @param item        字段项
	 * @param customField 自定义字段
	 * @param filedKey    唯一KEY
	 */
	private void setCustomFieldBaseProperty(TapdTemplateField item, TapdTemplateFieldDetail fieldDetail, PlatformCustomFieldItemDTO customField, Character filedKey) {
		customField.setId(item.getField());
		if (StringUtils.equals(item.getField(), TapdTemplateSystemField.TITLE)) {
			customField.setName(TapdTemplateSystemField.TITLE_LABEL_ZH);
			customField.setPlaceHolder(TapdTemplateSystemField.TITLE_PLACEHOLDER);
		} else if (StringUtils.equals(item.getField(), TapdTemplateSystemField.DESCRIPTION)) {
			customField.setName(TapdTemplateSystemField.DESCRIPTION_LABEL_ZH);
			customField.setPlaceHolder(TapdTemplateSystemField.DESCRIPTION_PLACEHOLDER);
		} else if (StringUtils.equals(item.getField(), TapdTemplateSystemField.SIZE)) {
			customField.setName(fieldDetail.getLabel());
			customField.setPlaceHolder(TapdTemplateSystemField.SIZE_PLACEHOLDER);
		} else {
			customField.setName(fieldDetail.getLabel());
		}
		customField.setKey(String.valueOf(filedKey));
		customField.setCustomData(item.getField());
		customField.setRequired(StringUtils.equals(item.getRequired(), "1") && !StringUtils.equals(item.getField(), TapdTemplateSystemField.DESCRIPTION));
		customField.setSystemField(StringUtils.equalsAny(item.getField(), TapdTemplateSystemField.TITLE, TapdTemplateSystemField.DESCRIPTION));
	}

	/**
	 * 设置自定义字段类型和选项值
	 *
	 * @param fieldDetail Tapd字段详情
	 * @param customField 自定义字段
	 */
	private void setCustomFieldTypeAndOption(TapdTemplateFieldDetail fieldDetail, PlatformCustomFieldItemDTO customField, Map<String, String> optionData) {
		Set<String> specialCustomFieldType = new HashSet<>(TapdSpecialCustomField.getSpecialFields());
		String htmlType = fieldDetail.getHtml_type();
		if (StringUtils.isNotBlank(htmlType)) {
			// Tapd自定义字段类型
			customField.setType(TapdFieldType.mappingTapdHtmlType(htmlType));
			customField.setOptions(StringUtils.equalsIgnoreCase(customField.getType(), PlatformCustomFieldType.CASCADER.name()) ?
					PluginUtils.toJSONString(parseCascadeFieldOptions(fieldDetail.getOptions())) : PluginUtils.toJSONString(parseCommonFieldOptions(fieldDetail.getOptions())));
			if (mappingSpecialField(specialCustomFieldType, htmlType)) {
				// 特殊自定义字段类型
				handleSpecialCustomFieldType(fieldDetail, customField, optionData);
			}
		}
	}

	/**
	 * 设置自定义字段默认值
	 *
	 * @param item        Tapd字段
	 * @param customField 自定义字段
	 */
	private void setCustomFieldDefaultValue(TapdTemplateField item, PlatformCustomFieldItemDTO customField, String type) {
		if (StringUtils.isNotBlank(item.getValue())) {
			if (StringUtils.equalsAny(customField.getType(), PlatformCustomFieldType.MULTIPLE_MEMBER.name())) {
				customField.setDefaultValue(PluginUtils.toJSONString(List.of(item.getValue().split(";"))));
			} else if (StringUtils.equalsAny(customField.getType(), PlatformCustomFieldType.MEMBER.name())) {
				customField.setDefaultValue(item.getValue().split(";")[0]);
			} else if (StringUtils.equals(customField.getType(), PlatformCustomFieldType.CASCADER.name())) {
				customField.setDefaultValue(item.getValue().substring(item.getValue().lastIndexOf("/") + 1));
			} else if (StringUtils.equals(customField.getType(), PlatformCustomFieldType.RADIO.name())) {
				customField.setDefaultValue(PluginUtils.parseArray(item.getValue()).getFirst().toString());
			} else if (StringUtils.equalsAny(customField.getType(), PlatformCustomFieldType.MULTIPLE_SELECT.name(), PlatformCustomFieldType.CHECKBOX.name())) {
				List<String> valList = PluginUtils.parseArray(item.getValue(), String.class);
				valList.removeIf(StringUtils::isBlank);
				customField.setDefaultValue(PluginUtils.toJSONString(valList));
			} else {
				customField.setDefaultValue(item.getValue());
			}
		}
	}

	/**
	 * 根据字段类型, 匹配具体的字段集合
	 *
	 * @param specialFields 特殊字段集合
	 * @param type          字段类型
	 * @return true: 匹配到特殊字段, false: 未匹配到特殊字段
	 */
	private static boolean mappingSpecialField(Set<String> specialFields, String type) {
		// 匹配特殊字段, 包含即可
		Optional<String> findField = specialFields.stream().filter(field -> StringUtils.contains(type, field)).findAny();
		return findField.isPresent();
	}

	/**
	 * 处理特殊自定义字段类型
	 *
	 * @param item        tapd字段
	 * @param customField 自定义字段
	 * @param optionData  选项数据
	 */
	private void handleSpecialCustomFieldType(TapdTemplateFieldDetail item, PlatformCustomFieldItemDTO customField, Map<String, String> optionData) {
		if (StringUtils.equalsAnyIgnoreCase(item.getHtml_type(), TapdSpecialCustomField.USER_CHOOSER, TapdSpecialCustomField.MIX_CHOOSER)) {
			// 自定义字段类型为user_chooser
			customField.setOptions(optionData.get(TapdOptionKey.USER.name()));
			customField.setType(StringUtils.equals(item.getName(), TapdTemplateSystemField.HANDLER_USER) ? PlatformCustomFieldType.MEMBER.name() : PlatformCustomFieldType.MULTIPLE_MEMBER.name());
		}
	}

	/**
	 * 准备特定的选项数据, 防止多次网络请求
	 *
	 * @return 选项数据映射集合
	 */
	private Map<String, String> prepareOptionData() {
		Map<String, String> optionData = new HashMap<>(16);
		// Tapd用户下拉选项
		optionData.put(TapdOptionKey.USER.name(), PluginUtils.toJSONString(tapdClient.getProjectUsers(projectConfig.getTapdKey())));
		return optionData;
	}

	/**
	 * 解析通用的选项值
	 *
	 * @param options 选项值
	 * @return 选项值集合
	 */
	private List<TapdFieldOption> parseCommonFieldOptions(List<Map> options) {
		if (CollectionUtils.isEmpty(options)) {
			return new ArrayList<>();
		}
		List<TapdFieldOption> fieldOptions = new ArrayList<>();
		Map optionMap = options.getFirst();
		// noinspection unchecked
		optionMap.keySet().forEach(key -> {
			TapdFieldOption fieldOption = new TapdFieldOption();
			fieldOption.setText(optionMap.get(key).toString());
			fieldOption.setValue(key.toString());
			fieldOptions.add(fieldOption);
		});
		return fieldOptions;
	}

	/**
	 * 解析级联选项值
	 *
	 * @param options 选项值
	 * @return 级联选项值
	 */
	private List<TapdFieldOption> parseCascadeFieldOptions(List<Map> options) {
		if (CollectionUtils.isEmpty(options)) {
			return new ArrayList<>();
		}
		List<TapdFieldOption> fieldOptions = new ArrayList<>();
		// 属于级联框选项值
		options.forEach(option -> {
			TapdFieldOption fieldOption = new TapdFieldOption();
			fieldOption.setText(option.get("name").toString());
			fieldOption.setValue(option.get("name").toString());
			if (option.containsKey("children")) {
				// noinspection unchecked
				fieldOption.setChildren(parseCascadeFieldOptions((List<Map>) option.get("children")));
			}
			fieldOptions.add(fieldOption);
		});
		return fieldOptions;
	}
}
