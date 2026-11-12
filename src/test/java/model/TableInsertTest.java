package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import types.DataType;
import static org.junit.jupiter.api.Assertions.*;
import model.Table;
class TableInsertTest {

    @Test
    void insertValidRow() {
        Column id = new Column("id", DataType.INTEGER, true);
        Column name = new Column("name", DataType.STRING, false);

        List<Column> columns = List.of(id, name);
        List<Row> rows = new ArrayList<>();

        Table table = new Table("users", columns, rows);

        table.insert(Map.of("id", 1, "name", "Alex"));

        assertEquals(1, rows.size());
    }
    @Test
    void insertRepeatID() {
        Column id = new Column("id", DataType.INTEGER, true);
        Column name = new Column("name", DataType.STRING, false);

        List<Column> columns = List.of(id, name);
        List<Row> rows = new ArrayList<>();

        Table table = new Table("users", columns, rows);

        table.insert(Map.of("id", 1, "name", "Alex"));
        table.insert(Map.of("id", 1, "name", "Alex"));

        assertEquals(1, rows.size());
    }
}