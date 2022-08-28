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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Session {
    private static final String SELECT_SQL = "SELECT * FROM %s WHERE %s = ?;";
    private static final String UPDATE_SQL = "UPDATE %s SET %s WHERE %s = ?;";
    private static final String INSERT_SQL = "INSERT INTO %s (%s) VALUES (%s);";
    private static final String DELETE_SQL = "DELETE FROM %s WHERE %s = ?;";

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
    public <T> void persist(T entity) {
        Class<?> type = entity.getClass();
        String idName = resolveId(type);
        Object id = resolveIdValue(type, idName, entity);

        if (id == null) {
            insert(entity, type);
        } else {
            update(entity, type, idName, id);
        }
    }

    @SneakyThrows
    private <T> Object resolveIdValue(Class<?> type, String idName, T entity) {
        Field idField = type.getDeclaredField(idName);
        idField.setAccessible(true);
        return idField.get(entity);
    }

    @SneakyThrows
    private <T> void update(T entity, Class<?> type, String idName, Object id) {
        try (Connection connection = dataSource.getConnection()) {
            List<String> fieldNames = resolveFieldNames(type);
            String sql = String.format(UPDATE_SQL, resolveTableName(type), composeSetSection(fieldNames, entity), idName);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                System.out.println("sql = " + statement);
                statement.executeUpdate();
            }
        }
    }

    @SneakyThrows
    public <T> void remove(T entity) {
        Class<?> type = entity.getClass();
        String idName = resolveId(type);
        Object id = resolveIdValue(type, idName, entity);
        try (Connection connection = dataSource.getConnection()) {
            String sql = String.format(DELETE_SQL, resolveTableName(type), idName);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                statement.executeUpdate();
            }
        }
    }

    @SneakyThrows
    private <T> String composeSetSection(List<String> fieldNames, T entity) {
        Class<?> type = entity.getClass();
        StringBuilder sb = new StringBuilder();
        for (String fieldName : fieldNames) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);
            sb.append(fieldName).append(" = ");
            if (value instanceof String) {
                sb.append("'").append(value).append("'");
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    @SneakyThrows
    private <T> void insert(T entity, Class<?> type) {
        try (Connection connection = dataSource.getConnection()) {

            List<String> fieldNames = resolveFieldNames(type);
            String sql = String.format(INSERT_SQL, resolveTableName(type), compose(fieldNames), resolveValues(fieldNames, entity));
            System.out.println("sql = " + sql);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        }
    }

    @SneakyThrows
    private <T> String resolveValues(List<String> fieldNames, T entity) {

        StringBuilder sb = new StringBuilder();
        for (String fieldName : fieldNames) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);
            if (value instanceof String) {
                sb.append("'").append(value).append("'");
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String compose(List<String> fieldNames) {
        StringBuilder sb = new StringBuilder();
        for (String fieldName : fieldNames) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(fieldName);
        }
        return sb.toString();
    }



    private <T> List<String> resolveFieldNames(Class<T> type) {
        List<String> fieldList = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                fieldList.add(column.name());
            }
        }
        return fieldList;
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
