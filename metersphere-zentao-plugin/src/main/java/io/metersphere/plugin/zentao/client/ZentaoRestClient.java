package io.metersphere.plugin.zentao.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.metersphere.plugin.platform.spi.BaseClient;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.zentao.constants.ZentaoRestApiUrl;
import io.metersphere.plugin.zentao.domain.ZentaoIntegrationConfig;
import io.metersphere.plugin.zentao.domain.request.rest.ZentaoRestBugEditRequest;
import io.metersphere.plugin.zentao.domain.response.rest.*;
import io.metersphere.plugin.zentao.utils.UnicodeConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

/**
 * 禅道Restful API调用方式, 支持16.0~18.10版本
 * 官方禅道V1版本地址: <a href="https://www.zentao.net/book/api/664.html"></a>
 */
public class ZentaoRestClient extends BaseClient {

	/**
	 * 禅道地址
	 */
	protected static String ENDPOINT;

	/**
	 * 禅道用户名
	 */
	protected static String USER_NAME;

	/**
	 * 禅道密码
	 */
	protected static String PASSWD;

	/**
	 * Restful API版本
	 */
	protected static final String API_VERSION = "v1";

	/**
	 * 一些常量及全局变量
	 */
	private static final String END_SUFFIX = "/";
	private static final String ERROR_RESPONSE_KEY = "error";
	private static final String SUCCESS_RESPONSE_KEY = "success";
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ZentaoRestClient(String url) {
		ENDPOINT = url;
	}

	/**
	 * 初始化禅道配置
	 *
	 * @param config 集成配置
	 */
	public void initConfig(ZentaoIntegrationConfig config) {
		if (config == null) {
			throw new MSPluginException("禅道服务集成配置为空");
		}
		ENDPOINT = config.getAddress();
		USER_NAME = config.getAccount();
		PASSWD = config.getPassword();
	}

	/**
	 * 获取Token
	 *
	 * @return token
	 */
	public String getToken() {
		ObjectNode jsonObj = objectMapper.createObjectNode();
		jsonObj.put("account", USER_NAME);
		jsonObj.put("password", PASSWD);
		ResponseEntity<ZentaoRestTokenResponse> response;
		try {
			response = restTemplate.postForEntity(getRestUrl(ZentaoRestApiUrl.GET_TOKEN, null), getJsonHttpEntity(jsonObj), ZentaoRestTokenResponse.class);
			if (response.getBody() == null) {
				throw new MSPluginException("禅道认证失败: 地址错误或未获取到Token");
			}
		} catch (Exception e) {
			if (e instanceof HttpClientErrorException && ((HttpClientErrorException.BadRequest) e).getStatusCode().is4xxClientError()) {
				throw new MSPluginException("禅道认证失败: 账号或密码错误");
			} else {
				throw new MSPluginException("禅道认证失败: 地址错误或连接超时");
			}
		}
		return response.getBody().getToken();
	}

