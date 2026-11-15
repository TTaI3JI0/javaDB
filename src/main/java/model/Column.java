package model;

import types.DataType;

/**
 * Описание столбца таблицы: имя, тип и признак первичного ключа.
 */
public class Column {
    private String name;
    private DataType type;
    private boolean primaryKey;

    /**
     * @param name       имя столбца
     * @param type       тип данных
     * @param primaryKey является ли столбец первичным ключом
     */
    public Column(String name, DataType type, boolean primaryKey) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
    }

    /** @return имя столбца */
    public String getNameColumn() {
        return name;
    }

    /** @return тип данных столбца */
    public DataType getTypeColumn() {
        return type;
    }

    /** @return true, если столбец — первичный ключ */
    public boolean isPrimKeyColumn() {
        return primaryKey;
    }

    @Override
    public String toString() {
        return name + " " + type + (primaryKey ? " PRIMARY KEY" : "");
    }
}
