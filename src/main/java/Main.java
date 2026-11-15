import model.Column;
import model.Database;
import model.Table;
import parser.Parser;
import types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Database db = new Database();

        ArrayList<Column> ownerColumns = new ArrayList<>();
        ownerColumns.add(new Column("id", DataType.INTEGER, true));
        ownerColumns.add(new Column("name", DataType.STRING, false));

        Table owners = new Table("owners", ownerColumns, new ArrayList<>());

        db.addTable("owners", owners);

        ArrayList<Column> columns = new ArrayList<>();
        columns.add(new Column("id", DataType.INTEGER, true));
        columns.add(new Column("brand", DataType.STRING, false));

        columns.add(new Column("owner_id", DataType.INTEGER, false));

        Table carsTable = new Table("cars", columns, new ArrayList<>());
        db.addTable("cars", carsTable);

        Scanner scanner = new Scanner(System.in);
        Parser parser = new Parser(db);


        System.out.println("СУБД запущена");
        System.out.println("Введите запрос:");
        System.out.println("Выход - exit");
        System.out.println();

        while (true) {
            System.out.print("db> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("СУБД завершила работу.");
                break;
            }
            if (input.isEmpty()) {
                continue;
            }

            try {
                String result = parser.parse(input);
                System.out.println(result);
            } catch (Exception e) {
                System.out.println(">>> Ошибка: " + e.getMessage());
            }
        }
        scanner.close();
    }
}