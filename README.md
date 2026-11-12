# javaDB

## Какие классы нужны:
1. Точка входа - `Main` - читает строку, вызывает Parser, печатает результат
2. Парсер - `Parser` - превращает текст SQL в `Command` (внутри простой `split`/regex) - Command
3. Команда (интерфейс) - `Command` - контракт `execute(Database)` - Command, Абстракция
4. Вставка - `InsertCommand` - вставляет строки с проверкой PK - Command
5. Выборка - `SelectCommand` - выбирает строки по `WHERE` через Iterator - Command, Iterator
6. Обновление - `UpdateCommand` - обновляет строки по `WHERE` - Command
7. Удаление - `DeleteCommand` - удаляет строки по `WHERE` - Command
8. Результат - `ResultSet` - столбцы + строки + `printAsTable()`
---
9. Каталог БД / DAO - `Database` - хранит таблицы в памяти, через `Serializer` грузит/сохраняет на диск, `getTable/createTable/saveTable` - DAO
10. Хранилище (интерфейс) - `TableRepository` - контракт `load/save/list` - DAO, Абстракция
11. JSON-хранилище - `JsonTableRepository` - реализация `TableRepository` через `Serializer` - DAO
12. Таблица - `Table` - схема + строки, `insert/update/delete/iterator()` - Инкапсуляция, Iterator
13. Строка - `Row` - `Map<String,Object>` - Инкапсуляция
14. Столбец - `Column` - имя + тип + PK - Инкапсуляция
15. Тип данных - `DataType` - `parse` и `compare` для 5 типов - Полиморфизм
16. Проверка условия - `ConditionEvaluator` - вычисляет `WHERE` для всех команд
17. Сериализатор - `Serializer` - JSON ↔ объекты (Gson + `LocalDate`)
18. Исключение БД - `DbException` - одна unchecked-ошибка на всё - Наследование
