package io.metersphere.platform.api;

import im.metersphere.plugin.exception.MSPluginException;
import im.metersphere.plugin.utils.JSON;
import im.metersphere.plugin.utils.LogUtil;
import io.metersphere.platform.utils.EncryptUtils;
import io.metersphere.platform.utils.EnvProxySelector;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();

            SSLConnectionSocketFactory csf = SSLConnectionSocketFactoryBuilder
                    .create()
                    .setSslContext(sslContext)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    // 可以支持设置系统代理
                    .setRoutePlanner(new SystemDefaultRoutePlanner(new EnvProxySelector()))
                    // 忽略 https
                    .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(csf)
                            .build())
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
        headers.setBasicAuth(authKey);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    protected  HttpHeaders getBearHttpHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
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
