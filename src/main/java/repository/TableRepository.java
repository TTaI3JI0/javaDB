package repository;

import model.Table;

import java.io.IOException;
import java.util.List;

/**
 * Хранилище таблиц (паттерн Repository/DAO): скрывает от остального кода формат и место хранения.
 */
public interface TableRepository {

    /** Сохраняет таблицу. */
    void save(Table tab) throws IOException;

    /** Загружает таблицу по имени. */
    Table load(String tableName) throws IOException;

    /** @return имена всех сохранённых таблиц */
    List<String> list() throws IOException;

    /** Удаляет сохранённую таблицу. */
    void delete(String tableName) throws IOException;
}
