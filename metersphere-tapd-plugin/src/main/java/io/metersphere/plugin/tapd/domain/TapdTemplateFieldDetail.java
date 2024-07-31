package io.metersphere.plugin.tapd.domain;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TapdTemplateFieldDetail {

	private String name;
	private String label;
	private String html_type;
	private String memo;
	private List<Map> options;
}
