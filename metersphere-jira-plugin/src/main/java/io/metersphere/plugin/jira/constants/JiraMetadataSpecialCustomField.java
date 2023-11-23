package io.metersphere.plugin.jira.constants;

import java.util.Arrays;
import java.util.List;

public class JiraMetadataSpecialCustomField {

    public static final String MULTI_USER_PICKER = "multiuserpicker";
    public static final String USER_PICKER = "userpicker";
    public static final String PEOPLE = "people";
    public static final String MULTI_CHECK_BOX = "multicheckboxes";
    public static final String CUSTOM_FIELD_TYPES = "customfieldtypes";
    public static final String SPRINT_FIELD_NAME = "sprint";
    public static final String EPIC_LINK = "epic-link";

    public static List<String> getSpecialFields() {
        return Arrays.asList(MULTI_USER_PICKER, USER_PICKER, PEOPLE, MULTI_CHECK_BOX, CUSTOM_FIELD_TYPES, SPRINT_FIELD_NAME, EPIC_LINK);
    }
}
