package index;

import model.Column;
import model.Database;
import model.Table;
import org.junit.jupiter.api.Test;
import types.DataType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static List<Column> carColumns() {
        return Arrays.asList(
                new Column("id", DataType.INTEGER, true),
                new Column("brand", DataType.STRING, false),
                new Column("price", DataType.DOUBLE, false));
    }

    @Test
    void findByOperators() {
        Index i = new Index("idx", "cars", "price", DataType.DOUBLE);
        i.add(25000.0, 1); i.add(15000.0, 2); i.add(30000.0, 3); i.add(25000.0, 4);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 4, 3)), i.find(">", 20000));
        assertEquals(Set.of(1, 4), i.find("=", 25000));
        assertEquals(Set.of(2), i.find("<=", 15000.0));
        assertNull(i.find("!=", 1));
        i.remove(25000.0, 1);
        assertEquals(Set.of(4), i.find("=", 25000.0));
    }

    @Test
    void selectUsesIndexAndIndexFollowsChanges() {
        Table t = new Table("cars", carColumns(), new ArrayList<>());
        t.insert(row("id", 1, "brand", "Toyota", "price", 25000.0));
        t.createIndex("idx_cars_brand", "brand");
        t.insert(row("id", 2, "brand", "BMW", "price", 15000.0));

        assertEquals(1, t.select("brand", "=", "BMW").size());
        assertTrue(t.getLastPlan().startsWith("INDEX SCAN"));

        t.update("id", "=", 2, row("brand", "Audi"));
        assertEquals(0, t.select("brand", "=", "BMW").size());
        assertEquals(1, t.select("brand", "=", "Audi").size());

        t.delete("brand", "=", "Toyota");
        assertEquals(0, t.select("brand", "=", "Toyota").size());
        assertEquals(1, t.getIndex("brand").size());

        t.select("brand", "!=", "Audi");
        assertEquals("FULL SCAN", t.getLastPlan());
        t.select("id", "=", 2);
        assertEquals("PRIMARY KEY LOOKUP", t.getLastPlan());
    }

    @Test
    void indexIsSavedAndRestored() throws Exception {
        Path dir = Files.createTempDirectory("javadb");
        Database db = new Database(dir.toString());
        Table t = db.createTable("cars", carColumns());
        t.insert(row("id", 1, "brand", "O'Neil", "price", 25000.0));
        t.insert(row("id", 2, "brand", "BMW", "price", 15000.0));
        db.saveTable(t);
        db.createIndex("idx_cars_brand", "cars", "brand");

        Database reopened = new Database(dir.toString());
        reopened.loadAll();
        Table t2 = reopened.getTable("cars");
        assertNotNull(t2.getIndex("brand"));
        assertEquals(1, t2.select("brand", "=", "O'Neil").size());
        assertTrue(t2.getLastPlan().startsWith("INDEX SCAN"));
    }

    @Test
    void staleIndexIsRebuiltOnLoad() throws Exception {
        Path dir = Files.createTempDirectory("javadb");
        Database db = new Database(dir.toString());
        Table t = db.createTable("cars", carColumns());
        db.createIndex("idx_cars_brand", "cars", "brand");
        // имитация сбоя: таблица сохранена, а индекс — нет
        t.insert(row("id", 1, "brand", "BMW"));
        new repository.JsonTableRepository(dir.toString()).save(t);

        Database reopened = new Database(dir.toString());
        reopened.loadAll();
        assertEquals(1, reopened.getTable("cars").select("brand", "=", "BMW").size());
    }
}
