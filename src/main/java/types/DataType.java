package types;


public enum DataType {
    INTEGER,
    DOUBLE,
    STRING,
    BOOLEAN;


    public boolean isValid(Object value) {
        switch (this) {
            case INTEGER:
                return value instanceof Integer;
            case DOUBLE:
                return value instanceof Double;

            case STRING:
                return value instanceof String;
            default:
                return false;
        }
    }
}