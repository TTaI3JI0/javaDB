package benchmark;

import model.Column;
import model.Row;
import model.Table;
import types.DataType;

import java.time.LocalDate;
import java.util.*;

/**
 * Замер скорости SELECT ... WHERE без индекса и с индексом.
 * Запуск: {@code java -cp target/classes benchmark.IndexBenchmark}.
 * Печатает таблицу в формате Markdown для README.
 */
public class IndexBenchmark {

    private static final int REPEATS = 200;

    /**
     * @param args не используются
     */
    public static void main(String[] args) {
        System.out.println("| Строк | Запрос | Без индекса, мс | С индексом, мс | Ускорение |");
        System.out.println("|---|---|---|---|---|");
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            run(n, "brand = 'Brand500'", "brand", "=", "Brand500");
            run(n, "price > 99000.0", "price", ">", 99_000.0);
        }
    }

    private static void run(int n, String title, String col, String op, Object value) {
        Table t = buildTable(n);
        double without = measure(t, col, op, value);
        t.createIndex("idx_" + col, col);
        double with = measure(t, col, op, value);
        System.out.printf(Locale.ROOT, "| %,d | `%s` | %.4f | %.4f | ×%.0f |%n",
                n, title, without, with, without / with);
    }

    private static Table buildTable(int n) {
        Table t = new Table("cars", Arrays.asList(
                new Column("id", DataType.INTEGER, true),
                new Column("brand", DataType.STRING, false),
                new Column("price", DataType.DOUBLE, false),
                new Column("made", DataType.DATE, false),
                new Column("used", DataType.BOOLEAN, false)), new ArrayList<>());
        Random rnd = new Random(42);
        List<Map<String, Object>> batch = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> v = new HashMap<>();
            v.put("id", i);
            v.put("brand", "Brand" + (i % 1000));                 // 1000 разных марок
            v.put("price", rnd.nextDouble() * 100_000);            // ~1% строк > 99000
            v.put("made", LocalDate.of(2000, 1, 1).plusDays(i % 9000));
            v.put("used", i % 2 == 0);
            batch.add(v);
        }
        t.insertAll(batch);
        return t;
    }

    /** Среднее время одного запроса после прогрева JIT, мс. */
    private static double measure(Table t, String col, String op, Object value) {
        int sink = 0;
        for (int i = 0; i < 50; i++) {
            sink += t.select(col, op, value).size();             // прогрев
        }
        long start = System.nanoTime();
        for (int i = 0; i < REPEATS; i++) {
            List<Row> r = t.select(col, op, value);
            sink += r.size();
        }
        long elapsed = System.nanoTime() - start;
        if (sink == 42) System.out.print("");                  // чтобы JIT не выкинул цикл
        return elapsed / 1e6 / REPEATS;
    }
}
