import main.java.model.Column;
import main.java.model.Row;
import main.java.model.Table;
import main.java.types.DataType;

void main() {
    Column col=new Column("id", DataType.INTEGER,true);
    Column col2=new Column("name", DataType.STRING,false);
    List<Column> listCol=new ArrayList<>();
    listCol.add(col);
    listCol.add(col2);
    List<Row> listRow=new ArrayList<>();

    Table tab=new Table("users",listCol,listRow);
    tab.insert(Map.of("id", 1, "name", "Alex"));
    tab.delete(1);
    Iterator<Row> itr=tab.iterator();
    while (itr.hasNext()){
        Row row=itr.next();
        System.out.println("1"+row.toString());

    }
    tab.insert(Map.of("id", 1, "name", "Alex"));
    tab.delete(2);
    Iterator<Row> itr1=tab.iterator();
    while (itr1.hasNext()){
        Row row=itr1.next();
        System.out.println("2 "+row.toString());

    }

}