package serializer;
import model.Column;
import java.util.List;
import types.DataType;
import model.Row;
import java.util.Map;
import model.Table;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class SerializerTest {

    @Test
    void SerializerTest() {
        List<Column> columns = List.of(
                new Column("id", DataType.INTEGER, true),
                new Column("price", DataType.DOUBLE, false),
                new Column("name", DataType.STRING, false),
                new Column("active", DataType.BOOLEAN, false)
        );
        Row row = new Row(Map.of(
                "id", 25,
                "price", 12.5,
                "name", "Hello",
                "active", true
        ));

        Table table = new Table(
                "Products",
                columns,
                List.of(row)
        );

        Serializer serializer = new Serializer();

        String json=serializer.serializeTable(table);
        System.out.println("JSON:");
        System.out.println(json);

        Table tab=serializer.deserializeTable(json);
        for (Column col:tab.getColumn()){
            Object value = tab.getRow().get(0)
                    .getValue(col.getNameColumn());
            System.out.println(
                    col.getNameColumn()+":"+
                            value+"|"+
                            value.getClass().getSimpleName()
            );
        }
        assertEquals(25,tab.getRow().get(0).getValue("id"));
    }
    @Test
    void integerOverflowTest() {
        String json = """
            {
              "name": "Products",
              "listColumn": [
                {
                  "name": "id",
                  "type": "INTEGER",
                  "primaryKey": true
                }
              ],
              "listRow": [
                {
                  "values": {
                    "id": 3000000000
                  }
                }
              ]
            }
            """;

        Serializer serializer = new Serializer();

        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.deserializeTable(json)
        );
    }
    @Test
    void nullValueTest() {
        String json = """
            {
              "name": "Products",
              "listColumn": [
                {
                  "name": "id",
                  "type": "INTEGER",
                  "primaryKey": true
                }
              ],
              "listRow": [
                {
                  "values": {
                    "id": null
                  }
                }
              ]
            }
            """;

        Serializer serializer = new Serializer();

        Table tab = serializer.deserializeTable(json);

        assertNull(tab.getRow().get(0).getValue("id"));
    }

    @Test
    void dateRoundTripTest() {
        Table table = new Table("events",
                List.of(new Column("id", DataType.INTEGER, true),
                        new Column("day", DataType.DATE, false)),
                List.of(new Row(Map.of("id", 1, "day", java.time.LocalDate.of(2026, 11, 15)))));
        Serializer serializer = new Serializer();
        Table tab = serializer.deserializeTable(serializer.serializeTable(table));
        assertEquals(java.time.LocalDate.of(2026, 11, 15), tab.getRow().get(0).getValue("day"));
    }
}
