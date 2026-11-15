package serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import model.Column;
import model.Row;
import model.Table;
import types.DataType;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Преобразует таблицы в JSON и обратно (Gson + {@link LocalDateAdapter}).
 * После чтения значения приводятся к типам столбцов: Gson читает числа как Long/Double,
 * а даты — как строки.
 */
public class Serializer {

    private final Gson gson = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .setPrettyPrinting()
            .create();

    /** @return общий настроенный экземпляр Gson (используется и для индексов) */
    public Gson gson() {
        return gson;
    }

    private Object convertValue(Object value, DataType type) {
        if (value instanceof Long) {
            long number = (Long) value;
            if (type == DataType.INTEGER) {
                if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                    return (int) number;
                }
                throw new IllegalArgumentException("Число выходит за диапазон Integer");
            }
            if (type == DataType.DOUBLE) {
                return (double) number;
            }
        }
        if (type == DataType.DATE && value instanceof String) {
            return LocalDate.parse((String) value);
        }
        return value;
    }

    /**
     * @param table таблица
     * @return JSON-представление
     */
    public String serializeTable(Table table) {
        return gson.toJson(table);
    }

    /**
     * @param json JSON-представление
     * @return таблица со значениями, приведёнными к типам столбцов
     * @throws IllegalArgumentException если файл повреждён или число не помещается в тип
     */
    public Table deserializeTable(String json) {
        Table tab = gson.fromJson(json, Table.class);
        if (tab == null || tab.getName() == null) {
            throw new IllegalArgumentException("Файл таблицы пуст или повреждён");
        }
        tab.validateSchema();
        for (Column column : tab.getColumn()) {
            String name = column.getNameColumn();
            DataType type = column.getTypeColumn();
            for (Row row : tab.getRow()) {
                row.SetValues(name, convertValue(row.getValue(name), type));
            }
        }
        return tab;
    }

    /**
     * Сохраняет таблицу. Запись атомарная: сначала во временный файл, затем переименование,
     * поэтому при аварийном завершении на диске остаётся либо старая, либо новая версия.
     */
    public void saveToFile(Table table, String path) throws IOException {
        writeAtomically(Path.of(path), serializeTable(table));
    }

    /**
     * @param path путь к файлу
     * @return прочитанная таблица
     */
    public Table readToFile(String path) throws IOException {
        return deserializeTable(Files.readString(Path.of(path)));
    }

    /**
     * Атомарно записывает текст в файл (tmp + move).
     */
    public static void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
