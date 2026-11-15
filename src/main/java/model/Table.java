package model;

import exceptions.DbException;
import index.Index;
import parser.ConditionEvaluator;

import java.util.*;

/**
 * Таблица: схема (список столбцов) и строки.
 * <p>
 * Все изменения данных идут только через методы таблицы — так первичный ключ
 * и вторичные индексы всегда остаются согласованными с данными.
 * Условия WHERE передаются тройкой «столбец, оператор, значение»;
 * столбец {@code null} означает «без WHERE» (все строки).
 */
public class Table {
    private String name;
    private List<Column> listColumn;
    private List<Row> listRow;

    /** Первичный (хеш-)индекс PK → строка. Не сериализуется, строится при первом обращении. */
    private transient Map<Object, Row> byPk;
    /** Вторичные индексы: имя столбца → индекс. Не сериализуются вместе с таблицей. */
    private transient Map<String, Index> indexes;
    /** План последнего выполненного поиска (для вывода и отчёта). */
    private transient String lastPlan;

    /**
     * @param name       имя таблицы
     * @param listColumn столбцы; ровно один должен быть первичным ключом
     * @param listRow    начальные строки (могут быть null)
     * @throws DbException если первичных ключей не один
     */
    public Table(String name, List<Column> listColumn, List<Row> listRow) {
        this.name = name;
        this.listColumn = new ArrayList<>(listColumn);
        this.listRow = listRow == null ? new ArrayList<>() : new ArrayList<>(listRow);
        validateSchema();
    }

    // ---------------------------------------------------------------- схема

    /** @return имя таблицы */
    public String getName() {
        return name;
    }

    /** @return строки только для чтения (изменять через insert/update/delete) */
    public List<Row> getRow() {
        return Collections.unmodifiableList(rows());
    }

    /** @return столбцы только для чтения */
    public List<Column> getColumn() {
        return Collections.unmodifiableList(listColumn);
    }

    /**
     * @param columnName имя столбца без учёта регистра
     * @return столбец
     * @throws DbException если столбца нет
     */
    public Column getColumn(String columnName) {
        Column c = findColumn(columnName);
        if (c == null) {
            throw new DbException("В таблице " + name + " нет столбца " + columnName);
        }
        return c;
    }

    /**
     * @param columnName имя столбца без учёта регистра
     * @return столбец или null
     */
    public Column findColumn(String columnName) {
        for (Column c : listColumn) {
            if (c.getNameColumn().equalsIgnoreCase(columnName.trim())) {
                return c;
            }
        }
        return null;
    }

    /** @return столбец первичного ключа */
    public Column getPrimaryKey() {
        for (Column c : listColumn) {
            if (c.isPrimKeyColumn()) {
                return c;
            }
        }
        throw new DbException("В таблице " + name + " нет первичного ключа");
    }

    /**
     * Проверяет схему: есть столбцы, имена уникальны, ровно один первичный ключ.
     *
     * @throws DbException при нарушении
     */
    public void validateSchema() {
        if (listColumn == null || listColumn.isEmpty()) {
            throw new DbException("Таблица " + name + " должна содержать хотя бы один столбец");
        }
        Set<String> names = new HashSet<>();
        int pkCount = 0;
        for (Column c : listColumn) {
            if (!names.add(c.getNameColumn().toLowerCase())) {
                throw new DbException("Повторяющийся столбец " + c.getNameColumn());
            }
            if (c.isPrimKeyColumn()) {
                pkCount++;
            }
        }
        if (pkCount != 1) {
            throw new DbException("Таблица " + name + " должна иметь ровно один PRIMARY KEY, найдено: " + pkCount);
        }
    }

    // ---------------------------------------------------------------- вставка

    /**
     * Вставляет одну запись. Отсутствующие столбцы получают NULL.
     *
     * @param values имя столбца → значение
     * @throws DbException если PK пустой или уже существует, неизвестный столбец или неверный тип
     */
    public void insert(Map<String, Object> values) {
        insertAll(Collections.singletonList(values));
    }

    /**
     * Вставляет несколько записей атомарно: если хотя бы одна некорректна
     * (в т.ч. дубликат PK внутри пачки), не вставляется ни одна.
     *
     * @param batch список записей
     * @return число вставленных строк
     */
    public int insertAll(List<Map<String, Object>> batch) {
        String pkName = getPrimaryKey().getNameColumn();
        List<Row> prepared = new ArrayList<>();
        Set<Object> batchKeys = new HashSet<>();
        for (Map<String, Object> values : batch) {
            Row row = prepareRow(values);
            Object pk = row.getValue(pkName);
            if (byPk().containsKey(pk) || !batchKeys.add(pk)) {
                throw new DbException("Нарушение уникальности первичного ключа: " + pkName + " = " + pk);
            }
            prepared.add(row);
        }
        for (Row row : prepared) {
            Object pk = row.getValue(pkName);
            rows().add(row);
            byPk().put(pk, row);
            for (Index idx : indexes().values()) {
                idx.add(row.getValue(idx.getColumnName()), pk);
            }
        }
        return prepared.size();
    }

