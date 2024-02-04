package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestBugResponse extends ZentaoRestBaseResponse {

	private List<ZentaoBugRestEditResponse> bugs;
}
