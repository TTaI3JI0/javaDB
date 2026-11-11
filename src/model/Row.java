package model;

import java.util.Map;
import java.util.HashMap;

public class Row {

    private Map<String,Object> values;
    public Row(Map<String,Object> values){
        this.values = new HashMap<>(values);
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
    @Override
    public String toString() {
        return values.toString();
    }
}
