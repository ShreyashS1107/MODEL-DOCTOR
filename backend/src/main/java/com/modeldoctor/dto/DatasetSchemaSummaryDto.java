package com.modeldoctor.dto;

import java.util.List;

public class DatasetSchemaSummaryDto {
    private Long rowCount;
    private Integer columnCount;
    private List<ColumnProfileDto> columns;
    private List<String> potentialTargetColumns;
    private List<String> potentialPredictionColumns;
    private List<String> potentialProtectedAttributes;

    public DatasetSchemaSummaryDto() {}

    public DatasetSchemaSummaryDto(Long rowCount, Integer columnCount, List<ColumnProfileDto> columns,
                                  List<String> potentialTargetColumns, List<String> potentialPredictionColumns,
                                  List<String> potentialProtectedAttributes) {
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.columns = columns;
        this.potentialTargetColumns = potentialTargetColumns;
        this.potentialPredictionColumns = potentialPredictionColumns;
        this.potentialProtectedAttributes = potentialProtectedAttributes;
    }

    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }

    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }

    public List<ColumnProfileDto> getColumns() { return columns; }
    public void setColumns(List<ColumnProfileDto> columns) { this.columns = columns; }

    public List<String> getPotentialTargetColumns() { return potentialTargetColumns; }
    public void setPotentialTargetColumns(List<String> potentialTargetColumns) { this.potentialTargetColumns = potentialTargetColumns; }

    public List<String> getPotentialPredictionColumns() { return potentialPredictionColumns; }
    public void setPotentialPredictionColumns(List<String> potentialPredictionColumns) { this.potentialPredictionColumns = potentialPredictionColumns; }

    public List<String> getPotentialProtectedAttributes() { return potentialProtectedAttributes; }
    public void setPotentialProtectedAttributes(List<String> potentialProtectedAttributes) { this.potentialProtectedAttributes = potentialProtectedAttributes; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long rowCount;
        private Integer columnCount;
        private List<ColumnProfileDto> columns;
        private List<String> potentialTargetColumns;
        private List<String> potentialPredictionColumns;
        private List<String> potentialProtectedAttributes;

        public Builder rowCount(Long rowCount) { this.rowCount = rowCount; return this; }
        public Builder columnCount(Integer columnCount) { this.columnCount = columnCount; return this; }
        public Builder columns(List<ColumnProfileDto> columns) { this.columns = columns; return this; }
        public Builder potentialTargetColumns(List<String> potentialTargetColumns) { this.potentialTargetColumns = potentialTargetColumns; return this; }
        public Builder potentialPredictionColumns(List<String> potentialPredictionColumns) { this.potentialPredictionColumns = potentialPredictionColumns; return this; }
        public Builder potentialProtectedAttributes(List<String> potentialProtectedAttributes) { this.potentialProtectedAttributes = potentialProtectedAttributes; return this; }

        public DatasetSchemaSummaryDto build() {
            return new DatasetSchemaSummaryDto(rowCount, columnCount, columns, potentialTargetColumns, potentialPredictionColumns, potentialProtectedAttributes);
        }
    }
}
