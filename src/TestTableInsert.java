import main.java.model.Column;
import main.java.model.Table;
import main.java.types.DataType;
import main.java.model.Row;

public static void main(String[] args) {
    Column col=new Column("id", DataType.INTEGER,true);
    Column col2=new Column("name", DataType.STRING,false);
    List<Column> listCol=new ArrayList<>();
    listCol.add(col);
    listCol.add(col2);
    List<Row> listRow=new ArrayList<>();

    Table tab=new Table("users",listCol,listRow);
    tab.insert(Map.of("id", 1, "name", "Alex"));
    tab.insert(Map.of("id", 1, "name", "Alex"));
    tab.insert(Map.of("id", 1, "цкщтп1", "Alex"));

    System.out.println(tab.getRow().size());
}
