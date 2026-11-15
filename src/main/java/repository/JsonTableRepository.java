package repository;

import model.Table;
import serializer.Serializer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранит каждую таблицу в отдельном файле {@code <каталог>/<имя>.json}.
 */
public class JsonTableRepository implements TableRepository {
    private final Serializer ser = new Serializer();
    private final Path directory;

    /** Хранилище в каталоге {@code data}. */
    public JsonTableRepository() {
        this("data");
    }

    /**
     * @param directory каталог с файлами таблиц
     */
    public JsonTableRepository(String directory) {
        this.directory = Path.of(directory);
    }

    @Override
    public void save(Table tab) throws IOException {
        Files.createDirectories(directory);
        ser.saveToFile(tab, file(tab.getName()).toString());
    }

    @Override
    public Table load(String tableName) throws IOException {
        return ser.readToFile(file(tableName).toString());
    }

    @Override
    public List<String> list() throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return names;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, "*.json")) {
            for (Path p : ds) {
                String f = p.getFileName().toString();
                names.add(f.substring(0, f.length() - ".json".length()));
            }
        }
        return names;
    }

    @Override
    public void delete(String tableName) throws IOException {
        Files.deleteIfExists(file(tableName));
    }

    private Path file(String tableName) {
        return directory.resolve(tableName + ".json");
    }
}
