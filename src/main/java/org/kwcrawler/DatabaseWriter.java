package org.kwcrawler;


import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseWriter {
    private final Connection connection;

    DatabaseWriter() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:15432/postgres");
        dataSource.setUser("postgres");
        dataSource.setPassword("postgres");
        try {
            connection = dataSource.getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean write(String terytId, String description, String owner_type, byte[] wkb) {
        try {
            var sqlQuery = "INSERT INTO my_geometries (id, description, owner_type, geom) " +
                    "VALUES (?, ?, ?, ST_GeomFromWKB(?, 2180)) " +
                    "ON CONFLICT (id) DO UPDATE SET " +
                    "description = EXCLUDED.description, " +
                    "owner_type = EXCLUDED.owner_type, " +
                    "geom = EXCLUDED.geom";

            var statement = connection.prepareStatement(sqlQuery);
            statement.setString(1, terytId);
            statement.setString(2, description);
            statement.setString(3, owner_type);
            statement.setBytes(4, wkb);
            statement.execute();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
