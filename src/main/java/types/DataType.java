package types;

import exceptions.DbException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Поддерживаемые типы данных столбцов.
 * Каждый тип умеет проверять, приводить, разбирать из SQL-литерала и сравнивать свои значения.
 */
public enum DataType {
    /** Целое число ({@link Integer}). */
    INTEGER,
    /** Число с плавающей точкой ({@link Double}). */
    DOUBLE,
    /** Строка ({@link String}). */
    STRING,
    /** Логическое значение ({@link Boolean}). */
    BOOLEAN,
    /** Дата в формате ISO yyyy-MM-dd ({@link LocalDate}). */
    DATE;

    /**
     * Проверяет, что значение уже имеет Java-тип этого столбца.
     *
     * @param value значение
     * @return true, если тип совпадает
     */
    public boolean isValid(Object value) {
        switch (this) {
            case INTEGER: return value instanceof Integer;
            case DOUBLE:  return value instanceof Double;
            case STRING:  return value instanceof String;
            case BOOLEAN: return value instanceof Boolean;
            case DATE:    return value instanceof LocalDate;
            default:      return false;
        }
    }

    /**
     * Приводит значение к Java-типу столбца: Long/Integer → Integer, любое число → Double,
     * строка "2024-05-01" → LocalDate. NULL остаётся NULL.
     *
     * @param value исходное значение
     * @return значение нужного типа
     * @throws DbException если привести нельзя
     */
    public Object convert(Object value) {
        if (value == null || isValid(value)) {
            return value;
        }
        switch (this) {
            case INTEGER:
                if (value instanceof Long || value instanceof Short || value instanceof Byte) {
                    long l = ((Number) value).longValue();
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return (int) l;
                    }
                    throw new DbException("Число " + value + " выходит за диапазон INTEGER");
                }
                break;
            case DOUBLE:
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                break;
            case DATE:
                if (value instanceof String) {
                    try {
                        return LocalDate.parse((String) value);
                    } catch (DateTimeParseException e) {
                        throw new DbException("Неверная дата '" + value + "', ожидается формат yyyy-MM-dd");
                    }
                }
                break;
            default:
                break;
        }
        throw new DbException("Значение " + format(value) + " не подходит для типа " + this);
    }

    /**
     * Разбирает SQL-литерал: {@code 42}, {@code 3.14}, {@code 'text'}, {@code TRUE},
     * {@code '2024-05-01'}, {@code NULL}.
     *
     * @param raw текст литерала
     * @return значение нужного типа или null
     * @throws DbException если литерал не соответствует типу
     */
    public Object parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            switch (this) {
                case INTEGER: return Integer.parseInt(s);
                case DOUBLE:  return Double.parseDouble(s);
                case BOOLEAN:
                    if (s.equalsIgnoreCase("TRUE")) return Boolean.TRUE;
                    if (s.equalsIgnoreCase("FALSE")) return Boolean.FALSE;
                    break;
                case STRING:  return unquote(s);
                case DATE:    return convert(unquote(s));
                default:      break;
            }
        } catch (NumberFormatException e) {
            // ниже — общее сообщение
        }
        throw new DbException("Литерал " + s + " не подходит для типа " + this);
    }

    /**
     * Сравнивает два значения этого типа (NULL меньше любого значения).
     *
     * @return отрицательное, 0 или положительное число
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int compare(Object a, Object b) {
        Object x = convert(a);
        Object y = convert(b);
        if (x == null || y == null) {
            return x == null ? (y == null ? 0 : -1) : 1;
        }
        return ((Comparable) x).compareTo(y);
    }

    /**
     * Определяет тип по имени из CREATE TABLE (INT, INTEGER, DOUBLE, FLOAT, STRING, VARCHAR, TEXT, BOOLEAN, BOOL, DATE).
     *
     * @param name имя типа без учёта регистра
     * @return тип
     * @throws DbException для неизвестного типа
     */
    public static DataType fromName(String name) {
        String n = name.trim().toUpperCase();
        int paren = n.indexOf('(');           // VARCHAR(50) -> VARCHAR
        if (paren > 0) n = n.substring(0, paren).trim();
        switch (n) {
            case "INT": case "INTEGER":                     return INTEGER;
            case "DOUBLE": case "FLOAT": case "REAL":       return DOUBLE;
            case "STRING": case "VARCHAR": case "TEXT":     return STRING;
            case "BOOLEAN": case "BOOL":                    return BOOLEAN;
            case "DATE":                                    return DATE;
            default: throw new DbException("Неизвестный тип данных: " + name);
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        return s;
    }

    private static String format(Object v) {
        return v instanceof String ? "'" + v + "'" : String.valueOf(v);
    }
}
