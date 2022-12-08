package io.metersphere.platform.api;

import im.metersphere.plugin.exception.MSPluginException;
import im.metersphere.plugin.utils.JSON;
import im.metersphere.plugin.utils.LogUtil;
import io.metersphere.platform.utils.EncryptUtils;
import io.metersphere.platform.utils.EnvProxySelector;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public abstract class BaseClient {

    protected  RestTemplate restTemplate;

     {
        try {
            TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
            SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
            CloseableHttpClient httpClient = HttpClients.custom()
                    // 可以支持设置系统代理
                    .setRoutePlanner(new SystemDefaultRoutePlanner(new EnvProxySelector()))
                    .setSSLSocketFactory(csf)
                    .build();
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);

            restTemplate = new RestTemplate(requestFactory);
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    protected  HttpHeaders getBasicHttpHeaders(String userName, String passWd) {
        String authKey = EncryptUtils.base64Encoding(userName + ":" + passWd);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + authKey);
        headers.add("Accept", "application/json");
        return headers;
    }

    protected  String getResult(ResponseEntity<String> response) {
        int statusCodeValue = response.getStatusCodeValue();
        LogUtil.info("responseCode: " + statusCodeValue);
        if(statusCodeValue >= 400){
            MSPluginException.throwException(response.getBody());
        }
        LogUtil.info("result: " + response.getBody());
        return response.getBody();
    }

    protected  Object getResultForList(Class clazz, ResponseEntity<String> response) {
        return Arrays.asList(JSON.parseArray(getResult(response), clazz).toArray());
    }

    protected  Object getResultForObject(Class clazz,ResponseEntity<String> response) {
        return JSON.parseObject(getResult(response), clazz);
    }

    public void validateProxyUrl(String url, String ...path) {
        try {
            if (!StringUtils.containsAny(new URI(url).getPath(), path)) {
                // 只允许访问图片
                MSPluginException.throwException("illegal path");
            }
        } catch (URISyntaxException e) {
            LogUtil.error(e);
            MSPluginException.throwException("illegal path");
        }
    }
}
