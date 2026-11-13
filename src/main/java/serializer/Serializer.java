package serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import model.Column;
import model.Table;
import model.Row;
import types.DataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class Serializer {
    private Object convertNumber(Object values,DataType type){
        if (values instanceof Long){
            Long number = (Long) values;
            if (type == DataType.INTEGER){
                if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                    return number.intValue();
                }else {
                    throw new IllegalArgumentException(
                            "Число выходит за диапазон Integer");
                }
            }
            if(type==DataType.DOUBLE){
                return number.doubleValue();
            }
        }
        return values;
    }
    private Gson gson = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();
    public String serializeTable(Table table){
        return gson.toJson(table);
    }
    public Table deserializeTable(String json){
        Table tab=gson.fromJson(json,Table.class);
        for (Column column :tab.getColumn()){
            String name = column.getNameColumn();
            DataType type = column.getTypeColumn();
            for (Row row:tab.getRow()){
                Object value = row.getValue(name);
                Object converted = convertNumber(value, type);

                row.SetValues(name, converted);
            }
        }
        return tab;
    }
    public void saveToFile(Table table,String path)throws IOException{
        Path filepath=Path.of(path);
        Files.writeString(filepath , serializeTable(table));

    }
    public Table readToFile(String path)throws IOException{
        String json=Files.readString(Path.of(path));
        return deserializeTable(json);
    }

}
