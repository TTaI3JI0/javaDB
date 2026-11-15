package repository;

import com.google.gson.Gson;
import index.Index;
import model.Table;
import serializer.Serializer;
import types.DataType;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Хранилище индексов (DAO): каждый индекс — файл {@code <каталог>/<имя>.json}
 * с описанием (таблица, столбец, тип) и содержимым «значение → список PK».
 */
public class IndexRepository {
    private final Path directory;
    private final Gson gson = new Serializer().gson();

    /** JSON-структура файла индекса. */
    private static class IndexFile {
        String name;
        String table;
        String column;
        DataType type;
        DataType pkType;
        Map<String, List<String>> entries = new LinkedHashMap<>();
    }

    /** Хранилище в каталоге {@code data/indexes}. */
    public IndexRepository() {
        this("data/indexes");
    }

    /**
     * @param directory каталог с файлами индексов
     */
    public IndexRepository(String directory) {
        this.directory = Path.of(directory);
    }

    /**
     * Сохраняет индекс (атомарно).
     *
     * @param idx   индекс
     * @param table таблица индекса (нужен тип PK)
     */
    public void save(Index idx, Table table) throws IOException {
        Files.createDirectories(directory);
        IndexFile f = new IndexFile();
        f.name = idx.getName();
        f.table = idx.getTableName();
        f.column = idx.getColumnName();
        f.type = idx.getType();
        f.pkType = table.getPrimaryKey().getTypeColumn();
        for (Map.Entry<Object, Set<Object>> e : idx.entries().entrySet()) {
            List<String> pks = new ArrayList<>();
            for (Object pk : e.getValue()) {
                pks.add(String.valueOf(pk));
            }
            f.entries.put(String.valueOf(e.getKey()), pks);
        }
        Serializer.writeAtomically(file(idx.getName()), gson.toJson(f));
    }

    /**
     * Загружает все индексы с диска.
     *
     * @return список индексов (ещё не подключённых к таблицам)
     */
    public List<Index> loadAll() throws IOException {
        List<Index> result = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return result;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*.json")) {
            for (Path p : ds) {
                try {
                    result.add(read(p));
                } catch (RuntimeException e) {
                    System.err.println("Пропущен повреждённый файл индекса " + p + ": " + e.getMessage());
                }
            }
        }
        return result;
    }

    private Index read(Path p) throws IOException {
        IndexFile f = gson.fromJson(Files.readString(p), IndexFile.class);
        if (f == null || f.name == null || f.table == null || f.column == null || f.type == null || f.pkType == null) {
            throw new IllegalArgumentException("неполное описание индекса");
        }
        Index idx = new Index(f.name, f.table, f.column, f.type);
        for (Map.Entry<String, List<String>> e : f.entries.entrySet()) {
            Object value = f.type.parse(literal(e.getKey(), f.type));
            for (String pk : e.getValue()) {
                idx.add(value, f.pkType.parse(literal(pk, f.pkType)));
            }
        }
        return idx;
    }

    /** Строки и даты в файле хранятся без кавычек — для DataType.parse их нужно добавить. */
    private static String literal(String raw, DataType type) {
        if (type == DataType.STRING || type == DataType.DATE) {
            return "'" + raw.replace("'", "''") + "'";
        }
        return raw;
    }

    /**
     * Удаляет файл индекса.
     *
     * @param indexName имя индекса
     */
    public void delete(String indexName) throws IOException {
        Files.deleteIfExists(file(indexName));
    }

    private Path file(String indexName) {
        return directory.resolve(indexName + ".json");
    }
}
