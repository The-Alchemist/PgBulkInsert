// Copyright (c) Philipp Wagner. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package de.bytefish.pgbulkinsert.de.bytefish.pgbulkinsert;

import de.bytefish.pgbulkinsert.de.bytefish.pgbulkinsert.exceptions.SaveEntityFailedException;
import de.bytefish.pgbulkinsert.de.bytefish.pgbulkinsert.functional.Action2;
import de.bytefish.pgbulkinsert.de.bytefish.pgbulkinsert.functional.Func2;
import de.bytefish.pgbulkinsert.de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter;
import de.bytefish.pgbulkinsert.de.bytefish.pgbulkinsert.util.StringUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.copy.PGCopyOutputStream;

import java.math.BigInteger;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PgBulkInsert<TEntity> {

    private class TableDefinition {

        private String schema;

        private String tableName;

        public TableDefinition(String tableName) {
            this("", tableName);
        }

        public TableDefinition(String schema, String tableName) {
            this.schema = schema;
            this.tableName = tableName;
        }

        public String getSchema() {
            return schema;
        }

        public String getTableName() {
            return tableName;
        }

        public String GetFullQualifiedTableName() {
            if (StringUtils.isNullOrWhiteSpace(schema)) {
                return tableName;
            }
            return String.format("%1$s.%2$s", schema, tableName);
        }

        @Override
        public String toString() {
            return String.format("TableDefinition (Schema = {%1$s}, TableName = {%2$s})", schema, tableName);
        }
    }

    private class ColumnDefinition
    {
        private String columnName;

        private Action2<PgBinaryWriter, TEntity> write;

        public ColumnDefinition(String columnName, Action2<PgBinaryWriter, TEntity> write) {
            this.columnName = columnName;
            this.write = write;
        }

        public String getColumnName() {
            return columnName;
        }

        public Action2<PgBinaryWriter, TEntity> getWrite() {
            return write;
        }

        @Override
        public String toString()
        {
            return String.format("ColumnDefinition (ColumnName = {%1$s}, Serialize = {%2$s})", columnName, write);
        }
    }

    private TableDefinition table;

    private List<ColumnDefinition> columns;

    public PgBulkInsert(String schemaName, String tableName)
    {
        table = new TableDefinition(schemaName, tableName);
        columns = new ArrayList<>();
    }

    public PgBulkInsert<TEntity> MapBoolean(String columnName, Func2<TEntity, Boolean> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    public PgBulkInsert<TEntity> MapInt(String columnName, Func2<TEntity, Integer> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    public PgBulkInsert<TEntity> MapShort(String columnName, Func2<TEntity, Short> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    public PgBulkInsert<TEntity> MapBigInt(String columnName, Func2<TEntity, BigInteger> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    public PgBulkInsert<TEntity> MapLong(String columnName, Func2<TEntity, Long> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    public PgBulkInsert<TEntity> MapLocalDateTime(String columnName, Func2<TEntity, LocalDateTime> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    public PgBulkInsert<TEntity> MapString(String columnName, Func2<TEntity, String> propertyGetter)
    {
        return AddColumn(columnName, (binaryWriter, entity) -> {
            binaryWriter.write(propertyGetter.invoke(entity));
        });
    }

    private PgBulkInsert<TEntity> AddColumn(String columnName, Action2<PgBinaryWriter, TEntity> action)
    {
        columns.add(new ColumnDefinition(columnName, action));

        return this;
    }

    public void saveAll(PGConnection connection, Stream<TEntity> entities) throws SQLException {

        CopyManager cpManager = connection.getCopyAPI();
        CopyIn copyIn = cpManager.copyIn(getCopyCommand());

        int columnCount = columns.size();

        try (PgBinaryWriter bw = new PgBinaryWriter()) {

            // Wrap the CopyOutputStream in our own Writer:
            bw.open(new PGCopyOutputStream(copyIn));

            // Start a New Row:
            bw.startRow(columnCount);

            // Insert Each Column:
            entities.forEach(entity -> {
                columns.forEach(column -> {
                    try {
                        column.getWrite().invoke(bw, entity);
                    } catch (Exception e) {
                        throw new SaveEntityFailedException(e);
                    }
                });
            });
        }
    }

    private String getCopyCommand()
    {
        String commaSeparatedColumns = columns.stream()
                .map(x -> x.columnName)
                .collect(Collectors.joining(", "));

        return String.format("COPY %1$s(%2$s) FROM STDIN BINARY",
                table.GetFullQualifiedTableName(),
                commaSeparatedColumns);
    }
}
