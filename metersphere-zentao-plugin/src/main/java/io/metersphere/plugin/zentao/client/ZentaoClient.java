package io.metersphere.plugin.zentao.client;

import io.metersphere.plugin.platform.spi.BaseClient;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.zentao.domain.*;
import io.metersphere.plugin.zentao.utils.UnicodeConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RequestCallback;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 由于禅道RESTFUL-API接口不完善, 且不支持附件相关功能;
 * 故选择了JSON-API调用方式; 而禅道JSON-API接口支持配置两种请求方式{PATH_INFO, GET};
 */
public abstract class ZentaoClient extends BaseClient {

    protected static String ENDPOINT;

    protected static String USER_NAME;

    protected static String PASSWD;

    public ZentaoApiUrl requestUrl;

    public static final String PROJECT_PARAM_KEY = "project";

    public static final String END_SUFFIX = "/";

    public ZentaoClient(String url) {
        ENDPOINT = url;
    }

    public void initConfig(ZentaoIntegrationConfig config) {
        if (config == null) {
            throw new MSPluginException("禅道服务集成配置为空");
        }
        USER_NAME = config.getAccount();
        PASSWD = config.getPassword();
        ENDPOINT = config.getAddress();
    }

    public String auth() {
        ZentaoAuthUserResponse authUser;
        String sessionId;
        try {
            sessionId = getSessionId();
            String loginUrl = requestUrl.getLogin();
            MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
            paramMap.add("account", USER_NAME);
            paramMap.add("password", PASSWD);
            ResponseEntity<String> response = restTemplate.exchange(loginUrl + sessionId, HttpMethod.POST, getHttpEntity(paramMap), String.class);
            authUser = getResultForObject(ZentaoAuthUserResponse.class, response);
        } catch (Exception e) {
            PluginLogUtils.error(e);
            throw new MSPluginException(e.getMessage());
        }
        ZentaoAuthUserResponse.User user = authUser.getUser();
        if (user == null) {
            PluginLogUtils.error(PluginUtils.toJSONString(authUser));
            // 登录失败，获取的session无效，置空session
            throw new MSPluginException("zentao login fail, user is null");
        }
        if (!StringUtils.equals(user.getAccount(), USER_NAME)) {
            PluginLogUtils.error("zentao login fail, inconsistent users");
            throw new MSPluginException("zentao login fail, inconsistent user");
        }
        return sessionId;
    }