    private Row prepareRow(Map<String, Object> values) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Column c = getColumn(e.getKey());
            normalized.put(c.getNameColumn(), convert(c, e.getValue()));
        }
        for (Column c : listColumn) {
            normalized.putIfAbsent(c.getNameColumn(), null);
        }
        Column pk = getPrimaryKey();
        if (normalized.get(pk.getNameColumn()) == null) {
            throw new DbException("Первичный ключ " + pk.getNameColumn() + " не может быть NULL");
        }
        return new Row(normalized);
    }

    private static Object convert(Column c, Object value) {
        try {
            return c.getTypeColumn().convert(value);
        } catch (DbException e) {
            throw new DbException("Столбец " + c.getNameColumn() + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- выборка

    /**
     * Возвращает строки, удовлетворяющие условию {@code column operator value}.
     * Стратегия выбирается автоматически:
     * <ul>
     *   <li>PRIMARY KEY LOOKUP — {@code pk = значение}, хеш-таблица, O(1);</li>
     *   <li>INDEX SCAN — по столбцу есть индекс, O(log n + k);</li>
     *   <li>FULL SCAN — полный проход итератором, O(n).</li>
     * </ul>
     *
     * @param column   столбец условия или null — все строки
     * @param operator оператор
     * @param value    значение
     * @return список строк (новый список, сами строки — из таблицы)
     */
    public List<Row> select(String column, String operator, Object value) {
        if (column == null) {
            lastPlan = "FULL SCAN";
            return new ArrayList<>(rows());
        }
        Column c = getColumn(column);
        String col = c.getNameColumn();

        if (c.isPrimKeyColumn() && "=".equals(operator.trim())) {
            Object key = tryConvert(c, value);
            if (key != null) {
                lastPlan = "PRIMARY KEY LOOKUP";
                Row r = byPk().get(key);
                return r == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(r));
            }
        }

        Index idx = indexes().get(col);
        if (idx != null) {
            Set<Object> pks = idx.find(operator, value);
            if (pks != null) {
                lastPlan = "INDEX SCAN (" + idx.getName() + ")";
                List<Row> result = new ArrayList<>(pks.size());
                for (Object pk : pks) {
                    result.add(byPk().get(pk));
                }
                return result;
            }
        }

        lastPlan = "FULL SCAN";
        List<Row> result = new ArrayList<>();
        Iterator<Row> it = iterator();
        while (it.hasNext()) {
            Row r = it.next();
            if (ConditionEvaluator.test(r.getValue(col), operator, value)) {
                result.add(r);
            }
        }
        return result;
    }

    private static Object tryConvert(Column c, Object value) {
        try {
            return c.getTypeColumn().convert(value);
        } catch (DbException e) {
            return null;
        }
    }

    /**
     * Находит строку по первичному ключу за O(1).
     *
     * @param primaryKey значение PK
     * @return строка или null
     */
    public Row getByPk(Object primaryKey) {
        return byPk().get(tryConvert(getPrimaryKey(), primaryKey));
    }

    /** @return способ выполнения последнего поиска: FULL SCAN, INDEX SCAN (...) или PRIMARY KEY LOOKUP */
    public String getLastPlan() {
        return lastPlan;
    }

    // ---------------------------------------------------------------- обновление

    /**
     * Обновляет строки, удовлетворяющие условию.
     *
     * @param column    столбец условия или null — все строки
     * @param operator  оператор
     * @param value     значение условия
     * @param newValues столбец → новое значение
     * @return число обновлённых строк
     * @throws DbException при попытке изменить PK, неизвестном столбце или неверном типе
     */
    public int update(String column, String operator, Object value, Map<String, Object> newValues) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : newValues.entrySet()) {
            Column c = getColumn(e.getKey());
            if (c.isPrimKeyColumn()) {
                throw new DbException("Изменять первичный ключ " + c.getNameColumn() + " нельзя");
            }
            changes.put(c.getNameColumn(), convert(c, e.getValue()));
        }
        String pkName = getPrimaryKey().getNameColumn();
        List<Row> matched = select(column, operator, value);
        for (Row row : matched) {
            Object pk = row.getValue(pkName);
            for (Map.Entry<String, Object> e : changes.entrySet()) {
                Index idx = indexes().get(e.getKey());
                if (idx != null) {
                    idx.remove(row.getValue(e.getKey()), pk);
                    idx.add(e.getValue(), pk);
                }
                row.SetValues(e.getKey(), e.getValue());
            }
        }
        return matched.size();
    }

    /**
     * Обновляет строку с указанным первичным ключом.
     *
     * @return число обновлённых строк (0 или 1)
     */
    public int update(Object primaryKey, Map<String, Object> values) {
        return update(getPrimaryKey().getNameColumn(), "=", primaryKey, values);
    }

    // ---------------------------------------------------------------- удаление

    /**
     * Удаляет строки, удовлетворяющие условию.
     *
     * @param column   столбец условия или null — все строки
     * @param operator оператор
     * @param value    значение условия
     * @return число удалённых строк
     */
    public int delete(String column, String operator, Object value) {
        List<Row> matched = select(column, operator, value);
        if (matched.isEmpty()) {
            return 0;
        }
        String pkName = getPrimaryKey().getNameColumn();
        Set<Row> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        toRemove.addAll(matched);
        rows().removeIf(toRemove::contains);           // один проход O(n), без ConcurrentModification
        for (Row row : matched) {
            Object pk = row.getValue(pkName);
            byPk().remove(pk);
            for (Index idx : indexes().values()) {
                idx.remove(row.getValue(idx.getColumnName()), pk);
            }
        }
        return matched.size();
    }

    /**
     * Удаляет строку с указанным первичным ключом.
     *
     * @return число удалённых строк (0 или 1)
     */
    public int delete(Object primaryKey) {
        return delete(getPrimaryKey().getNameColumn(), "=", primaryKey);
    }

    /**
     * Итератор по строкам (паттерн Iterator). Удаление через итератор запрещено,
     * чтобы не рассинхронизировать индексы.
     */
    public Iterator<Row> iterator() {
        return Collections.unmodifiableList(rows()).iterator();
    }

    // ---------------------------------------------------------------- индексы

    /**
     * Создаёт индекс по столбцу и заполняет его существующими строками.
     *
     * @param indexName  имя индекса
     * @param columnName столбец
     * @return созданный индекс
     * @throws DbException если столбец уже проиндексирован
     */
    public Index createIndex(String indexName, String columnName) {
        Column c = getColumn(columnName);
        if (indexes().containsKey(c.getNameColumn())) {
            throw new DbException("Столбец " + c.getNameColumn() + " уже проиндексирован: "
                    + indexes().get(c.getNameColumn()).getName());
        }
        Index idx = new Index(indexName, name, c.getNameColumn(), c.getTypeColumn());
        rebuildIndex(idx);
        indexes().put(c.getNameColumn(), idx);
        return idx;
    }

    /**
     * Подключает готовый (например, загруженный с диска) индекс.
     * Если он не согласован с данными (сбой между записью таблицы и индекса) — перестраивает его.
     *
     * @param idx индекс
     * @return true, если индекс пришлось перестроить
     */
    public boolean attachIndex(Index idx) {
        Column c = getColumn(idx.getColumnName());
        boolean rebuilt = false;
        if (!isConsistent(idx)) {
            rebuildIndex(idx);
            rebuilt = true;
        }
        indexes().put(c.getNameColumn(), idx);
        return rebuilt;
    }

    /**
     * Отключает индекс от таблицы.
     *
     * @param idx индекс
     */
    public void detachIndex(Index idx) {
        indexes().remove(idx.getColumnName());
    }

    /**
     * @param columnName столбец
     * @return индекс по столбцу или null
     */
    public Index getIndex(String columnName) {
        Column c = findColumn(columnName);
        return c == null ? null : indexes().get(c.getNameColumn());
    }

    /** @return все индексы таблицы */
    public Collection<Index> getIndexes() {
        return Collections.unmodifiableCollection(indexes().values());
    }

    /**
     * Заполняет индекс заново по данным таблицы.
     *
     * @param idx индекс
     */
    public void rebuildIndex(Index idx) {
        idx.clear();
        String pkName = getPrimaryKey().getNameColumn();
        for (Row r : rows()) {
            idx.add(r.getValue(idx.getColumnName()), r.getValue(pkName));
        }
    }

    private boolean isConsistent(Index idx) {
        String pkName = getPrimaryKey().getNameColumn();
        int expected = 0;
        for (Row r : rows()) {
            Object v = r.getValue(idx.getColumnName());
            if (v != null) {
                expected++;
                if (!idx.contains(v, r.getValue(pkName))) {
                    return false;
                }
            }
        }
        return expected == idx.size();
    }

    // ---------------------------------------------------------------- служебное

    /** Gson создаёт объект без конструктора, поэтому поля инициализируются лениво. */
    private List<Row> rows() {
        if (listRow == null) {
            listRow = new ArrayList<>();
        }
        return listRow;
    }

    private Map<String, Index> indexes() {
        if (indexes == null) {
            indexes = new LinkedHashMap<>();
        }
        return indexes;
    }

    private Map<Object, Row> byPk() {
        if (byPk == null) {
            byPk = new HashMap<>();
            String pkName = getPrimaryKey().getNameColumn();
            for (Row r : rows()) {
                byPk.put(r.getValue(pkName), r);
            }
        }
        return byPk;
    }

    @Override
    public String toString() {
        return name + listColumn + " rows=" + rows().size();
    }
}
