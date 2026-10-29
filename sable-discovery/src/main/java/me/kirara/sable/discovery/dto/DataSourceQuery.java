package me.kirara.sable.discovery.dto;

import me.kirara.sable.common.page.PageQuery;

/** 数据源列表查询条件。 */
public class DataSourceQuery extends PageQuery {

    /** 数据源类型，精确匹配：MYSQL / POSTGRESQL / ORACLE / SQLSERVER / MONGODB / REDIS。 */
    private String type;

    /** 状态，精确匹配：ACTIVE / INACTIVE / ERROR / TESTING。 */
    private String status;

    /** 分组名，精确匹配。 */
    private String groupName;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}
