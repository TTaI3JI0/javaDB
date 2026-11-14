import model.Row;

import java.util.Objects;


public class ConditionEvaluator {
    public boolean evaluator(Row row,String columnName,String operator,Object expectedValue ){
        Object actualuvalue=row.getValue(columnName);
        if (operator.equals("=")){
            return Objects.equals(actualuvalue,expectedValue);
        }
        if (operator.equals("!=")){
            return !Objects.equals(actualuvalue,expectedValue);
        }
        if (operator.equals(">")){
            if (actualuvalue instanceof Number && expectedValue instanceof Number){
                return (((Number) actualuvalue).doubleValue()
                        >((Number) expectedValue).doubleValue());
            }
        }

        if (operator.equals("<")){
            if (actualuvalue instanceof Number && expectedValue instanceof Number){
                return (((Number) actualuvalue).doubleValue()
                        <((Number) expectedValue).doubleValue());
            }
        }
        if (operator.equals("<=")){
            if (actualuvalue instanceof Number && expectedValue instanceof Number){
                return (((Number) actualuvalue).doubleValue()
                        <=((Number) expectedValue).doubleValue());
            }
        }
        if (operator.equals(">=")){
            if (actualuvalue instanceof Number && expectedValue instanceof Number){
                return (((Number) actualuvalue).doubleValue()
                        >=((Number) expectedValue).doubleValue());
            }
        }
        return false;
    }
}
