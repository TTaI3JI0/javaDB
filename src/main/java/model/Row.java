package model;

import java.util.HashMap;
import java.util.Map;

/**
 * Строка таблицы: отображение «имя столбца → значение».
 */
public class Row {

    private Map<String, Object> values;

    /**
     * @param values значения строки (копируются)
     */
    public Row(Map<String, Object> values) {
        this.values = new HashMap<>(values);
    }

    /** @return все значения строки */
    public Map<String, Object> getValuesRow() {
        return values;
    }

    /**
     * @param key имя столбца
     * @return значение или null
     */
    public Object getValue(String key) {
        return values.get(key);
    }

    /**
     * Устанавливает значение столбца. Для строк, лежащих в таблице, используйте
     * {@link Table#update}, иначе индексы не обновятся.
     *
     * @param key имя столбца
     * @param val новое значение
     */
    public void SetValues(String key, Object val) {
        values.put(key, val);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
