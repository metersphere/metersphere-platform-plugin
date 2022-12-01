package io.metersphere.platform.api;

import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.platform.domain.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 平台对接相关业务
 * @author jianxing.chen
 */
public interface Platform {

    /**
     * 获取平台相关需求
     * 功能用例关联需求时调用
     * @param projectConfig 项目设置表单值
     * @return 需求列表
     */
    List<DemandDTO> getDemands(String projectConfig);

    /**
     * 创建缺陷并封装 MS 返回
     * 创建缺陷时调用
     * @param issuesRequest issueRequest
     * @return MS 缺陷
     */
    IssuesWithBLOBs addIssue(PlatformIssuesUpdateRequest issuesRequest);

    /**
     * 项目设置中选项，从平台获取下拉框选项
     * frontend.json 中选项值配置了 optionMethod ，项目设置时调用
     * @return 返回下拉列表
     */
    List<SelectOption> getProjectOptions(GetOptionRequest request);

    /**
     * 更新缺陷
     * 编辑缺陷时调用
     * @param request
     * @return MS 缺陷
     */
    IssuesWithBLOBs updateIssue(PlatformIssuesUpdateRequest request);

    /**
     * 删除缺陷平台缺陷
     * 删除缺陷时调用
     * @param id 平台的缺陷 ID
     */
    void deleteIssue(String id);

    /**
     * 校验服务集成配置
     * 服务集成点击校验时调用
     */
    void validateIntegrationConfig();

    /**
     * 校验项目配置
     * 项目设置成点击校验项目 key 时调用
     */
    void validateProjectConfig(String projectConfig);

    /**
     * 校验用户配置配置
     * 用户信息，校验第三方信息时调用
     */
    void validateUserConfig(String userConfig);

    /**
     * 支持附件上传
     * 编辑缺陷上传附件是会调用判断是否支持附件上传
     * 如果支持会调用 syncIssuesAttachment 上传缺陷到第三方平台
     */
    boolean isAttachmentUploadSupport();

    /**
     * 获取缺陷平台项目下的相关人员
     * @return
     */
    List<PlatformUser> getPlatformUser();

    /**
     * 同步缺陷最新变更
     * 开源用户点击同步缺陷时调用
     */
    SyncIssuesResult syncIssues(SyncIssuesRequest request);

    /**
     * 获取附件内容
     * 同步缺陷中，同步附件时会调用
     * @param fileKey 文件关键字
     */
    byte[] getAttachmentContent(String fileKey);

    /**
     * 获取第三方平台缺陷的自定义字段
     * frontend.json 中选项值配置了 optionMethod ，项目设置时调用
     * @return
     */
    List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfig);

    /**
     * Get请求的代理
     * 目前使用场景：富文本框中如果有图片是存储在第三方平台，MS 通过 url 访问
     * 这时如果第三方平台需要登入才能访问到静态资源，可以通过将富文本框图片内容构造如下格式访问
     * ![name](/resource/md/get/url?platform=Jira?project_id=&workspace_id=&url=)
     * @param url
     * @return
     */
    ResponseEntity proxyForGet(String url, Class responseEntityClazz);

    /**
     * 同步 MS 缺陷附件到第三方平台
     * isAttachmentUploadSupport 返回为 true 时，同步和创建缺陷时会调用
     */
    void syncIssuesAttachment(SyncIssuesAttachmentRequest request);

    /**
     * 获取第三方平台的状态列表
     * 编辑缺陷时的下拉框选项会调用
     * @param issueKey
     * @return
     */
    List<PlatformStatusDTO> getStatusList(String issueKey);
}
