package repository;
import model.Table;
import java.io.IOException;

public interface TableRepository {
    public void save(Table tab)throws IOException;
    public Table load(String tableName)throws IOException;
}
