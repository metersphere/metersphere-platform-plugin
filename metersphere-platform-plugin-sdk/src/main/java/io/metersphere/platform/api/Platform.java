package io.metersphere.platform.api;

import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.platform.domain.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface Platform {

    /**
     * 获取平台相关需求
     * @param projectConfig
     * @return
     */
    List<DemandDTO> getDemands(String projectConfig);

    /**
     * 添加缺陷到缺陷平台
     *
     * @param issuesRequest issueRequest
     */
    IssuesWithBLOBs addIssue(PlatformIssuesUpdateRequest issuesRequest);

    /**
     * 项目设置获取下拉框选项
     * @return
     */
    List<SelectOption> getProjectOptions(GetOptionRequest request);

    /**
     * 更新缺陷
     * @param request
     */
    IssuesWithBLOBs updateIssue(PlatformIssuesUpdateRequest request);

    /**
     * 删除缺陷平台缺陷
     *
     * @param id issue id
     */
    void deleteIssue(String id);

    /**
     * 校验服务集成配置
     */
    void validateIntegrationConfig();

    /**
     * 校验项目配置
     */
    void validateProjectConfig(String projectConfig);

    /**
     * 校验用户配置配置
     */
    void validateUserConfig(String userConfig);

    /**
     * 支持附件上传
     */
    boolean isAttachmentUploadSupport();

    /**
     * 获取缺陷平台项目下的相关人员
     * @return platform user list
     */
    List<PlatformUser> getPlatformUser();

    /**
     * 同步缺陷最新变更
     */
    SyncIssuesResult syncIssues(SyncIssuesRequest request);

    /**
     * 获取附件内容
     * @param fileKey
     */
    byte[] getAttachmentContent(String fileKey);

    /**
     * 获取第三方平台缺陷模板
     * @return
     */
    List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfig);

    /**
     * Get请求的代理
     * jira 匿名获取图片用到
     * @param url
     * @return
     */
    ResponseEntity proxyForGet(String url, Class responseEntityClazz);

    /**
     * 同步MS缺陷附件到第三方平台
     */
    void syncIssuesAttachment(SyncIssuesAttachmentRequest request);

    /**
     * 获取第三方平台的状态集合
     * @param issueKey
     * @return
     */
    List<PlatformStatusDTO> getStatusList(String issueKey);
}
