import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Parser parser = new Parser();

        System.out.println("СУБД запущена");
        System.out.println();
        System.out.println("Введите запрос:");
        System.out.println("Выход - exit");
        System.out.println();

        while (true) {
            System.out.print("db> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Успешное завершениие работы!");
                break;
            }

            // Пропускаем пустые строки
            if (input.isEmpty()) {
                continue;
            }

            try {
                // Отправляем ввод в парсер и получаем результат
                String result = parser.parse(input);
                System.out.println(result);
            } catch (DbException e) {
                // Ошибки БД
                System.err.println("Ошибка БД: " + e.getMessage());
            } catch (Exception e) {
                // Другие ошибки
                System.err.println("Неизвестная ошибка: " + e.getMessage());
            }
        }

        scanner.close();
    }
}