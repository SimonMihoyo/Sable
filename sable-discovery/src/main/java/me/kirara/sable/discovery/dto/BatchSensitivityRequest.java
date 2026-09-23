package me.kirara.sable.discovery.dto;

import java.util.List;

/** 批量修正字段分级请求体。 */
public class BatchSensitivityRequest {

    private List<Long> columnIds;
    private String sensitivity;

    public List<Long> getColumnIds() { return columnIds; }
    public void setColumnIds(List<Long> columnIds) { this.columnIds = columnIds; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
}
