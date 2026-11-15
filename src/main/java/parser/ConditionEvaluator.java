package parser;

import exceptions.DbException;
import model.Row;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Вычисляет простое условие WHERE вида {@code столбец оператор значение}.
 * Поддерживаются операторы =, !=, &lt;&gt;, &gt;, &lt;, &gt;=, &lt;= для чисел, строк, дат и логических значений.
 */
public class ConditionEvaluator {

    /**
     * Проверяет условие для строки.
     *
     * @param row           строка
     * @param columnName    имя столбца
     * @param operator      оператор сравнения
     * @param expectedValue значение из запроса
     * @return true, если строка удовлетворяет условию
     */
    public boolean evaluator(Row row, String columnName, String operator, Object expectedValue) {
        return test(row.getValue(columnName), operator, expectedValue);
    }

    /**
     * Проверяет условие для пары значений.
     *
     * @throws DbException при неизвестном операторе или несравнимых типах
     */
    public static boolean test(Object actual, String operator, Object expected) {
        String op = operator.trim();
        if (actual == null || expected == null) {
            boolean bothNull = actual == null && expected == null;
            switch (op) {
                case "=":            return bothNull;
                case "!=": case "<>": return !bothNull;
                default:             return false;   // NULL не больше и не меньше ничего
            }
        }
        int c = compare(actual, expected);
        switch (op) {
            case "=":             return c == 0;
            case "!=": case "<>": return c != 0;
            case ">":             return c > 0;
            case "<":             return c < 0;
            case ">=":            return c >= 0;
            case "<=":            return c <= 0;
            default: throw new DbException("Неизвестный оператор: " + operator);
        }
    }

    /**
     * Сравнивает два значения разных совместимых типов: любые числа между собой,
     * дату со строкой вида 'yyyy-MM-dd', строки, логические значения.
     *
     * @throws DbException если значения несравнимы
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof LocalDate && b instanceof String) {
            b = toDate((String) b);
        } else if (a instanceof String && b instanceof LocalDate) {
            a = toDate((String) a);
        }
        if (a.getClass() == b.getClass() && a instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        throw new DbException("Нельзя сравнить " + a + " (" + a.getClass().getSimpleName()
                + ") и " + b + " (" + b.getClass().getSimpleName() + ")");
    }

    private static LocalDate toDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new DbException("Неверная дата '" + s + "', ожидается формат yyyy-MM-dd");
        }
    }
}
