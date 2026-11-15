package model;

import exceptions.DbException;
import java.util.HashMap;
import java.util.Map;

public class Database {

    private final Map<String, Table> tables = new HashMap<>();


    public void addTable(String name, Table table) {
        tables.put(name.toLowerCase(), table);
    }


    public Table getTable(String name) {
        Table t = tables.get(name.toLowerCase());
        if (t == null) {
            throw new DbException("Таблица " + name + " не найдена!");
        }
        return t;
    }
}