import model.Column;
import model.Database;
import parser.Parser;
import types.DataType;

import java.util.ArrayList;
import java.util.Scanner;

/**
 * Точка входа: загружает БД с диска и в цикле выполняет запросы из консоли.
 */
public class Main {
    /**
     * @param args не используются
     */
    public static void main(String[] args) {

        Database db = new Database();
        db.loadAll();

        // При первом запуске создаём две связанные таблицы: owners.car_id -> cars.id
        if (!db.hasTable("cars")) {
            ArrayList<Column> columns = new ArrayList<>();
            columns.add(new Column("id", DataType.INTEGER, true));
            columns.add(new Column("brand", DataType.STRING, false));
            columns.add(new Column("price", DataType.DOUBLE, false));
            columns.add(new Column("is_new", DataType.BOOLEAN, false));
            db.createTable("cars", columns);
        }

        if (!db.hasTable("owners")) {
            ArrayList<Column> ownerColumns = new ArrayList<>();
            ownerColumns.add(new Column("id", DataType.INTEGER, true));
            ownerColumns.add(new Column("name", DataType.STRING, false));
            ownerColumns.add(new Column("birth_date", DataType.DATE, false));
            ownerColumns.add(new Column("car_id", DataType.INTEGER, false));
            db.createTable("owners", ownerColumns);
        }

        Scanner scanner = new Scanner(System.in);
        Parser parser = new Parser(db);


        System.out.println("СУБД запущена. Таблицы: " + db.getTableNames());
        System.out.println("Введите запрос:");
        System.out.println("Выход - exit");
        System.out.println();

        while (true) {
            System.out.print("db> ");
            if (!scanner.hasNextLine()) {
                break;
            }
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
