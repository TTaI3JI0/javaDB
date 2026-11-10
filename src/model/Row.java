package model;

import java.util.Map;

public class Row {

    private Map<String,Object> values;
    public Row(Map<String,Object> values){
        this.values=values;
    }
    public Map<String,Object> getValuesRow(){
        return values;
    }
    public Object getValue(String key){
        return values.get(key);
    }
    public void SetValues(String key,Object val){
        values.put(key,val);

    }
}