	/**
	 * 校验产品或者项目
	 */
	public void validateProject(String zentaoKey, String type) {
		if (StringUtils.isBlank(zentaoKey) || StringUtils.isBlank(type)) {
			throw new MSPluginException("禅道项目校验参数不能为空!");
		}
		ResponseEntity<Map> response;
		try {
			response = restTemplate.getForEntity(getRestUrl(ZentaoRestApiUrl.GET_PRODUCT_OR_PROJECT, type), Map.class, zentaoKey);
			if (response.getBody() == null || response.getBody().containsKey(ERROR_RESPONSE_KEY)) {
				throw new MSPluginException("产品或项目不存在!");
			}
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
	}

	/**
	 * 新增禅道缺陷
	 *
	 * @param request             缺陷请求参数
	 * @param productOrProjectKey 项目Key
	 * @return 缺陷响应内容
	 */
	public ZentaoBugRestEditResponse add(ZentaoRestBugEditRequest request, String productOrProjectKey) {
		ResponseEntity<ZentaoBugRestEditResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.ADD_BUG, null), HttpMethod.POST, getJsonHttpEntityWithToken(PluginUtils.toJSONString(request)), ZentaoBugRestEditResponse.class, productOrProjectKey);
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
		return response.getBody();
	}

	/**
	 * 修改禅道缺陷
	 *
	 * @param request  缺陷请求参数
	 * @param issueKey 缺陷Key
	 * @return 缺陷响应内容
	 */
	public ZentaoBugRestEditResponse update(ZentaoRestBugEditRequest request, String issueKey) {
		ResponseEntity<ZentaoBugRestEditResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_OR_UPDATE_OR_DELETE_BUG, null), HttpMethod.PUT, getJsonHttpEntityWithToken(PluginUtils.toJSONString(request)), ZentaoBugRestEditResponse.class, issueKey);
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
		return response.getBody();
	}

	/**
	 * 获取禅道缺陷详情
	 *
	 * @param issueKey 缺陷Key
	 * @return 缺陷详情
	 */
	public ZentaoRestBugDetailResponse get(String issueKey) {
		ResponseEntity<ZentaoRestBugDetailResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_OR_UPDATE_OR_DELETE_BUG, null), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestBugDetailResponse.class, issueKey);
			if (response.getBody() == null) {
				throw new MSPluginException("获取禅道缺陷详情失败!");
			}
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
		return response.getBody();
	}

	/**
	 * 删除禅道缺陷
	 *
	 * @param issueKey 缺陷Key
	 */
	public void delete(String issueKey) {
		ResponseEntity<ZentaoRestMessageResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_OR_UPDATE_OR_DELETE_BUG, null), HttpMethod.DELETE, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestMessageResponse.class, issueKey);
			if (response.getBody() == null || !StringUtils.equals(response.getBody().getMessage(), SUCCESS_RESPONSE_KEY)) {
				throw new MSPluginException("删除禅道缺陷失败!");
			}
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
	}

	/**
	 * 获取禅道用户列表
	 *
	 * @return 用户列表
	 */
	public ZentaoRestUserResponse getUsers() {
		ResponseEntity<ZentaoRestUserResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_USERS, null), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestUserResponse.class);
			if (response.getBody() == null) {
				throw new MSPluginException("获取禅道用户列表失败!");
			}
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
		return response.getBody();
	}

	/**
	 * 解决BUG
	 */
	public void resolveBug(String zentaoKey, String assignedTo) {
		ObjectNode jsonObj = objectMapper.createObjectNode();
		jsonObj.put("comment", StringUtils.EMPTY);
		jsonObj.put("assignedTo", assignedTo);
		restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.RESOLVE_BUG, null), HttpMethod.POST, getJsonHttpEntityWithToken(PluginUtils.toJSONString(jsonObj)), String.class, zentaoKey);
	}

	/**
	 * 关闭BUG
	 */
	public void closeBug(String zentaoKey) {
		ObjectNode jsonObj = objectMapper.createObjectNode();
		jsonObj.put("comment", StringUtils.EMPTY);
		restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.CLOSE_BUG, null), HttpMethod.POST, getJsonHttpEntityWithToken(PluginUtils.toJSONString(jsonObj)), String.class, zentaoKey);
	}

	/**
	 * 关闭BUG
	 */
	public void activeBug(String zentaoKey, String assignedTo) {
		ObjectNode jsonObj = objectMapper.createObjectNode();
		jsonObj.put("comment", StringUtils.EMPTY);
		jsonObj.put("assignedTo", assignedTo);
		restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.ACTIVE_BUG, null), HttpMethod.POST, getJsonHttpEntityWithToken(PluginUtils.toJSONString(jsonObj)), String.class, zentaoKey);
	}

	/**
	 * 分页获取禅道需求列表
	 *
	 * @param productOrProjectKey 产品或项目Key
	 * @param type                产品或项目
	 * @param page                页码
	 * @param limit               每页数量
	 */
	public ZentaoRestDemandResponse pageDemands(String productOrProjectKey, String type, int page, int limit) {
		ResponseEntity<ZentaoRestDemandResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.LIST_DEMAND, type), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestDemandResponse.class, productOrProjectKey, page, limit);
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
		return response.getBody();
	}

	/**
	 * 获取禅道产品计划列表
	 *
	 * @param productKey 产品Key
	 * @param page       页码
	 * @param limit      每页数量
	 * @return 计划列表
	 */
	public ZentaoRestPlanResponse getProductPlans(String productKey, int page, int limit) {
		ResponseEntity<ZentaoRestPlanResponse> response;
		try {
			response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.LIST_PLAN, null), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestPlanResponse.class, productKey, page, limit);
		} catch (Exception e) {
			throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
		}
		return response.getBody();
	}

	/**
	 * 登录认证
	 */
	public void auth() {
		if (StringUtils.isBlank(getToken())) {
			throw new MSPluginException("禅道认证失败!");
		}
	}

	/**
	 * 获取请求地址
	 *
	 * @return 请求地址
	 */
	public String getBaseUrl() {
		if (ENDPOINT.endsWith(END_SUFFIX)) {
			return ENDPOINT;
		}
		return ENDPOINT + END_SUFFIX;
	}

	/**
	 * 获取Restful请求地址
	 *
	 * @param url  请求地址
	 * @param type 请求类型
	 * @return Restful请求地址
	 */
	private String getRestUrl(String url, String type) {
		return getBaseUrl() + "api.php/" + API_VERSION + (StringUtils.isEmpty(type) ? StringUtils.EMPTY : "/" + type) + url;
	}

	/**
	 * 获取请求头
	 *
	 * @return 请求头
	 */
	protected HttpHeaders getHeader() {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		return httpHeaders;
	}

	/**
	 * 获取请求参数(no-token)
	 *
	 * @param jsonObj json对象
	 * @return 请求参数
	 */
	protected HttpEntity<String> getJsonHttpEntity(ObjectNode jsonObj) {
		return new HttpEntity<>(jsonObj.toString(), getHeader());
	}

	/**
	 * 获取请求参数(token)
	 *
	 * @param json Body请求参数
	 * @return 请求参数
	 */
	protected HttpEntity<String> getJsonHttpEntityWithToken(String json) {
		HttpHeaders header = getHeader();
		header.add("Token", getToken());
		return new HttpEntity<>(json, header);
	}
}
