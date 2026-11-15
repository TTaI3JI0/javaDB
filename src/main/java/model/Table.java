package model;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import exceptions.DbException;

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

    public String getName(){return name;}

    public void insert(Map<String, Object> values) {
        for (Column column : listColumn) {
            Object value = values.get(column.getNameColumn());
            if (value == null) {
                throw new exceptions.DbException("Ошибка INSERT: пропущено значение для колонки " + column.getNameColumn());
            }
            if (!column.getTypeColumn().isValid(value)) {
                throw new exceptions.DbException("Ошибка INSERT: неверный тип данных для колонки " + column.getNameColumn());
            }
            if (column.isPrimKeyColumn()) {
                String keyName = column.getNameColumn();
                Object keyValue = values.get(keyName);
                for (Row row : listRow) {
                    Object existingKey = row.getValue(keyName);
                    if (keyValue.equals(existingKey)) {
                        throw new exceptions.DbException("Ошибка INSERT: дубликат первичного ключа " + keyValue);
                    }
                }
            }
        }
        Row newRow = new Row(values);
        listRow.add(newRow);
    }

    public void update(Object primaryKey, Map<String, Object> values) {
        for (Column column : listColumn) {
            String nm=column.getNameColumn();
            if(values.containsKey(nm)){
                if(column.isPrimKeyColumn()){
                    return;
                }
                Object value = values.get(nm);
                if (value == null) {
                    return;
                }
                if (!column.getTypeColumn().isValid(value)) {
                    return;
                }
            }

        }
        for (String key:values.keySet()){
            boolean exists=false;
            for (Column col : listColumn){
                if (col.getNameColumn().equals(key)){
                    exists=true;
                    break;
                }
            }
            if(!exists){
                return;
            }
        }
        for (Column column : listColumn) {
            if (column.isPrimKeyColumn()) {
                String keyName = column.getNameColumn();
                for (Row row : listRow) {
                    Object existingKey = row.getValue(keyName);
                    if (primaryKey.equals(existingKey)) {
                        for (String key : values.keySet()) {
                            row.SetValues(key, values.get(key));
                        }
                        return;
                    }
                }
            }
        }
    }
    public void delete(Object primaryKey){
        for (Column column : listColumn) {
            if (column.isPrimKeyColumn()) {
                String keyName = column.getNameColumn();
                listRow.removeIf(row->row.getValue(keyName).equals(primaryKey));
            }
        }
    }

    public void remove(Row row) {
        listRow.remove(row);
    }

    public Iterator<Row> iterator(){
        return listRow.iterator();
    }
}
