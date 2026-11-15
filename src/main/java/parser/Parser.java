package parser;

import exceptions.DbException;
import index.Index;
import model.Column;
import model.Database;
import model.Row;
import model.Table;
import types.DataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает SQL-подобные запросы и выполняет их над {@link Database}.
 * Поддерживаются INSERT INTO, SELECT, UPDATE, DELETE, CREATE TABLE, DROP TABLE,
 * CREATE INDEX, DROP INDEX. После каждого изменения таблица сохраняется на диск.
 */
public class Parser {
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?is)^CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.*)\\)$");
    private static final Pattern DROP_TABLE =
            Pattern.compile("(?i)^DROP\\s+TABLE\\s+(\\w+)$");
    private static final Pattern CREATE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)$");
    private static final Pattern DROP_INDEX =
            Pattern.compile("(?i)^DROP\\s+INDEX\\s+(\\w+)$");

    private final Database database;

    /**
     * @param database база данных, над которой выполняются запросы
     */
    public Parser(Database database) {
        this.database = database;
    }

    /**
     * Разбирает и выполняет один запрос.
     *
     * @param input текст запроса (точка с запятой в конце необязательна)
     * @return текст результата для вывода в консоль
     * @throws DbException при синтаксической или логической ошибке
     */
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

        if (upper.startsWith("CREATE") || upper.startsWith("DROP")) {
            return ddl(sql);
        }

        throw new DbException("Команда не поддерживается: " + sql);
    }


    /**
     * INSERT INTO t [(c1, c2)] VALUES (...)[, (...)] — одна или несколько записей.
     */
    private String insert(String sql) {
        if (!sql.toUpperCase().matches("^INSERT\\s+INTO\\s.*")) {
            throw new DbException("INSERT: ожидалось INSERT INTO");
        }
        String rest = sql.substring(indexOfKeyword(sql, "INTO") + 4).trim();

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

        String tail = rest.substring(i).trim();
        List<String> columnNames = new ArrayList<>();

        if (tail.startsWith("(")) {
            int close = tail.indexOf(')');
            if (close < 0) {
                throw new DbException("INSERT: не закрыта скобка со списком колонок");
            }
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

        if (columnNames.isEmpty()) {
            for (Column col : table.getColumn()) {
                columnNames.add(col.getNameColumn());
            }
        }

        List<Map<String, Object>> batch = new ArrayList<>();
        while (true) {
            if (!tail.startsWith("(")) {
                throw new DbException("INSERT: ожидалась '(' перед значениями");
            }

            int close = findMatchingParen(tail, 0);
            if (close < 0) {
                throw new DbException("INSERT: не закрыта скобка со значениями");
            }

            List<String> rawValues = splitTopLevel(tail.substring(1, close), ',');

            if (columnNames.size() != rawValues.size()) {
                throw new DbException(String.format(
                        "INSERT: число колонок (%d) не совпадает с числом значений (%d)",
                        columnNames.size(), rawValues.size()));
            }

            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (int k = 0; k < columnNames.size(); k++) {
                Column col = table.getColumn(columnNames.get(k));
                rowMap.put(col.getNameColumn(), parseValue(col, rawValues.get(k)));
            }
            batch.add(rowMap);

            tail = tail.substring(close + 1).trim();
            if (tail.isEmpty()) {
                break;
            }
            if (!tail.startsWith(",")) {
                throw new DbException("INSERT: лишний текст после значений: " + tail);
            }
            tail = tail.substring(1).trim();
        }

        int added = table.insertAll(batch);
        database.saveTable(table);

        return "Добавлено строк: " + added;
    }

    /**
     * SELECT * | c1, c2 FROM t [WHERE условие].
     */
    private String select(String sql) {
        String rest = sql.substring("SELECT".length()).trim();

        int fromIndex = indexOfKeyword(rest, "FROM");
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

        int whereIndex = indexOfKeyword(afterFrom, "WHERE");
        String tableName;
        String whereCondition = null;

        if (whereIndex != -1) {
            tableName = afterFrom.substring(0, whereIndex).trim();
            whereCondition = afterFrom.substring(whereIndex + 5).trim();
        } else {
            tableName = afterFrom;
        }

        Table table = database.getTable(tableName);

        Condition c = parseCondition(table, whereCondition);
        List<Row> resultRows = table.select(c.column, c.operator, c.value);

        return formatResult(selectedColumns, resultRows, table)
                + "Строк: " + resultRows.size() + " | план: " + table.getLastPlan();
    }

    /**
     * DELETE FROM t [WHERE условие].
     */
    private String delete(String sql) {
        if (!sql.toUpperCase().matches("^DELETE\\s+FROM\\s.*")) {
            throw new DbException("DELETE: ожидалось DELETE FROM");
        }
        String rest = sql.substring(indexOfKeyword(sql, "FROM") + 4).trim();

        int whereIndex = indexOfKeyword(rest, "WHERE");
        String tableName;
        String whereCondition = null;

        if (whereIndex != -1) {
            tableName = rest.substring(0, whereIndex).trim();
            whereCondition = rest.substring(whereIndex + 5).trim();
        } else {
            tableName = rest;
        }

        Table table = database.getTable(tableName);

        Condition c = parseCondition(table, whereCondition);
        int deleted = table.delete(c.column, c.operator, c.value);
        database.saveTable(table);

        return "Удалено строк: " + deleted;
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
     * UPDATE t SET c1 = v1[, c2 = v2] [WHERE условие].
     */
    private String update(String sql) {
        String rest = sql.substring("UPDATE".length()).trim();

        int setIndex = indexOfKeyword(rest, "SET");
        if (setIndex == -1) {
            throw new DbException("UPDATE: ожидалось слово SET");
        }

        String tableName = rest.substring(0, setIndex).trim();
        String afterSet = rest.substring(setIndex + 3).trim();

        int whereIndex = indexOfKeyword(afterSet, "WHERE");
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

            Column col = table.getColumn(assign.substring(0, eqIndex).trim());
            updates.put(col.getNameColumn(), parseValue(col, assign.substring(eqIndex + 1)));
        }

        Condition c = parseCondition(table, whereCondition);
        int updatedCount = table.update(c.column, c.operator, c.value, updates);
        database.saveTable(table);

        return "Обновлено строк: " + updatedCount;
    }

    /**
     * CREATE TABLE, DROP TABLE, CREATE INDEX, DROP INDEX.
     */
    private String ddl(String sql) {
        Matcher m = CREATE_TABLE.matcher(sql);
        if (m.matches()) {
            List<Column> columns = new ArrayList<>();
            for (String def : splitTopLevel(m.group(2), ',')) {
                String[] parts = def.trim().split("\\s+");
                if (parts.length < 2) {
                    throw new DbException("CREATE TABLE: ожидалось 'имя ТИП [PRIMARY KEY]': " + def);
                }
                boolean pk = def.toUpperCase().matches(".*\\bPRIMARY\\s+KEY\\b.*");
                columns.add(new Column(parts[0], DataType.fromName(parts[1]), pk));
            }
            database.createTable(m.group(1), columns);
            return "Таблица " + m.group(1) + " создана";
        }

        m = DROP_TABLE.matcher(sql);
        if (m.matches()) {
            database.dropTable(m.group(1));
            return "Таблица " + m.group(1) + " удалена";
        }

        m = CREATE_INDEX.matcher(sql);
        if (m.matches()) {
            Index idx = database.createIndex(m.group(1), m.group(2), m.group(3));
            return "Индекс " + idx + " создан, записей: " + idx.size();
        }

        m = DROP_INDEX.matcher(sql);
        if (m.matches()) {
            database.dropIndex(m.group(1));
            return "Индекс " + m.group(1) + " удалён";
        }

        throw new DbException("Неверный синтаксис: " + sql);
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

    /**
     * Ищет ключевое слово целиком (не часть другого слова) вне кавычек.
     *
     * @return индекс начала слова или -1
     */
    private int indexOfKeyword(String s, String keyword) {
        boolean inString = false;
        int n = keyword.length();
        for (int p = 0; p + n <= s.length(); p++) {
            char c = s.charAt(p);
            if (c == '\'') {
                inString = !inString;
                continue;
            }
            if (inString || !s.regionMatches(true, p, keyword, 0, n)) {
                continue;
            }
            boolean startOk = p == 0 || !Character.isLetterOrDigit(s.charAt(p - 1)) && s.charAt(p - 1) != '_';
            boolean endOk = p + n == s.length() || !Character.isLetterOrDigit(s.charAt(p + n)) && s.charAt(p + n) != '_';
            if (startOk && endOk) {
                return p;
            }
        }
        return -1;
    }

    /** Условие WHERE: столбец, оператор, значение (column == null — без условия). */
    private static class Condition {
        String column;
        String operator;
        Object value;
    }

    /**
     * Разбирает условие вида {@code столбец оператор значение}.
     * Значение приводится к типу столбца.
     */
    private Condition parseCondition(Table table, String condition) {
        Condition result = new Condition();
        if (condition == null) {
            return result;
        }
        String[] ops = {">=", "<=", "!=", "<>", "=", ">", "<"};
        boolean inString = false;
        for (int p = 0; p < condition.length(); p++) {
            char ch = condition.charAt(p);
            if (ch == '\'') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            for (String op : ops) {
                if (condition.startsWith(op, p)) {
                    Column col = table.getColumn(condition.substring(0, p).trim());
                    result.column = col.getNameColumn();
                    result.operator = op;
                    result.value = parseValue(col, condition.substring(p + op.length()));
                    return result;
                }
            }
        }
        throw new DbException("Неверный оператор в WHERE: " + condition);
    }

    /**
     * Разбирает литерал по типу столбца (поддерживает все 5 типов и NULL).
     */
    private Object parseValue(Column col, String raw) {
        try {
            return col.getTypeColumn().parse(raw.trim());
        } catch (DbException e) {
            throw new DbException("Столбец " + col.getNameColumn() + ": " + e.getMessage());
        }
    }


    private String formatResult(List<String> columns, List<Row> rows, Table table) {
        List<String> actualCols = new ArrayList<>();

        if (columns.contains("*")) {
            for (Column col : table.getColumn()) {
                actualCols.add(col.getNameColumn());
            }
        } else {
            for (String name : columns) {
                actualCols.add(table.getColumn(name).getNameColumn());
            }
        }

        int[] widths = new int[actualCols.size()];
        for (int k = 0; k < actualCols.size(); k++) {
            widths[k] = actualCols.get(k).length();
            for (Row row : rows) {
                widths[k] = Math.max(widths[k], cell(row.getValue(actualCols.get(k))).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder line = new StringBuilder("+");
        for (int w : widths) {
            line.append("-".repeat(w + 2)).append("+");
        }
        line.append("\n");

        sb.append(line).append("|");
        for (int k = 0; k < actualCols.size(); k++) {
            sb.append(" ").append(String.format("%-" + widths[k] + "s", actualCols.get(k))).append(" |");
        }
        sb.append("\n").append(line);

        if (rows.isEmpty()) {
            sb.append("(пусто)\n");
        } else {
            for (Row row : rows) {
                sb.append("|");
                for (int k = 0; k < actualCols.size(); k++) {
                    sb.append(" ").append(String.format("%-" + widths[k] + "s",
                            cell(row.getValue(actualCols.get(k))))).append(" |");
                }
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private static String cell(Object val) {
        return val == null ? "NULL" : val.toString();
    }

}
