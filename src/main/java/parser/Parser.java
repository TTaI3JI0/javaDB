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
            tail = tail.substring(close + 1).trim(); // отрезаем колонки от остатка
        }

        if (!tail.toUpperCase().startsWith("VALUES")) {
            throw new DbException("INSERT: ожидалось VALUES");
        }
        tail = tail.substring("VALUES".length()).trim();

        if (!tail.startsWith("(")) {
            throw new DbException("INSERT: ожидалась '(' перед значениями");
        }

        int close = findMatchingParen(tail, 0);
        if (close < 0) {
            throw new DbException("INSERT: не закрыта скобка со значениями");
        }

        String valuesInner = tail.substring(1, close);
        List<String> rawValues = splitTopLevel(valuesInner, ',');

        return "Значения: " + rawValues;
    }

    /**
     * Правильная скобочная последовательность (индекс открывающей и закрывающей)
     */
    private int findMatchingParen(String s, int open) {
        int depth = 0;
        boolean inString = false;

        for (int p = open; p < s.length(); p++) {
            char c = s.charAt(p);

            if (c == '\'') {
                if (inString && p + 1 < s.length() && s.charAt(p + 1) == '\'') {
                    p++;
                    continue;
                }
                inString = !inString;
            } else if (!inString) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return p;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Разбивает строку по разделителю, игнорируя разделители внутри '...'.
     */
    private List<String> splitTopLevel(String s, char sep) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        for (int p = 0; p < s.length(); p++) {
            char c = s.charAt(p);

            if (c == '\'') {
                if (inString && p + 1 < s.length() && s.charAt(p + 1) == '\'') {
                    current.append('\'');
                    p++;
                    continue;
                }
                inString = !inString;
                current.append(c);
            } else if (c == sep && !inString) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result;
    }
}