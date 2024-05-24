package io.metersphere.plugin.tapd.client;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.spi.BaseClient;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.tapd.constants.TapdSystemType;
import io.metersphere.plugin.tapd.constants.TapdUrl;
import io.metersphere.plugin.tapd.domain.TapdIntegrationConfig;
import io.metersphere.plugin.tapd.domain.TapdProject;
import io.metersphere.plugin.tapd.domain.TapdTransitionStatusItem;
import io.metersphere.plugin.tapd.domain.response.TapdBaseResponse;
import io.metersphere.plugin.tapd.domain.response.TapdBugResponse;
import io.metersphere.plugin.tapd.domain.response.TapdStoryResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;

import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TapdClient extends BaseClient {

	protected static String ENDPOINT = "https://api.tapd.cn";

	protected static String BASE_URL = "https://www.tapd.cn";

	protected static String USERNAME;

	protected static String PASSWORD;

	public TapdClient(TapdIntegrationConfig integrationConfig) {
		initConfig(integrationConfig);
	}

	/**
	 * 初始化配置参数
	 *
	 * @param config 配置
	 */
	public void initConfig(TapdIntegrationConfig config) {
		if (config == null) {
			throw new MSPluginException("Tapd服务集成配置为空");
		}
		USERNAME = config.getAccount();
		PASSWORD = config.getPassword();
	}

	/**
	 * 认证
	 */
	public void auth() {
		try {
			restTemplate.exchange(ENDPOINT + TapdUrl.AUTH, HttpMethod.GET, getAuthHttpEntity(), String.class);
		} catch (Exception e) {
			if (e instanceof HttpClientErrorException && ((HttpClientErrorException) e).getStatusCode().is4xxClientError()) {
				throw new MSPluginException("TAPD认证失败: API账号或口令错误");
			} else {
				PluginLogUtils.error(e);
				throw new MSPluginException("TAPD认证失败: 请求错误");
			}
		}
	}

	/**
	 * 获取项目
	 *
	 * @param projectKey 项目key
	 * @return 返回项目
	 */
	public TapdProject getProject(String projectKey) {
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_PROJECT_INFO, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, projectKey);
			if (response.getBody() == null) {
				return null;
			}
			return PluginUtils.parseObject(PluginUtils.toJSONString(((Map) response.getBody().getData()).get("Workspace")), TapdProject.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 获取起始状态流
	 *
	 * @param systemType 系统类型
	 * @param projectKey 项目ID
	 * @return 状态流
	 */
	public SelectOption getFirstStepWorkFlow(String systemType, String projectKey, String storyTypeId) {
		SelectOption statusOption = new SelectOption();
		try {
			String firstStepUrl = ENDPOINT + TapdUrl.GET_WORKFLOW_FIRST_STEP;
			if (StringUtils.equals(TapdSystemType.STORY, systemType)) {
				firstStepUrl = firstStepUrl + "&workitem_type_id=" + storyTypeId;
			}
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(firstStepUrl, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, systemType, projectKey);
			if (response.getBody() == null) {
				return null;
			}
			// noinspection unchecked
			Map<String, String> statusMap = PluginUtils.parseMap(PluginUtils.toJSONString(response.getBody().getData()));
			statusMap.keySet().forEach(statusKey -> {
				statusOption.setText(statusMap.get(statusKey));
				statusOption.setValue(statusKey);
			});
			return statusOption;
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 获取状态流, 流转细则
	 *
	 * @param systemType     系统类型
	 * @param projectKey     项目ID
	 * @param previousStatus 当前状态
	 * @return 状态流选项
	 */
	public List<SelectOption> getWorkFlowTransition(String systemType, String projectKey, String previousStatus) {
		List<SelectOption> statusOption = new ArrayList<>();
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_WORKFLOW_TRANSITIONS, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, systemType, projectKey);
			if (response.getBody() == null) {
				return null;
			}
			List<TapdTransitionStatusItem> statusTransitions = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), TapdTransitionStatusItem.class);
			if (CollectionUtils.isEmpty(statusTransitions)) {
				return null;
			}
			// 获取工作流状态中英文名对应关系
			ResponseEntity<TapdBaseResponse> statusDictResponse = restTemplate.exchange(ENDPOINT + TapdUrl.GET_WORKFLOW_STATUS_MAP, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, systemType, projectKey);
			// noinspection unchecked
			Map<String, String> statusDictMap = PluginUtils.parseMap(PluginUtils.toJSONString(Objects.requireNonNull(statusDictResponse.getBody()).getData()));
			List<String> transitionStatus;
			if (StringUtils.isNotBlank(previousStatus)) {
				transitionStatus = statusTransitions.stream().filter(transition -> StringUtils.equals(transition.getStepPrevious(), previousStatus)).map(TapdTransitionStatusItem::getStepNext).distinct().collect(Collectors.toList());
			} else {
				transitionStatus = statusTransitions.stream().map(TapdTransitionStatusItem::getStepPrevious).distinct().collect(Collectors.toList());
			}
			transitionStatus.forEach(statusKey -> {
				SelectOption selectOption = new SelectOption();
				selectOption.setText(statusDictMap.get(statusKey) == null ? statusKey : statusDictMap.get(statusKey));
				selectOption.setValue(statusKey);
				statusOption.add(selectOption);
			});
			return statusOption;
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 获取项目成员列表
	 *
	 * @param projectKey 项目Key
	 * @return 成员列表
	 */
	public List<SelectOption> getProjectUsers(String projectKey) {
		List<SelectOption> userOptions = new ArrayList<>();
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_PROJECT_USERS, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, projectKey);
			if (response.getBody() == null) {
				throw new MSPluginException("获取Tapd项目成员列表为空!");
			}
			List<Map> userMaps = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), Map.class);
			if (CollectionUtils.isEmpty(userMaps)) {
				throw new MSPluginException("获取Tapd项目成员列表为空!");
			}
			userMaps.forEach(userMap -> {
				// noinspection unchecked
				Map<String, String> user = PluginUtils.parseMap(PluginUtils.toJSONString(userMap.get("UserWorkspace")));
				SelectOption selectOption = new SelectOption();
				selectOption.setText(user.get("user"));
				selectOption.setValue(user.get("user"));
				userOptions.add(selectOption);
			});
			return userOptions;
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException("获取Tapd项目成员列表异常!");
		}
	}

	/**
	 * 分页获取项目的需求
	 *
	 * @param projectKey 项目Key
	 * @param startPage  开始页码
	 * @param pageSize   每页Size
	 * @return 需求列表
	 */
	public List<TapdStoryResponse> getProjectStories(String projectKey, Integer startPage, Integer pageSize) {
		List<TapdStoryResponse> stories = new ArrayList<>();
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_PROJECT_STORY, HttpMethod.GET, getAuthHttpEntity(),
					TapdBaseResponse.class, projectKey, startPage, pageSize);
			if (response.getBody() == null) {
				return new ArrayList<>();
			}
			List<Map> storyMaps = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), Map.class);
			List<TapdStoryResponse> tmpStories = new ArrayList<>();
			storyMaps.forEach(storyMap -> {
				TapdStoryResponse story = PluginUtils.parseObject(PluginUtils.toJSONString(storyMap.get("Story")), TapdStoryResponse.class);
				tmpStories.add(story);
			});
			List<TapdStoryResponse> childStories = tmpStories.stream().filter(story -> StringUtils.isBlank(story.getParent_id()) || !StringUtils.equals(story.getParent_id(), "0")).toList();
			List<TapdStoryResponse> parentStories = tmpStories.stream().filter(story -> StringUtils.isNotBlank(story.getParent_id()) && StringUtils.equals(story.getParent_id(), "0")).toList();
			for (TapdStoryResponse story : parentStories) {
				if (StringUtils.isNotBlank(story.getChildren_id()) && !StringUtils.equalsAny(story.getChildren_id(), "|", "||")) {
					List<String> childStoryIds = List.of(story.getChildren_id().replaceAll("\\|\\|", StringUtils.EMPTY).split("\\|"));
					List<TapdStoryResponse> filterChildList = childStories.stream().filter(filterStory -> childStoryIds.contains(filterStory.getId())).collect(Collectors.toList());
					story.setChildren(filterChildList);
				}
				stories.add(story);
			}
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
		}
		return stories;
	}

	/**
	 * 新增缺陷
	 *
	 * @param paramMap   参数Map
	 * @param projectKey 项目Key
	 * @return 请求返回
	 */
	public TapdBugResponse editBug(MultiValueMap<String, Object> paramMap, String projectKey) {
		paramMap.add("workspace_id", projectKey);
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.EDIT_BUG, HttpMethod.POST,
					getPostAuthHttpEntityForParam(paramMap), TapdBaseResponse.class);
			return PluginUtils.parseObject(PluginUtils.toJSONString(PluginUtils.parseMap(
					PluginUtils.toJSONString(Objects.requireNonNull(response.getBody()).getData())).get("Bug")), TapdBugResponse.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException("创建Tapd缺陷异常");
		}
	}

	/**
	 * 分页查询缺陷
	 *
	 * @param projectKey 项目Key
	 * @param page       页码
	 * @param limit      每页大小
	 * @return 缺陷列表
	 */
	public List<Map> getBugForPage(String projectKey, int page, int limit) {
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.LIST_BUG, HttpMethod.GET,
					getAuthHttpEntity(), TapdBaseResponse.class, projectKey, page, limit);
			if (response.getBody() == null || response.getBody().getData() == null) {
				return new ArrayList<>();
			}
			List<Map> bugMaps = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), Map.class);
			return bugMaps.stream().map(bugMap -> (Map) bugMap.get("Bug")).collect(Collectors.toList());
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException("分页查询Tapd缺陷异常");
		}
	}

	/**
	 * 获取图片下载链接
	 *
	 * @param projectKey 项目Key
	 * @param imagePath  图片路径
	 * @return 获取图片下载链接
	 */
	public String getPicTmpDownUrl(String projectKey, String imagePath) {
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_DOWNLOAD_URL, HttpMethod.GET,
					getAuthHttpEntity(), TapdBaseResponse.class, projectKey, imagePath);
			if (response.getBody() == null || response.getBody().getData() == null) {
				return null;
			}
			Map responseDataMap = PluginUtils.parseMap(PluginUtils.toJSONString(response.getBody().getData()));
			return PluginUtils.parseMap(PluginUtils.toJSONString(responseDataMap.get("Attachment"))).get("download_url").toString();
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * 获取附件字节流
	 *
	 * @param fileDownUrl        文件URL
	 * @param inputStreamHandler 流处理
	 */
	public void getAttachmentBytes(String fileDownUrl, Consumer<InputStream> inputStreamHandler) {
		RequestCallback requestCallback = request -> {
			// 定义请求头的接收类型
			request.getHeaders().setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
			request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
		};

		restTemplate.execute(fileDownUrl, HttpMethod.GET,
				requestCallback, (clientHttpResponse) -> {
					inputStreamHandler.accept(clientHttpResponse.getBody());
					return null;
				});
	}

	/**
	 * 获取认证实体
	 *
	 * @return 获取认证头
	 */
	protected HttpEntity<MultiValueMap<String, Object>> getPostAuthHttpEntityForParam(MultiValueMap<String, Object> paramMap) {
		return new HttpEntity<>(paramMap, getAuthHeader());
	}

	/**
	 * 获取认证实体
	 *
	 * @return 获取认证头
	 */
	protected HttpEntity<MultiValueMap<String, String>> getAuthHttpEntity() {
		return new HttpEntity<>(getAuthHeader());
	}

	/**
	 * 获取认证Header Base64{api_user:api_password}
	 *
	 * @return 获取认证头
	 */
	protected HttpHeaders getAuthHeader() {
		return getBasicHttpHeaders(USERNAME, PASSWORD);
	}

	/**
	 * 获取URL前缀
	 *
	 * @return 获取认证头
	 */
	public String getBaseUrl() {
		return BASE_URL;
	}
}
