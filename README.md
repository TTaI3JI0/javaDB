# javaDB — упрощённая СУБД на Java

## Архитектура

| Пакет | Классы | Назначение |
|---|---|---|
| — | Main | Консольный интерфейс |
| parser | Parser, ConditionEvaluator | Разбор запросов, условия WHERE |
| model | Database, Table, Row, Column | Таблицы и данные |
| index | Index | Индекс на TreeMap |
| repository | TableRepository, JsonTableRepository, IndexRepository | Хранение на диске (DAO/Repository) |
| serializer | Serializer, LocalDateAdapter | JSON (Gson) |
| types | DataType | INTEGER, DOUBLE, STRING, BOOLEAN, DATE |
| exceptions | DbException | Ошибки СУБД |

Паттерны: Repository/DAO (TableRepository, IndexRepository), Iterator (Table.iterator()).

### Диаграмма классов

classDiagram
    Main --> Parser
    Main --> Database
    Parser --> Database
    Parser --> ConditionEvaluator
    Database o-- Table
    Database --> TableRepository
    Database --> IndexRepository
    Table *-- Column
    Table *-- Row
    Table o-- Index
    Column --> DataType
    TableRepository <|.. JsonTableRepository
    JsonTableRepository --> Serializer

    class Database {
        +loadAll()
        +createTable()
        +dropTable()
        +saveTable()
        +createIndex()
    }
    class Table {
        +insertAll()
        +select()
        +update()
        +delete()
        +iterator()
    }
    class Index {
        -TreeMap map
        +add()
        +remove()
        +find()
    }

### Диаграмма последовательности (SELECT)

sequenceDiagram
    actor User
    User->>Main: SELECT * FROM cars WHERE brand = 'BMW'
    Main->>Parser: parse(sql)
    Parser->>Database: getTable("cars")
    Parser->>Table: select("brand", "=", "BMW")
    alt есть индекс
        Table->>Index: find("=", "BMW")
        Index-->>Table: первичные ключи
    else нет индекса
        Table->>Table: полный проход
    end
    Table-->>Parser: строки
    Parser-->>User: таблица результата

### Индексирование

Индекс — TreeMap<значение столбца, множество PK> (красно-чёрное дерево). Поиск по = — O(log n), по >, <, >=, <= — O(log n + k), без индекса — O(n). Индексы обновляются при INSERT/UPDATE/DELETE, сохраняются в data/indexes/ и загружаются при запуске.

## Тестирование производительности

Запрос SELECT * FROM cars WHERE ..., среднее время по 200 запускам (benchmark.IndexBenchmark).

| Строк | Условие | Без индекса, мс | С индексом, мс |
|---|---|---|---|
| 1 000 | brand = 'Brand500' | 0.063 | 0.013 |
| 10 000 | brand = 'Brand500' | 0.527 | 0.025 |
| 100 000 | brand = 'Brand500' | 2.869 | 0.008 |
| 100 000 | price > 99000 | 2.180 | 0.066 |

## Инструкция пользователя

Запуск: класс Main (JDK 21, Maven). Данные хранятся в папке data/.

INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);
INSERT INTO cars (id, brand) VALUES (2, 'BMW');
SELECT * FROM cars;
SELECT brand, price FROM cars WHERE price > 20000;
UPDATE cars SET price = 30000 WHERE id = 2;
DELETE FROM cars WHERE brand = 'Toyota';
CREATE TABLE shops (id INT PRIMARY KEY, title STRING, opened DATE);
DROP TABLE shops;
CREATE INDEX idx_cars_brand ON cars (brand);
exit

## Распределение работы

| Участник | Ответственность |
|---|---|
| Кирилл Кадетов | Модель данных, хранение на диске, сериализация, индексирование |
| Никита … | Консольный интерфейс, парсер запросов, вывод результатов |

## Выводы

Разработана СУБД с поддержкой пяти типов данных, первичных ключей и SQL-подобных запросов с сохранением данных на диск. Индекс на основе TreeMap ускоряет поиск: на 100 000 строк поиск по равенству стал быстрее примерно в 380 раз.

## Источники

1. Документация Java SE 21 API. https://docs.oracle.com/en/java/javase/21/docs/api/
2. Gson User Guide. https://github.com/google/gson/blob/main/UserGuide.md
3. Кормен Т. и др. Алгоритмы: построение и анализ. Гл. 13 «Красно-чёрные деревья».
