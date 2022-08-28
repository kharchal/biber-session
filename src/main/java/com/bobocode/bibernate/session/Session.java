package com.bobocode.bibernate.session;

import com.bobocode.bibernate.annotation.Column;
import com.bobocode.bibernate.annotation.Id;
import com.bobocode.bibernate.annotation.Table;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class Session {
    private static final String SELECT_SQL = "SELECT * FROM %s WHERE %s = ?;";

    private final DataSource dataSource;

    private Map<Key, Object> cache;

    public Session(DataSource dataSource) {
        this.dataSource = dataSource;
        cache = new HashMap<>();
    }

    @SneakyThrows
    public <T> T find(Class<T> type, Object id) {
        T cachedObj = (T) cache.get(new Key(type, id));
        if (cachedObj != null) {
            return cachedObj;
        }
        try (Connection connection = dataSource.getConnection()) {
            String sql = String.format(SELECT_SQL, resolveTableName(type), resolveId(type));
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                System.out.println("statement = " + statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    T obj = resolveObject(type, resultSet);

                    cache.put(new Key(type, id), obj);
                    return obj;
                }
            }

        }
    }

    @SneakyThrows
    private <T> T resolveObject(Class<T> type, ResultSet resultSet) {
        Constructor<T> constructor = type.getConstructor();
        T instance = constructor.newInstance();
        for (Field field : type.getDeclaredFields()) {
            field.setAccessible(true);
            Column column = field.getAnnotation(Column.class);
            String columnName = column == null ? field.getName() : column.name();
            field.set(instance, resultSet.getObject(columnName));
        }
        return instance;
    }

    private <T> String resolveId(Class<T> type) {
        Field idField = null;
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field.getName();
            }
            if (field.getName().equals("id")) {
                idField = field;
            }
        }
        if (idField == null) {
            throw new RuntimeException("couldn't find id field for " + type.getSimpleName());
        }
        return "id";
    }

    private <T> String resolveTableName(Class<T> type) {
        Table tableAnnotation = type.getAnnotation(Table.class);
        return tableAnnotation == null ? type.getSimpleName() : tableAnnotation.name();
    }

    public void close() {
        cache.clear();
    }
}
