package model;

import java.util.List;
import java.util.Map;

public class Table {
    private String name;
    private List<Column> listColumn;
    private List<Row> listRow;

    public Table(String name, List<Column> listColumn, List<Row> listRow) {
        this.name = name;
        this.listColumn = listColumn;
        this.listRow = listRow;
    }

    public List<Row> getRow() {
        return listRow;
    }

    public List<Column> getColumn() {
        return listColumn;
    }

    public void isert(Map<String, Object> values) {
        for (Column column : listColumn) {
            Object value = values.get(column.getNameColumn());
            if (value == null) {
                return;
            }
            if (!column.getTypeColumn().isValid(value)) {
                return;
            }
            if (column.isPrimKeyColumn()) {
                String keyName = column.getNameColumn();
                Object keyValue = values.get(keyName);
                for (Row row : listRow) {
                    Object existingKey = row.getValue(keyName);
                    if (keyValue.equals(existingKey)) {
                        return;
                    }
                }
            }
        }
        Row newRow = new Row(values);
        listRow.add(newRow);
    }
}
