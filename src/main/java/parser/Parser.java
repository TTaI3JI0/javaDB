package parser;

import exceptions.DbException;
import model.Table;
import model.Database;
import model.Row;
import parser.ConditionEvaluator;

import java.util.ArrayList;
import java.util.List;
import types.DataType;
import java.util.LinkedHashMap;
import java.util.Map;

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

        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }

        String upper = sql.toUpperCase();

        if (upper.startsWith("INSERT")) {
            return insert(sql);
        }

        if (upper.startsWith("SELECT")) {
            return select(sql);
        }

        if (upper.startsWith("DELETE")) {
            return delete(sql);
        }

        if (upper.startsWith("UPDATE")) {
            return update(sql);
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
            tail = tail.substring(close + 1).trim();
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

        if (columnNames.isEmpty()) {
            for (model.Column col : table.getColumn()) {
                columnNames.add(col.getNameColumn());
            }
        }

        if (columnNames.size() != rawValues.size()) {
            throw new DbException(String.format(
                    "INSERT: число колонок (%d) не совпадает с числом значений (%d)",
                    columnNames.size(), rawValues.size()));
        }

        Map<String, Object> rowMap = new LinkedHashMap<>();
        for (int k = 0; k < columnNames.size(); k++) {
            String colName = columnNames.get(k);
            String raw = rawValues.get(k);

            DataType type = null;
            for (model.Column col : table.getColumn()) {
                if (col.getNameColumn().equalsIgnoreCase(colName)) {
                    type = col.getTypeColumn();
                    break;
                }
            }

            if (type == null) {
                throw new DbException("Колонка не найдена: " + colName);
            }

            // Конвертация
            String token = raw.trim();
            boolean isQuoted = token.startsWith("'") && token.endsWith("'");
            String unquoted = isQuoted ? token.substring(1, token.length() - 1).replace("''", "'") : token;

            try {
                if (type == DataType.INTEGER) {
                    rowMap.put(colName, Integer.parseInt(unquoted));
                } else if (type == DataType.DOUBLE) {
                    rowMap.put(colName, Double.parseDouble(unquoted));
                } else if (type == DataType.BOOLEAN) {
                    rowMap.put(colName, Boolean.parseBoolean(unquoted));
                } else {
                    rowMap.put(colName, unquoted);
                }
            } catch (NumberFormatException e) {
                throw new DbException("INSERT: неверный формат числа: " + unquoted);
            }
        }

        table.insert(rowMap);

        return "1 строка добавлена в " + tableName;
    }

    private String select(String sql) {
        String rest = sql.substring("SELECT".length()).trim();

        int fromIndex = rest.toUpperCase().indexOf("FROM");
        if (fromIndex == -1) {
            throw new DbException("SELECT: ожидалось слово FROM");
        }

        String columnsPart = rest.substring(0, fromIndex).trim();
        List<String> selectedColumns = new ArrayList<>();

        if (columnsPart.equals("*")) {
            selectedColumns.add("*");
        } else {
            selectedColumns = splitTopLevel(columnsPart, ',');
        }

        String afterFrom = rest.substring(fromIndex + 4).trim();

        int whereIndex = afterFrom.toUpperCase().indexOf("WHERE");
        String tableName;
        String whereCondition = null;

        if (whereIndex != -1) {
            tableName = afterFrom.substring(0, whereIndex).trim();
            whereCondition = afterFrom.substring(whereIndex + 5).trim();
        } else {
            tableName = afterFrom;
        }

        Table table = database.getTable(tableName);
        if (table == null) {
            throw new DbException("Таблица не найдена: " + tableName);
        }

        List<Row> resultRows = new ArrayList<>();
        for (Row row : table.getRow()) {
            if (whereCondition == null) {
                resultRows.add(row);
            } else {
                if (matchesCondition(row, whereCondition)) {
                    resultRows.add(row);
                }
            }
        }

        return formatResult(selectedColumns, resultRows, table);
    }

    private String delete(String sql) {
        String rest = sql.substring("DELETE FROM".length()).trim();

        int whereIndex = rest.toUpperCase().indexOf("WHERE");
        String tableName;
        String whereCondition = null;

        if (whereIndex != -1) {
            tableName = rest.substring(0, whereIndex).trim();
            whereCondition = rest.substring(whereIndex + 5).trim();
        } else {
            tableName = rest;
        }

        Table table = database.getTable(tableName);

        List<Row> toDelete = new ArrayList<>();
        for (Row row : table.getRow()) {
            if (whereCondition == null || matchesCondition(row, whereCondition)) {
                toDelete.add(row);
            }
        }

        for (Row row : toDelete) {
            table.remove(row);
        }

        return "Удалено строк: " + toDelete.size();
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

    private String update(String sql) {
        String rest = sql.substring("UPDATE".length()).trim();

        int setIndex = rest.toUpperCase().indexOf("SET");
        if (setIndex == -1) {
            throw new DbException("UPDATE: ожидалось слово SET");
        }

        String tableName = rest.substring(0, setIndex).trim();
        String afterSet = rest.substring(setIndex + 3).trim();

        int whereIndex = afterSet.toUpperCase().indexOf("WHERE");
        String setPart;
        String whereCondition = null;

        if (whereIndex != -1) {
            setPart = afterSet.substring(0, whereIndex).trim();
            whereCondition = afterSet.substring(whereIndex + 5).trim();
        } else {
            setPart = afterSet;
        }

        Table table = database.getTable(tableName);

        Map<String, Object> updates = new LinkedHashMap<>();
        List<String> assignments = splitTopLevel(setPart, ',');

        for (String assign : assignments) {
            int eqIndex = assign.indexOf('=');
            if (eqIndex == -1) {
                throw new DbException("UPDATE: неверное присваивание: " + assign);
            }

            String colName = assign.substring(0, eqIndex).trim();
            String rawValue = assign.substring(eqIndex + 1).trim();

            DataType type = null;
            for (model.Column col : table.getColumn()) {
                if (col.getNameColumn().equalsIgnoreCase(colName)) {
                    type = col.getTypeColumn();
                    break;
                }
            }
            if (type == null) {
                throw new DbException("UPDATE: колонка не найдена: " + colName);
            }

            String token = rawValue.trim();
            boolean isQuoted = token.startsWith("'") && token.endsWith("'");
            String unquoted = isQuoted ? token.substring(1, token.length() - 1).replace("''", "'") : token;

            if (unquoted.equalsIgnoreCase("NULL")) {
                updates.put(colName, null);
                continue;
            }

            try {
                if (type == DataType.INTEGER) {
                    updates.put(colName, Integer.parseInt(unquoted));
                } else if (type == DataType.DOUBLE) {
                    updates.put(colName, Double.parseDouble(unquoted));
                } else if (type == DataType.BOOLEAN) {
                    updates.put(colName, Boolean.parseBoolean(unquoted));
                } else {
                    updates.put(colName, unquoted);
                }
            } catch (NumberFormatException e) {
                throw new DbException("UPDATE: неверный формат числа: " + unquoted);
            }
        }

        int updatedCount = 0;
        for (Row row : table.getRow()) {
            if (whereCondition == null || matchesCondition(row, whereCondition)) {
                for (Map.Entry<String, Object> entry : updates.entrySet()) {
                    row.SetValues(entry.getKey(), entry.getValue());
                }
                updatedCount++;
            }
        }

        return "Обновлено строк: " + updatedCount;
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
    private boolean matchesCondition(Row row, String condition) {
        String[] ops = {">=", "<=", "!=", "=", ">", "<"};
        String foundOp = null;
        int opIndex = -1;

        for (String op : ops) {
            opIndex = condition.indexOf(op);
            if (opIndex != -1) {
                foundOp = op;
                break;
            }
        }
        if (foundOp == null) {
            throw new DbException("Неверный оператор в WHERE: " + condition);
        }

        String colName = condition.substring(0, opIndex).trim();
        String rawValue = condition.substring(opIndex + foundOp.length()).trim();

        if (rawValue.startsWith("'") && rawValue.endsWith("'")) {
            rawValue = rawValue.substring(1, rawValue.length() - 1).replace("''", "'");
        }

        Object actualValue = row.getValue(colName);
        Object expectedValue = rawValue;

        if (actualValue instanceof Integer) {
            expectedValue = Integer.parseInt(rawValue);
        } else if (actualValue instanceof Double) {
            expectedValue = Double.parseDouble(rawValue);
        } else if (actualValue instanceof Boolean) {
            expectedValue = Boolean.parseBoolean(rawValue);
        }

        ConditionEvaluator evaluator = new ConditionEvaluator();
        return evaluator.evaluator(row, colName, foundOp, expectedValue);
    }


    private String formatResult(List<String> columns, List<Row> rows, Table table) {
        List<String> actualCols = new ArrayList<>();

        if (columns.contains("*")) {
            for (model.Column col : table.getColumn()) {
                actualCols.add(col.getNameColumn());
            }
        } else {
            actualCols = columns;
        }

        StringBuilder sb = new StringBuilder();

        for (String col : actualCols) {
            sb.append(String.format("%-15s", col));
        }
        sb.append("\n").append("-".repeat(actualCols.size() * 15)).append("\n");

        if (rows.isEmpty()) {
            sb.append("(пусто)\n");
        } else {
            for (Row row : rows) {
                for (String col : actualCols) {
                    Object val = row.getValue(col);
                    sb.append(String.format("%-15s", val == null ? "NULL" : val.toString()));
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

}