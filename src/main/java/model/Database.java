package model;

import exceptions.DbException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import repository.TableRepository;

public class Database {

    private final Map<String, Table> tables = new HashMap<>();
    private final TableRepository repository;

    public Database(TableRepository repository) {
        this.repository = repository;
    }

    public void addTable(String name, Table table) throws IOException {
        repository.save(table);
        tables.put(name.toLowerCase(), table);
    }


    public Table getTable(String name) throws DbException {
        Table t = tables.get(name.toLowerCase());
        if (t == null) {
            throw new DbException("Таблица " + name + " не найдена!");
        }
        return t;
    }

    public Table loadTable(String name) throws IOException {
        Table loadtable = repository.load(name);
        tables.put(name.toLowerCase(), loadtable);
        return loadtable;
    }
}