package Serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import model.Column;
import model.Table;
import model.Row;

import java.util.List;

public class Serializer {
    private Object convertNumber(Object va)
    private Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
    public String serializeTable(Table table){
        Table tab=new Table(table.getName(),table.getColumn(),table.getRow());
        String json=gson.toJson(tab);
        return json;
    }
    public Table deserializeTable(String json){
        Table tab=gson.fromJson(json,Table.class);
        return tab;
    }
}
