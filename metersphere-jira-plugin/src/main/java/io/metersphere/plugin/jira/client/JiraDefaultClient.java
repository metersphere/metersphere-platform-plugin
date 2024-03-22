package io.metersphere.plugin.jira.client;


import io.metersphere.plugin.jira.constants.JiraApiUrl;
import io.metersphere.plugin.jira.constants.JiraMetadataField;
import io.metersphere.plugin.jira.domain.*;
import io.metersphere.plugin.platform.dto.request.SyncAllBugRequest;
import io.metersphere.plugin.platform.spi.BaseClient;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class JiraDefaultClient extends BaseClient {

	protected static String ENDPOINT;

	protected static String USER_NAME;

	protected static String PASSWD;

	protected static String TOKEN;

	protected static String AUTH_TYPE;

	public static final String AUTH_HEADER_TYPE = "bearer";

	public static final String AUTH_SELF = "{\"self\"";

	protected static final String PREFIX = "/rest/api/2";

	private static final String GREENHOPPER_V1_BASE_URL = "/rest/greenhopper/1.0";

	private static final String ISSUE_RELATE_FILTER_JQL = "project in projectsWhereUserHasPermission(\"Link Issues\") AND (resolution = Unresolved or statusCategory != Done) ORDER BY priority DESC, updated DESC";

	public JiraDefaultClient(JiraIntegrationConfig jiraIntegrationConfig) {
		initConfig(jiraIntegrationConfig);
	}

	/**
	 * 获取缺陷
	 *
	 * @param issuesId 缺陷ID
	 * @return 返回缺陷
	 */
	public JiraIssue getIssues(String issuesId) {
		PluginLogUtils.info("getIssues: " + issuesId);
		ResponseEntity<String> responseEntity;
		responseEntity = restTemplate.exchange(getBaseUrl() + "/issue/" + issuesId, HttpMethod.GET, getAuthHttpEntity(), String.class);
		return getResultForObject(JiraIssue.class, responseEntity);
	}

	/**
	 * 获取Jira创建元数据
	 *
	 * @param projectKey 项目Key
	 * @param issueType  缺陷类型
	 * @return 返回元数据字段Map
	 */
	public Map<String, JiraCreateMetadataResponse.Field> getCreateMetadata(String projectKey, String issueType) {
		String url = getBaseUrl() + JiraApiUrl.CREATE_META;
		ResponseEntity<String> response;
		Map<String, JiraCreateMetadataResponse.Field> fields;
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, projectKey, issueType);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
		try {
			fields = getResultForObject(JiraCreateMetadataResponse.class, response).getProjects().get(0).getIssuetypes().get(0).getFields();
		} catch (Exception e) {
			PluginLogUtils.error(e);
			throw new MSPluginException("请检查服务集成信息或Jira项目ID");
		}
		fields.remove("project");
		fields.remove("issuetype");
		return fields;
	}

	/**
	 * 获取缺陷类型
	 *
	 * @param projectKey 项目key
	 * @return 返回缺陷类型
	 */
	public List<JiraIssueType> getIssueType(String projectKey) {
		JiraIssueProject project = getProject(projectKey);
		String url = getUrl(JiraApiUrl.GET_ISSUE_TYPE);
		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, project.getId());
		} catch (HttpClientErrorException e) {
			// SaaS 的jira才有这个接口，报错则调用其他接口
			if (HttpStatus.NOT_FOUND.isSameCodeAs(e.getStatusCode())) {
				return this.getProject(projectKey).getIssueTypes();
			}
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
		return getResultForList(JiraIssueType.class, response);
	}

	/**
	 * 获取项目
	 *
	 * @param projectKey 项目key
	 * @return 返回项目
	 */
	public JiraIssueProject getProject(String projectKey) {
		String url = getUrl("/project/" + projectKey);
		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
		return getResultForObject(JiraIssueProject.class, response);
	}

	/**
	 * 查询Assignable User
	 *
	 * @param projectKey 项目Key
	 * @param query      查询参数
	 * @return 返回Assignable User列表
	 */
	public List<JiraUser> assignableUserSearch(String projectKey, String query) {
		int startAt = 0;
		int maxResults = 30;
		String baseUrl = getBaseUrl() + "/user/assignable/search?project={1}&maxResults=" + maxResults + "&startAt=" + startAt;
		String url = baseUrl;
		if (StringUtils.isNotBlank(query)) {
			// cloud 加了 username 会报错，报错就用 query
			url = baseUrl + "&username=" + query;
		}

		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, projectKey);
		} catch (Exception e) {
			try {
				// 兼容不同版本查询
				if (StringUtils.isNotBlank(query)) {
					url = baseUrl + "&query=" + query;
				}
				response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, projectKey);
			} catch (Exception ex) {
				PluginLogUtils.error(ex);
				return new ArrayList<>();
			}
		}
		return getResultForList(JiraUser.class, response);
	}


	/**
	 * 查询所有用户
	 *
	 * @param query 查询参数
	 * @return 返回用户列表
	 */
	public List<JiraUser> allUserSearch(String query) {
		int startAt = 0;
		int maxResults = 30;
		String baseUrl = getBaseUrl() + "/user/search?maxResults=" + maxResults + "&startAt=" + startAt;
		// server 版本没有username报错，报错则加上username
		String url = baseUrl + "&query=" + (StringUtils.isNotBlank(query) ? query : StringUtils.EMPTY);
		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class);
		} catch (Exception e) {
			try {
				// 兼容不同版本查询
				url = baseUrl + "&username=" + (StringUtils.isNotBlank(query) ? query : "\"\"");
				response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class);
			} catch (Exception ex) {
				PluginLogUtils.error(ex);
				return new ArrayList<>();
			}
		}
		return getResultForList(JiraUser.class, response);
	}

	/**
	 * 分页获取需求列表
	 *
	 * @param projectKey 项目Key
	 * @param issueType  缺陷类型
	 * @param startAt    开始位置
	 * @param maxResults 数据大小
	 * @return 需求列表
	 */
	public Map<String, Object> pageDemand(String projectKey, String issueType, int startAt, int maxResults, String query) {
		String jql = getBaseUrl() + "/search?jql=project=" + projectKey + "+AND+issuetype=" + issueType +
				(StringUtils.isNotBlank(query) ? "+AND+summary~\"" + query + "\"" : StringUtils.EMPTY) +
				"&maxResults=" + maxResults + "&startAt=" + startAt + "&fields=summary,issuetype";
		ResponseEntity<String> responseEntity = restTemplate.exchange(jql, HttpMethod.GET, getAuthHttpEntity(), String.class);
		// noinspection unchecked
		return PluginUtils.parseMap(responseEntity.getBody());
	}

	/**
	 * 添加缺陷
	 *
	 * @param body         请求body
	 * @param fieldNameMap 参数字段Map
	 * @return 返回缺陷
	 */
	public JiraAddIssueResponse addIssue(String body, Map<String, String> fieldNameMap) {
		PluginLogUtils.info("Add Jira Bug Param:" + body);
		HttpHeaders headers = getAuthHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<String> response = null;
		try {
			response = restTemplate.exchange(getBaseUrl() + "/issue", HttpMethod.POST, requestEntity, String.class);
		} catch (HttpClientErrorException e) {
			handleFieldErrorMsg(fieldNameMap, e);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
		return getResultForObject(JiraAddIssueResponse.class, response);
	}

	/**
	 * 创建或者修改的接口返回参数错误; 将错误中参数ID替换成参数名称, 方便用户定位.
	 *
	 * @param fieldNameMap 参数Map
	 * @param e            接口错误异常
	 */
	private static void handleFieldErrorMsg(Map<String, String> fieldNameMap, HttpClientErrorException e) {
		if (HttpStatus.BAD_REQUEST.isSameCodeAs(e.getStatusCode()) && fieldNameMap != null) {
			Map<String, String> fieldNameErrorMap = new HashMap<>(16);
			try {
				// noinspection unchecked
				Map<String, String> fieldErrorMap = (Map<String, String>) PluginUtils.parseMap(e.getResponseBodyAsString()).get("errors");
				fieldErrorMap.forEach((id, msg) -> fieldNameErrorMap.put(fieldNameMap.get(id) == null ? id : fieldNameMap.get(id), msg));
			} catch (Exception exception) {
				PluginLogUtils.error(exception);
			}
			if (!fieldNameErrorMap.isEmpty()) {
				throw new MSPluginException(PluginUtils.toJSONString(fieldNameErrorMap));
			}
		}
		PluginLogUtils.error(e);
		throw new MSPluginException(e.getMessage());
	}

	/**
	 * 获取Transitions
	 *
	 * @param issueKey 缺陷Key
	 * @return 返回Transitions
	 */
	public List<JiraTransitionsResponse.Transitions> getTransitions(String issueKey) {
		ResponseEntity<String> response = restTemplate.exchange(getBaseUrl() + "/issue/{1}/transitions", HttpMethod.GET, getAuthHttpEntity(), String.class, issueKey);
		return getResultForObject(JiraTransitionsResponse.class, response).getTransitions();
	}

	/**
	 * 修改Transition值
	 *
	 * @param param    参数
	 * @param issueKey 缺陷Key
	 */
	public void doTransitions(String param, String issueKey) {
		PluginLogUtils.info("doTransitions: " + param);
		HttpHeaders headers = getAuthHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> requestEntity = new HttpEntity<>(param, headers);
		try {
			restTemplate.exchange(getBaseUrl() + "/issue/{1}/transitions", HttpMethod.POST, requestEntity, String.class, issueKey);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
		}
	}

	/**
	 * 获取Sprint
	 *
	 * @param query 查询参数
	 * @return 返回Sprint
	 */
	public List<JiraSprint> getSprint(String query) {
		String url = getGreenhopperV1BaseUrl() + "/sprint/picker?_=" + System.currentTimeMillis();
		if (StringUtils.isNotBlank(query)) {
			url += "&query=" + query;
		}
		ResponseEntity<String> response = restTemplate.exchange(url,
				HttpMethod.GET, getAuthHttpEntity(), String.class);
		JiraSprintResponse jiraSprintResponse = getResultForObject(JiraSprintResponse.class, response);
		List<JiraSprint> sprints = new ArrayList<>();
		if (!CollectionUtils.isEmpty(jiraSprintResponse.getSuggestions())) {
			sprints = jiraSprintResponse.getSuggestions();
		}
		if (!CollectionUtils.isEmpty(jiraSprintResponse.getAllMatches())) {
			sprints.addAll(jiraSprintResponse.getAllMatches());
		}
		return sprints;
	}

	/**
	 * 获取Epic
	 *
	 * @param queryKey 查询参数
	 * @return 返回Epic集合
	 */
	public List<JiraEpic> getEpics(String queryKey) {
		ResponseEntity<String> response = restTemplate.exchange(getGreenhopperV1BaseUrl() + "/epics?maxResults=300&searchQuery={0}&hideDone=true&_=" + System.currentTimeMillis(),
				HttpMethod.GET, getAuthHttpEntity(), String.class, queryKey);
		List<JiraEpicResponse.EpicLists> epicLists = getResultForObject(JiraEpicResponse.class, response).getEpicLists();
		if (CollectionUtils.isEmpty(epicLists)) {
			return new ArrayList<>();
		}
		List<JiraEpic> jiraEpics = new ArrayList<>();
		epicLists.forEach(item -> {
			List<JiraEpic> epicNames = item.getEpicNames();
			if (!CollectionUtils.isEmpty(epicNames)) {
				jiraEpics.addAll(epicNames);
			}
		});
		return jiraEpics;
	}

	/**
	 * 获取GreenhopperUrl
	 *
	 * @return 返回GreenhopperUrl
	 */
	public String getGreenhopperV1BaseUrl() {
		return ENDPOINT + GREENHOPPER_V1_BASE_URL;
	}

	/**
	 * 更新缺陷
	 *
	 * @param id           缺陷ID
	 * @param body         请求body
	 * @param fieldNameMap 参数字段Map
	 */
	public void updateIssue(String id, String body, Map<String, String> fieldNameMap) {
		PluginLogUtils.info("updateIssue: " + body);
		HttpHeaders headers = getAuthHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
		try {
			restTemplate.exchange(getBaseUrl() + "/issue/" + id, HttpMethod.PUT, requestEntity, String.class);
		} catch (HttpClientErrorException e) {
			handleFieldErrorMsg(fieldNameMap, e);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
	}

	/**
	 * 删除缺陷
	 *
	 * @param id 缺陷ID
	 */
	public void deleteIssue(String id) {
		PluginLogUtils.info("deleteIssue: " + id);
		try {
			restTemplate.exchange(getBaseUrl() + "/issue/" + id, HttpMethod.DELETE, getAuthHttpEntity(), String.class);
		} catch (HttpClientErrorException e) {
			if (HttpStatus.NOT_FOUND.isSameCodeAs(e.getStatusCode())) {
				// NOT_FOUND 缺陷未找到
				PluginLogUtils.error(e.getMessage());
			}
		}
	}

	/**
	 * 删除附件
	 *
	 * @param id 附件ID
	 */
	public void deleteAttachment(String id) {
		PluginLogUtils.info("deleteAttachment: " + id);
		try {
			restTemplate.exchange(getBaseUrl() + "/attachment/" + id, HttpMethod.DELETE, getAuthHttpEntity(), String.class);
		} catch (HttpClientErrorException e) {
			if (HttpStatus.NOT_FOUND.isSameCodeAs(e.getStatusCode())) {
				// 404Jira附件未找到
				throw new MSPluginException(e.getMessage());
			}
		}
	}


	/**
	 * 上传附件
	 *
	 * @param issueKey 缺陷Key
	 * @param file     附件
	 */
	public void uploadAttachment(String issueKey, File file) {
		HttpHeaders authHeader = getAuthHeader();
		authHeader.add("X-Atlassian-Token", "no-check");
		authHeader.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));

		MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
		FileSystemResource fileResource = new FileSystemResource(file);
		paramMap.add("file", fileResource);
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, authHeader);
		try {
			restTemplate.exchange(getBaseUrl() + "/issue/" + issueKey + "/attachments", HttpMethod.POST, requestEntity, String.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
		}
	}

	/**
	 * 认证
	 */
	public void auth() {
		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(getBaseUrl() + "/myself", HttpMethod.GET, getAuthHttpEntity(), String.class);
			if (StringUtils.isBlank(response.getBody()) || !response.getBody().startsWith(AUTH_SELF)) {
				throw new MSPluginException("JIRA认证失败: 地址错误");
			}
		} catch (HttpClientErrorException e) {
			if (HttpStatus.UNAUTHORIZED.isSameCodeAs(e.getStatusCode())) {
				throw new MSPluginException("JIRA认证失败: 账号或密码(Token)错误");
			}
			if (HttpStatus.NOT_FOUND.isSameCodeAs(e.getStatusCode())) {
				throw new MSPluginException("JIRA认证失败: 地址错误");
			} else {
				PluginLogUtils.error(e);
				throw new MSPluginException("JIRA认证失败", e);
			}
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException("JIRA认证失败", e);
		}
	}

	/**
	 * 获取认证参数
	 *
	 * @return 返回认证参数
	 */
	protected HttpEntity<MultiValueMap<String, String>> getAuthHttpEntity() {
		return new HttpEntity<>(getAuthHeader());
	}

	/**
	 * 获取认证头
	 *
	 * @return 返回认证头
	 */
	protected HttpHeaders getAuthHeader() {
		HttpHeaders headers;
		if (StringUtils.isNotBlank(AUTH_TYPE) && StringUtils.equals(AUTH_TYPE, AUTH_HEADER_TYPE)) {
			headers = getBearHttpHeaders(TOKEN);
		} else {
			headers = getBasicHttpHeaders(USER_NAME, PASSWD);
		}
		headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
		return headers;
	}

	/**
	 * 获取认证头(Json)
	 *
	 * @return 返回认证头
	 */
	protected HttpHeaders getAuthJsonHeader() {
		HttpHeaders headers = getAuthHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	/**
	 * 获取需求请求URL
	 *
	 * @return 返回请求URL
	 */
	public String getBaseDemandUrl() {
		return ENDPOINT;
	}

	/**
	 * 获取请求URL
	 *
	 * @return 返回请求URL
	 */
	public String getBaseUrl() {
		return ENDPOINT + PREFIX;
	}

	/**
	 * 获取完整URL
	 *
	 * @param path 请求路径
	 * @return 返回完整URL
	 */
	protected String getUrl(String path) {
		return getBaseUrl() + path;
	}

	/**
	 * 初始化配置参数
	 *
	 * @param config 配置
	 */
	public void initConfig(JiraIntegrationConfig config) {
		if (config == null) {
			throw new MSPluginException("Jira服务集成配置为空");
		}
		String url = config.getAddress();

		if (StringUtils.isNotBlank(url) && url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		ENDPOINT = url;
		USER_NAME = config.getAccount();
		PASSWD = config.getPassword();
		TOKEN = config.getToken();
		AUTH_TYPE = config.getAuthType();
	}

	/**
	 * 获取项目缺陷(分页)
	 *
	 * @param startAt    开始页
	 * @param maxResults 每页大小
	 * @param projectKey 项目key
	 * @param issueType  缺陷类型
	 * @return 缺陷集合
	 */
	public JiraIssueListResponse getProjectIssues(Integer startAt, Integer maxResults, String projectKey, String issueType, SyncAllBugRequest syncRequest) {
		return getProjectIssues(startAt, maxResults, projectKey, issueType, syncRequest, null);
	}

	/**
	 * 获取项目缺陷 (分页)
	 *
	 * @param startAt    开始页
	 * @param maxResults 每页大小
	 * @param projectKey 项目key
	 * @param issueType  缺陷类型
	 * @param fields     过滤字段 {
	 *                   *all - include all fields
	 *                   *navigable - include just navigable fields
	 *                   summary,comment - include just the summary and comments
	 *                   -description - include navigable fields except the description (the default is *navigable for search)
	 *                   *all,-comment - include everything except comments
	 *                   }
	 * @return 缺陷集合
	 */
	public JiraIssueListResponse getProjectIssues(Integer startAt, Integer maxResults, String projectKey, String issueType, SyncAllBugRequest syncRequest, String fields) {
		ResponseEntity<String> responseEntity;
		String url = getBaseUrl() + "/search?startAt={1}&maxResults={2}&jql=project={3}+AND+issuetype={4}";
		if (syncRequest != null && syncRequest.getPre() != null && syncRequest.getCreateTime() != null) {
			url = url + "+AND+created" + (syncRequest.getPre() ? "<=" : ">=") + "\"" + DateFormatUtils.format(syncRequest.getCreateTime(), "yyyy-MM-dd HH:mm") + "\"";
		}
		if (StringUtils.isNotBlank(fields)) {
			url = url + "&fields=" + fields;
		} else {
			// 字段参数默认不传的话使用*all,-comment
			url = url + "&fields=*all,-comment";
		}
		responseEntity = restTemplate.exchange(url,
				HttpMethod.GET, getAuthHttpEntity(), String.class, startAt, maxResults, projectKey, issueType);
		return getResultForObject(JiraIssueListResponse.class, responseEntity);
	}

	/**
	 * 获取附件内容
	 *
	 * @param url                请求路径
	 * @param inputStreamHandler 返回内容处理
	 */
	public void getAttachmentContent(String url, Consumer<InputStream> inputStreamHandler) {
		RequestCallback requestCallback = request -> {
			request.getHeaders().addAll(getAuthHeader());
			// 定义请求头的接收类型
			request.getHeaders().setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
		};

		restTemplate.execute(url, HttpMethod.GET, requestCallback, clientHttpResponse -> {
			inputStreamHandler.accept(clientHttpResponse.getBody());
			return null;
		});
	}

	/**
	 * 获取项目缺陷附件 (分页)
	 *
	 * @param startAt     开始页
	 * @param maxResults  每页大小
	 * @param projectKey  项目key
	 * @param issueType   缺陷类型
	 * @param syncRequest 同步参数
	 * @return 返回缺陷附件集合
	 */
	public JiraIssueListResponse getProjectIssuesAttachment(Integer startAt, Integer maxResults, String projectKey, String issueType, SyncAllBugRequest syncRequest) {
		return getProjectIssues(startAt, maxResults, projectKey, issueType, syncRequest, JiraMetadataField.ATTACHMENT_NAME);
	}

	/**
	 * 代理接口
	 *
	 * @param path                路径
	 * @param responseEntityClazz 响应对象类型
	 * @return 响应内容
	 */
	public ResponseEntity<?> proxyForGet(String path, Class<?> responseEntityClazz) {
		PluginLogUtils.info("jira proxyForGet: " + path);
		String endpoint = ENDPOINT;
		try {
			// ENDPOINT 可能会带有前缀，比如 http://xxxx/jira
			// 这里去掉 /jira，再拼接图片路径path
			URI uri = new URI(ENDPOINT);
			endpoint = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
		} catch (URISyntaxException e) {
			PluginLogUtils.error(e);
		}
		String url = endpoint + path;
		validateProxyUrl(url, "/secure/attachment", "/attachment/content");
		return restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), responseEntityClazz);
	}

	/**
	 * 获取状态
	 *
	 * @param jiraKey 缺陷Key
	 * @return 返回状态集合
	 */
	public List<JiraStatusResponse> getStatus(String jiraKey) {
		ResponseEntity<String> response = restTemplate.exchange(getBaseUrl() + "/project/" + jiraKey + "/statuses", HttpMethod.GET, getAuthHttpEntity(), String.class);
		return getResultForList(JiraStatusResponse.class, response);
	}

	/**
	 * 获取issue-link
	 *
	 * @param currentIssueKey 当前缺陷key
	 * @param query           查询参数
	 * @return 返回issue-link集合
	 */
	public List<JiraIssueLink> getIssueLinks(String currentIssueKey, String query) {
		String url = getBaseUrl() + "/issue/picker?showSubTaskParent=true&showSubTasks=true"
				+ (StringUtils.isNotEmpty(currentIssueKey) ? "&currentIssueKey=" + currentIssueKey : StringUtils.EMPTY)
				+ (StringUtils.isNotEmpty(query) ? "&query=" + query : StringUtils.EMPTY)
				+ "&currentJQL=" + URLEncoder.encode(ISSUE_RELATE_FILTER_JQL, StandardCharsets.UTF_8);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class);
		List<JiraIssueLinkResponse.IssueLink> sections = getResultForObject(JiraIssueLinkResponse.class, response).getSections();
		if (CollectionUtils.isEmpty(sections)) {
			return Collections.emptyList();
		}
		List<JiraIssueLink> issueLinks = new ArrayList<>();
		sections.forEach(section -> {
			List<JiraIssueLink> issues = section.getIssues();
			if (!CollectionUtils.isEmpty(issues)) {
				issueLinks.addAll(issues);
			}
		});
		return issueLinks;
	}

	/**
	 * 获取issue-link-type
	 *
	 * @return 返回issue-link-type集合
	 */
	public List<JiraIssueLinkTypeResponse.IssueLinkType> getIssueLinkType() {
		ResponseEntity<String> response = restTemplate.exchange(getBaseUrl() + "/issueLinkType", HttpMethod.GET, getAuthHttpEntity(), String.class);
		List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes = getResultForObject(JiraIssueLinkTypeResponse.class, response).getIssueLinkTypes();
		if (CollectionUtils.isEmpty(issueLinkTypes)) {
			return Collections.emptyList();
		}
		return issueLinkTypes;
	}

	/**
	 * 关联issue
	 *
	 * @param request link-issue请求参数
	 */
	public void linkIssue(JiraIssueLinkRequest request) {
		PluginLogUtils.info("linkIssue: " + request);
		HttpHeaders headers = getAuthHeader();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> requestEntity = new HttpEntity<>(PluginUtils.toJSONString(request), headers);
		try {
			restTemplate.exchange(getBaseUrl() + "/issueLink", HttpMethod.POST, requestEntity, String.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
	}

	/**
	 * un-link-issue
	 *
	 * @param linkId 关联ID
	 */
	public void unLinkIssue(String linkId) {
		PluginLogUtils.info("deleteIssueLink: " + linkId);
		try {
			restTemplate.exchange(getBaseUrl() + "/issueLink/" + linkId, HttpMethod.DELETE, getAuthHttpEntity(), String.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException(e.getMessage());
		}
	}
}
