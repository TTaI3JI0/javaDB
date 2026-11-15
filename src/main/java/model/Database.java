package model;

import exceptions.DbException;
import index.Index;
import repository.IndexRepository;
import repository.JsonTableRepository;
import repository.TableRepository;

import java.io.IOException;
import java.util.*;

/**
 * Каталог базы данных: держит таблицы и индексы в памяти и через репозитории
 * сохраняет их на диск / загружает при запуске.
 * <p>
 * Ошибки ввода-вывода оборачиваются в {@link DbException}, поэтому вызывающему коду
 * не нужно обрабатывать {@link IOException}.
 */
public class Database {

    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<String, Index> indexes = new LinkedHashMap<>();
    private final TableRepository repository;
    private final IndexRepository indexRepository;

    /** БД в каталоге {@code data} (таблицы) и {@code data/indexes} (индексы). */
    public Database() {
        this(new JsonTableRepository(), new IndexRepository());
    }

    /**
     * БД в указанном каталоге.
     *
     * @param dataDir каталог данных
     */
    public Database(String dataDir) {
        this(new JsonTableRepository(dataDir), new IndexRepository(dataDir + "/indexes"));
    }

    /**
     * @param repository хранилище таблиц
     */
    public Database(TableRepository repository) {
        this(repository, new IndexRepository());
    }

    /**
     * @param repository      хранилище таблиц
     * @param indexRepository хранилище индексов
     */
    public Database(TableRepository repository, IndexRepository indexRepository) {
        this.repository = repository;
        this.indexRepository = indexRepository;
    }

    // ---------------------------------------------------------------- загрузка

    /**
     * Загружает с диска все таблицы и индексы. Вызывается при запуске программы.
     * Повреждённые файлы пропускаются с предупреждением; индекс, не согласованный
     * с таблицей (после аварийного завершения), перестраивается и пересохраняется.
     */
    public void loadAll() {
        try {
            for (String name : repository.list()) {
                try {
                    Table t = repository.load(name);
                    tables.put(key(t.getName()), t);
                } catch (Exception e) {
                    System.err.println("Не удалось загрузить таблицу " + name + ": " + e.getMessage());
                }
            }
            for (Index idx : indexRepository.loadAll()) {
                Table t = tables.get(key(idx.getTableName()));
                if (t == null || t.findColumn(idx.getColumnName()) == null) {
                    System.err.println("Индекс " + idx.getName() + " ссылается на несуществующую таблицу/столбец — удалён");
                    indexRepository.delete(idx.getName());
                    continue;
                }
                if (t.attachIndex(idx)) {
                    System.err.println("Индекс " + idx.getName() + " не совпадал с данными и был перестроен");
                    indexRepository.save(idx, t);
                }
                indexes.put(key(idx.getName()), idx);
            }
        } catch (IOException e) {
            throw new DbException("Ошибка загрузки базы данных: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает одну таблицу с диска.
     *
     * @param name имя таблицы
     * @return таблица
     */
    public Table loadTable(String name) {
        try {
            Table t = repository.load(name);
            tables.put(key(name), t);
            return t;
        } catch (IOException e) {
            throw new DbException("Не удалось загрузить таблицу " + name + ": " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- таблицы

    /**
     * Добавляет готовую таблицу и сохраняет её на диск.
     *
     * @param name  имя таблицы
     * @param table таблица
     * @throws DbException если таблица уже существует
     */
    public void addTable(String name, Table table) {
        if (tables.containsKey(key(name))) {
            throw new DbException("Таблица " + name + " уже существует");
        }
        tables.put(key(name), table);
        saveTable(table);
    }

    /**
     * CREATE TABLE: создаёт таблицу и сохраняет её на диск.
     *
     * @param name    имя таблицы
     * @param columns столбцы (ровно один PRIMARY KEY)
     * @return созданная таблица
     */
    public Table createTable(String name, List<Column> columns) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new DbException("Недопустимое имя таблицы: " + name);
        }
        Table t = new Table(name, columns, new ArrayList<>());
        addTable(name, t);
        return t;
    }

    /**
     * DROP TABLE: удаляет таблицу, её файл и все её индексы.
     *
     * @param name имя таблицы
     */
    public void dropTable(String name) {
        Table t = getTable(name);
        try {
            for (Index idx : new ArrayList<>(t.getIndexes())) {
                indexes.remove(key(idx.getName()));
                indexRepository.delete(idx.getName());
            }
            repository.delete(t.getName());
        } catch (IOException e) {
            throw new DbException("Не удалось удалить таблицу " + name + ": " + e.getMessage(), e);
        }
        tables.remove(key(name));
    }

    /**
     * @param name имя таблицы без учёта регистра
     * @return таблица
     * @throws DbException если таблицы нет
     */
    public Table getTable(String name) {
        Table t = tables.get(key(name));
        if (t == null) {
            throw new DbException("Таблица " + name + " не найдена!");
        }
        return t;
    }

    /** @return true, если таблица существует */
    public boolean hasTable(String name) {
        return tables.containsKey(key(name));
    }

    /** @return имена всех таблиц */
    public List<String> getTableNames() {
        List<String> names = new ArrayList<>();
        for (Table t : tables.values()) {
            names.add(t.getName());
        }
        return names;
    }

    /**
     * Сохраняет таблицу и все её индексы. Вызывать после каждого INSERT/UPDATE/DELETE.
     * Порядок: сначала таблица, потом индексы — если программа упадёт между ними,
     * при следующем запуске индекс будет перестроен.
     *
     * @param table таблица
     */
    public void saveTable(Table table) {
        try {
            repository.save(table);
            for (Index idx : table.getIndexes()) {
                indexRepository.save(idx, table);
            }
        } catch (IOException e) {
            throw new DbException("Не удалось сохранить таблицу " + table.getName() + ": " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- индексы

    /**
     * CREATE INDEX name ON table (column): строит индекс и сохраняет его на диск.
     *
     * @return созданный индекс
     */
    public Index createIndex(String indexName, String tableName, String columnName) {
        if (indexes.containsKey(key(indexName))) {
            throw new DbException("Индекс " + indexName + " уже существует");
        }
        Table t = getTable(tableName);
        Index idx = t.createIndex(indexName, columnName);
        try {
            indexRepository.save(idx, t);
        } catch (IOException e) {
            t.detachIndex(idx);
            throw new DbException("Не удалось сохранить индекс " + indexName + ": " + e.getMessage(), e);
        }
        indexes.put(key(indexName), idx);
        return idx;
    }

    /**
     * DROP INDEX name: удаляет индекс из памяти и с диска.
     *
     * @param indexName имя индекса
     */
    public void dropIndex(String indexName) {
        Index idx = getIndex(indexName);
        if (idx == null) {
            throw new DbException("Индекс " + indexName + " не найден");
        }
        getTable(idx.getTableName()).detachIndex(idx);
        indexes.remove(key(indexName));
        try {
            indexRepository.delete(idx.getName());
        } catch (IOException e) {
            throw new DbException("Не удалось удалить файл индекса " + indexName + ": " + e.getMessage(), e);
        }
    }

    /**
     * @param indexName имя индекса
     * @return индекс или null
     */
    public Index getIndex(String indexName) {
        return indexes.get(key(indexName));
    }

    /** @return все индексы */
    public Collection<Index> getIndexes() {
        return Collections.unmodifiableCollection(indexes.values());
    }

    private static String key(String name) {
        return name.trim().toLowerCase();
    }
}
