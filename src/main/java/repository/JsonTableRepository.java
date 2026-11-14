package repository;

import model.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import serializer.Serializer;

public class JsonTableRepository implements TableRepository {
    private Serializer ser = new Serializer();
    private String directory = "data";

    @Override
    public void save(Table tab) throws IOException {
        Files.createDirectories(Path.of(directory));
        Path path=Path.of(directory).resolve(tab.getName()+".json");
        ser.saveToFile(tab,path.toString());
    }

    @Override
    public Table load(String tableName) throws IOException {
        Path path=Path.of(directory).resolve(tableName+".json");
        return ser.readToFile(path.toString());
    }

}

