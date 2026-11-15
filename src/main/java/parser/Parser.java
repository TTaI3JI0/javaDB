package parser;

import exceptions.DbException;
import model.Table;
import model.Database;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final Database database;

    public Parser(Database database) {
        this.database = database;
    }

    public String parse(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String sql = input.trim();
        String upper = sql.toUpperCase();

        if (upper.startsWith("INSERT")) {
            return insert(sql);
        }

        throw new DbException("Команда не поддерживается: " + sql);
    }


    private String insert(String sql) {
        String rest = sql.substring("INSERT INTO".length()).trim();

        int i = 0;
        while (i < rest.length()
                && !Character.isWhitespace(rest.charAt(i))
                && rest.charAt(i) != '(') {
            i++;
        }
        String tableName = rest.substring(0, i);
        if (tableName.isEmpty()) {
            throw new DbException("INSERT: не указано имя таблицы");
        }

        Table table = database.getTable(tableName);
        if (table == null) {
            throw new DbException("Таблица не найдена: " + tableName);
        }

        String tail = rest.substring(i).trim();
        List<String> columnNames = new ArrayList<>();

        if (tail.startsWith("(")) {
            int close = tail.indexOf(')');
            String columnsInner = tail.substring(1, close);

            for (String part : columnsInner.split(",")) {
                columnNames.add(part.trim());
            }
        }

        if (columnNames.isEmpty()) {
            return "Колонки не указаны";
        }
        return "Колонки: " + columnNames;
    }
}