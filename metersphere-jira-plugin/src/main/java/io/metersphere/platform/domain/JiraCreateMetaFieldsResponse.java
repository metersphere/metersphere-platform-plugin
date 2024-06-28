package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraCreateMetaFieldsResponse {

	private int startAt;
	private int maxResults;
	private int total;
	private List<JiraCreateMetadataResponse.Field> fields;
	/**
	 * 兼容一些非SASS环境, 及版本较低的环境
	 */
	private List<JiraCreateMetadataResponse.Field> values;
}
