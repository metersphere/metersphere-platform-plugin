package io.metersphere.plugin.tapd.constants;

public class TapdUrl {

	public static final String AUTH = "/quickstart/testauth";

	public static final String GET_PROJECT_INFO = "/workspaces/get_workspace_info?workspace_id={1}";

	public static final String GET_BUGS_TEMPLATE_LIST = "/bugs/template_list?workspace_id={1}";

	public static final String GET_BUGS_DEFAULT_TEMPLATE = "/bugs/get_default_bug_template?template_id={1}&workspace_id={2}";

	public static final String GET_ALL_BUGS_FIELD = "/bugs/get_fields_info?workspace_id={1}";

	public static final String GET_WORKFLOW_FIRST_STEP = "/workflows/first_step?system={1}&workspace_id={2}";

	public static final String GET_WORKFLOW_TRANSITIONS = "/workflows/all_transitions?system={1}&workspace_id={2}";

	public static final String GET_WORKFLOW_STATUS_MAP = "/workflows/status_map?system={1}&workspace_id={2}";

	public static final String GET_PROJECT_USERS = "/workspaces/users?workspace_id={1}";

	public static final String GET_PROJECT_STORY = "/stories?workspace_id={1}&page={2}&limit={3}&fields=id,name,children_id,parent_id";

	public static final String GET_PROJECT_STORY_COUNT = "/stories/count?workspace_id={1}";

	public static final String EDIT_BUG = "/bugs";

	public static final String LIST_BUG = "/bugs?workspace_id={1}&page={2}&limit={3}";

	public static final String GET_DOWNLOAD_URL = "/files/get_image?workspace_id={1}&image_path={2}";
}
