import exceptions.DbException;

public class Parser {
    public String parse(String input) {
        if (input.contains("error")) {
            throw new DbException("Тестовая ошибка базы данных!");
        }
        return "Парсер получил: " + input;
    }
}