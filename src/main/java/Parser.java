import exceptions.DbException;

public class Parser {
    private final Database database;

    public Parser(Database database) {
        this.database = database;
    }
    public String parse(String input) {
        if (input.contains("error")) {
            throw new DbException("Ошибка!");
        }
        return input;
    }
}