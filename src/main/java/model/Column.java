package model;

public class Column {
    private String name;
    private types.DataType type;
    private boolean primaryKey;
    public Column(String name, types.DataType type, boolean primaryKey){
        this.name=name;
        this.type=type;
        this.primaryKey=primaryKey;
    }
    public String getNameColumn(){
        return name;
    }
    public types.DataType getTypeColumn(){
        return type;
    }
    public boolean isPrimKeyColumn(){
        return primaryKey;
    }

}
