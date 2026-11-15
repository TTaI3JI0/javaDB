package model;

import exceptions.DbException;
import org.junit.jupiter.api.Test;
import types.DataType;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TableTest {

    private Table cars() {
        return new Table("cars", Arrays.asList(
                new Column("id", DataType.INTEGER, true),
                new Column("brand", DataType.STRING, false),
                new Column("price", DataType.DOUBLE, false),
                new Column("made", DataType.DATE, false),
                new Column("used", DataType.BOOLEAN, false)), new ArrayList<>());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void insertWithSomeColumnsFillsNulls() {
        Table t = cars();
        t.insert(row("id", 2, "brand", "BMW"));
        Row r = t.getByPk(2);
        assertEquals("BMW", r.getValue("brand"));
        assertNull(r.getValue("price"));
    }

    @Test
    void insertConvertsTypes() {
        Table t = cars();
        t.insert(row("id", 1, "price", 25000, "made", "2020-05-01"));   // Integer -> Double, String -> Date
        Row r = t.getByPk(1);
        assertEquals(25000.0, r.getValue("price"));
        assertEquals(LocalDate.of(2020, 5, 1), r.getValue("made"));
    }

    @Test
    void duplicatePrimaryKeyThrows() {
        Table t = cars();
        t.insert(row("id", 1, "brand", "Toyota"));
        assertThrows(DbException.class, () -> t.insert(row("id", 1, "brand", "BMW")));
        assertEquals(1, t.getRow().size());
    }

    @Test
    void batchInsertIsAtomic() {
        Table t = cars();
        assertThrows(DbException.class, () -> t.insertAll(Arrays.asList(
                row("id", 1), row("id", 2), row("id", 1))));
        assertEquals(0, t.getRow().size());
    }

    @Test
    void wrongTypeAndNullPkThrow() {
        Table t = cars();
        assertThrows(DbException.class, () -> t.insert(row("id", 1, "price", "дорого")));
        assertThrows(DbException.class, () -> t.insert(row("brand", "BMW")));
        assertThrows(DbException.class, () -> t.insert(row("id", 1, "color", "red")));
    }

    @Test
    void updateAndDeleteByCondition() {
        Table t = cars();
        t.insert(row("id", 1, "brand", "Toyota", "price", 25000.0));
        t.insert(row("id", 2, "brand", "BMW", "price", 15000.0));
        t.insert(row("id", 3, "brand", "Toyota", "price", 18000.0));

        assertEquals(1, t.update("id", "=", 2, row("price", 30000)));
        assertEquals(30000.0, t.getByPk(2).getValue("price"));

        assertEquals(2, t.select("price", ">", 20000).size());
        assertEquals(2, t.delete("brand", "=", "Toyota"));
        assertEquals(1, t.getRow().size());
        assertThrows(DbException.class, () -> t.update(null, null, null, row("id", 5)));
    }

    @Test
    void dateAndBooleanConditions() {
        Table t = cars();
        t.insert(row("id", 1, "made", "2019-01-01", "used", true));
        t.insert(row("id", 2, "made", "2022-01-01", "used", false));
        assertEquals(1, t.select("made", ">", "2020-01-01").size());
        assertEquals(1, t.select("used", "=", true).size());
    }
}
