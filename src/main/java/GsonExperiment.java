import com.google.gson.Gson;
import model.Column;
import types.DataType;

public class GsonExperiment {
    public static void main(String[] args) {
        Column col=new Column("id", DataType.INTEGER,true);
        Gson gson=new Gson();
        String json=gson.toJson(col);
        System.out.println(json);
        Column col2=gson.fromJson(json,Column.class);
        System.out.println(col2);
        System.out.println(col2.getNameColumn());
        System.out.println(col2.getTypeColumn());
        System.out.println(col2.isPrimKeyColumn());
    }
}
