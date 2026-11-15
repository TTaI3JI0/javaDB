package index;

import exceptions.DbException;
import types.DataType;

import java.util.*;

/**
 * Вторичный упорядоченный индекс по одному столбцу таблицы.
 * <p>
 * Хранит отображение «значение столбца → множество первичных ключей строк» в {@link TreeMap}
 * (красно-чёрное дерево). Поиск по равенству — O(log n), по диапазону (&gt;, &lt;, &gt;=, &lt;=) —
 * O(log n + k), где k — число найденных строк. Без индекса любой WHERE требует O(n).
 * Значения NULL в индекс не попадают.
 */
public class Index {
    private final String name;
    private final String tableName;
    private final String columnName;
    private final DataType type;
    private final TreeMap<Object, Set<Object>> map;

    /**
     * @param name       имя индекса (например, idx_cars_brand)
     * @param tableName  имя таблицы
     * @param columnName индексируемый столбец
     * @param type       тип столбца — задаёт порядок ключей
     */
    public Index(String name, String tableName, String columnName, DataType type) {
        this.name = name;
        this.tableName = tableName;
        this.columnName = columnName;
        this.type = type;
        this.map = new TreeMap<>(type::compare);
    }

    /**
     * Добавляет строку в индекс.
     *
     * @param value значение столбца
     * @param pk    первичный ключ строки
     */
    public void add(Object value, Object pk) {
        if (value == null) {
            return;
        }
        map.computeIfAbsent(type.convert(value), k -> new LinkedHashSet<>()).add(pk);
    }

    /**
     * Удаляет строку из индекса.
     *
     * @param value значение столбца
     * @param pk    первичный ключ строки
     */
    public void remove(Object value, Object pk) {
        if (value == null) {
            return;
        }
        Object key = type.convert(value);
        Set<Object> pks = map.get(key);
        if (pks != null) {
            pks.remove(pk);
            if (pks.isEmpty()) {
                map.remove(key);
            }
        }
    }

    /**
     * Ищет первичные ключи строк, удовлетворяющих условию {@code column op value}.
     *
     * @param op    оператор (=, &gt;, &gt;=, &lt;, &lt;=)
     * @param value значение из запроса
     * @return множество PK; null — если индекс не может обработать условие
     *         (оператор != или значение несовместимо по типу), тогда нужен полный проход
     */
    public Set<Object> find(String op, Object value) {
        Object key;
        try {
            key = type.convert(value);
        } catch (DbException e) {
            return null;
        }
        if (key == null) {
            return null;
        }
        Collection<Set<Object>> buckets;
        switch (op.trim()) {
            case "=":
                Set<Object> exact = map.get(key);
                return exact == null ? Collections.emptySet() : Collections.unmodifiableSet(exact);
            case ">":  buckets = map.tailMap(key, false).values(); break;
            case ">=": buckets = map.tailMap(key, true).values();  break;
            case "<":  buckets = map.headMap(key, false).values(); break;
            case "<=": buckets = map.headMap(key, true).values();  break;
            default:   return null;
        }
        Set<Object> result = new LinkedHashSet<>();
        for (Set<Object> b : buckets) {
            result.addAll(b);
        }
        return result;
    }

    /** Очищает индекс. */
    public void clear() {
        map.clear();
    }

    /** @return число записей (пар значение–PK) в индексе */
    public int size() {
        int n = 0;
        for (Set<Object> s : map.values()) {
            n += s.size();
        }
        return n;
    }

    /** @return содержимое индекса только для чтения (для сохранения на диск) */
    public Map<Object, Set<Object>> entries() {
        return Collections.unmodifiableMap(map);
    }

    /**
     * Проверяет, что строка с данным PK записана в индексе под данным значением.
     */
    public boolean contains(Object value, Object pk) {
        if (value == null) {
            return true;
        }
        Set<Object> pks = map.get(type.convert(value));
        return pks != null && pks.contains(pk);
    }

    /** @return имя индекса */
    public String getName() {
        return name;
    }

    /** @return имя таблицы */
    public String getTableName() {
        return tableName;
    }

    /** @return имя индексируемого столбца */
    public String getColumnName() {
        return columnName;
    }

    /** @return тип индексируемого столбца */
    public DataType getType() {
        return type;
    }

    @Override
    public String toString() {
        return name + " ON " + tableName + " (" + columnName + ")";
    }
}
