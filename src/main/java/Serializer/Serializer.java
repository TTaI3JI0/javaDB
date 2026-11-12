package Serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import model.Column;
import model.Table;
import model.Row;
import types.DataType;
import java.time.LocalDate;

import java.util.List;

public class Serializer {
    private Object convertNumber(Object values,DataType type){
        if (values instanceof Long){
            Long number = (Long) values;
            if(type==DataType.BOOLEAN){
                return number.doubleValue();
            }
            if (type == DataType.INTEGER){

                if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                    return number.intValue();
                }else {
                    throw new IllegalArgumentException(
                            "Число выходит за диапазон Integer");
                }
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
}