    public ZentaoAddBugResponse.Bug addBug(MultiValueMap<String, Object> paramMap) {
        String sessionId = auth();
        String defaultProject = getDefaultProject(paramMap);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(requestUrl.getBugCreate() + sessionId + (StringUtils.isNotEmpty(defaultProject) ? "&project=" + defaultProject : StringUtils.EMPTY),
                    HttpMethod.POST, getHttpEntity(paramMap), String.class);
        } catch (Exception e) {
            PluginLogUtils.error(e.getMessage(), e);
            throw new MSPluginException(e.getMessage());
        }
        ZentaoAddBugResponse addBugResponse = getResultForObject(ZentaoAddBugResponse.class, response);
        ZentaoAddBugResponse.Bug bug = null;
        try {
            bug = PluginUtils.parseObject(addBugResponse.getData(), ZentaoAddBugResponse.Bug.class);
        } catch (Exception e) {
            PluginLogUtils.error(e);
        }
        if (bug == null) {
            throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(response.getBody()));
        }
        return bug;
    }

    public void updateBug(String id, MultiValueMap<String, Object> paramMap) {
        String sessionId = auth();
        try {
            ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBugUpdate(),
                    HttpMethod.POST, getHttpEntity(paramMap), String.class, id, sessionId);
            ZentaoAddBugResponse addBugResponse = getResultForObject(ZentaoAddBugResponse.class, response);
            if (!StringUtils.equalsIgnoreCase(addBugResponse.getStatus(), "success")
                    && StringUtils.isNotBlank(addBugResponse.getData())
                    && !StringUtils.equals(addBugResponse.getData(), "[]")) {
                // 如果没改啥东西保存也会报错，addIssueResponse.getData() 值为 "[]"
                throw new MSPluginException(UnicodeConvertUtils.unicodeToCn(response.getBody()));
            }
        } catch (Exception e) {
            PluginLogUtils.error(e.getMessage(), e);
            throw new MSPluginException(e.getMessage());
        }
    }

    public void deleteBug(String id) {
        String sessionId = auth();
        try {
            restTemplate.exchange(requestUrl.getBugDelete(), HttpMethod.GET, getHttpEntity(), String.class, id, sessionId);
        } catch (Exception e) {
            PluginLogUtils.error(e.getMessage(), e);
            throw new MSPluginException(e.getMessage());
        }
    }

    public Map<String, Object> getBugById(String id) {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBugGet(), HttpMethod.GET, getHttpEntity(), String.class, id, sessionId);
        ZentaoBugResponse bugResponse = getResultForObject(ZentaoBugResponse.class, response);
        if (StringUtils.equalsIgnoreCase(bugResponse.getStatus(), "fail")) {
            ZentaoBugResponse.Bug bug = new ZentaoBugResponse.Bug();
            bug.setId(id);
            bug.setSteps(StringUtils.SPACE);
            bug.setTitle(StringUtils.SPACE);
            bug.setStatus("closed");
            bug.setDeleted("1");
            bug.setOpenedBy(StringUtils.SPACE);
            bugResponse.setData(PluginUtils.toJSONString(bug));
        }
        // noinspection unchecked
        return PluginUtils.parseMap(bugResponse.getData());
    }

    public Map<String, Object> getUsers() {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getUserGet() + sessionId,
                HttpMethod.GET, getHttpEntity(), String.class);
        // noinspection unchecked
        return PluginUtils.parseMap(response.getBody());
    }

    public Map<String, Object> getDemands(String projectKey) {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getStoryGet() + sessionId,
                HttpMethod.GET, getHttpEntity(), String.class, projectKey);
        // noinspection unchecked
        return PluginUtils.parseMap(response.getBody());
    }

    public void checkProject(String projectKey) {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getProductGet(),
                HttpMethod.GET, getHttpEntity(), String.class, projectKey, sessionId);
        try {
            // noinspection unchecked
            Map<String, Object> data = PluginUtils.parseMap(PluginUtils.parseMap(response.getBody()).get("data").toString());
            // noinspection unchecked
            if (data.get("id") != null || ((Map<String, Object>) data.get("product")).get("id") != null) {
                return;
            }
        } catch (Exception e) {
            PluginLogUtils.error("check zentao project error : " + response.getBody());
        }
        throw new MSPluginException("项目不存在");
    }

    public void uploadAttachment(String objectType, String objectId, File file) {
        String sessionId = auth();
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        FileSystemResource fileResource = new FileSystemResource(file);
        paramMap.add("files", fileResource);
        HttpHeaders header = getHeader();
        header.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));
        HttpEntity<MultiValueMap<String, Object>> httpEntity = getHttpEntity(paramMap, header);
        try {
            restTemplate.exchange(requestUrl.getFileUpload(), HttpMethod.POST, httpEntity, String.class, objectType, objectId, sessionId);
        } catch (Exception e) {
            PluginLogUtils.info("upload zentao attachment error");
        }
    }

    public void deleteAttachment(String fileId) {
        String sessionId = auth();
        try {
            restTemplate.exchange(requestUrl.getFileDelete(), HttpMethod.GET, getHttpEntity(), String.class, fileId, sessionId);
        } catch (Exception e) {
            PluginLogUtils.info("delete zentao attachment error");
        }
    }

    public void getAttachmentBytes(String fileId, Consumer<InputStream> inputStreamHandler) {
        RequestCallback requestCallback = request -> {
            // 定义请求头的接收类型
            request.getHeaders().setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
            request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
        };

        String sessionId = auth();
        restTemplate.execute(requestUrl.getFileDownload(), HttpMethod.GET,
                requestCallback, (clientHttpResponse) -> {
                    inputStreamHandler.accept(clientHttpResponse.getBody());
                    return null;
                }, fileId, sessionId);
    }

    public Map<String, Object> getBugsByProjectId(Integer pageNum, Integer pageSize, String projectId) {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBugList(),
                HttpMethod.GET, getHttpEntity(), String.class, projectId, 9999999, pageSize, pageNum, sessionId);
        try {
            // noinspection unchecked
            return PluginUtils.parseMap(PluginUtils.parseMap(response.getBody()).get("data").toString());
        } catch (Exception e) {
            PluginLogUtils.error(e);
            throw new MSPluginException("获取项目缺陷集合异常, 请检查集成或项目配置!");
        }
    }

    private String getSessionId() {
        String getSessionUrl = requestUrl.getSessionGet();
        ResponseEntity<String> response = restTemplate.exchange(getSessionUrl, HttpMethod.GET, getHttpEntity(), String.class);
        ZentaoSessionResponse sessionResponse = getResultForObject(ZentaoSessionResponse.class, response);
        return PluginUtils.parseObject(sessionResponse.getData(), ZentaoSessionResponse.Session.class).getSessionID();
    }

    protected HttpHeaders getHeader() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
        return httpHeaders;
    }

    protected HttpEntity<MultiValueMap<String, Object>> getHttpEntity() {
        return new HttpEntity<>(getHeader());
    }

    protected HttpEntity<MultiValueMap<String, Object>> getHttpEntity(MultiValueMap<String, Object> paramMap) {
        return new HttpEntity<>(paramMap, getHeader());
    }

    protected HttpEntity<MultiValueMap<String, Object>> getHttpEntity(MultiValueMap<String, Object> paramMap, MultiValueMap<String, String> headers) {
        return new HttpEntity<>(paramMap, headers);
    }

    private String getDefaultProject(MultiValueMap<String, Object> param) {
        if (param.containsKey(PROJECT_PARAM_KEY) && !CollectionUtils.isEmpty(param.get(PROJECT_PARAM_KEY))) {
            return param.get(PROJECT_PARAM_KEY).get(0).toString();
        }
        return StringUtils.EMPTY;
    }

    public String getBaseUrl() {
        if (ENDPOINT.endsWith(END_SUFFIX)) {
            return ENDPOINT.substring(0, ENDPOINT.length() - 1);
        }
        return ENDPOINT;
    }

    public String getReplaceImgUrl(String replaceImgUrl) {
        String baseUrl = getBaseUrl();
        String[] split = baseUrl.split("/");
        String suffix = split[split.length - 1];
        if (StringUtils.equals("biz", suffix)) {
            suffix = baseUrl;
        } else if (!StringUtils.equalsAny(suffix, "zentao", "pro", "zentaopms", "zentaopro", "zentaobiz")) {
            suffix = "";
        } else {
            suffix = "/" + suffix;
        }
        return String.format(replaceImgUrl, suffix);
    }
}
