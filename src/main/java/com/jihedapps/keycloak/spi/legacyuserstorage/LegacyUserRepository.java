package com.jihedapps.keycloak.spi.legacyuserstorage;

import io.sentry.ISpan;
import io.sentry.NoOpSpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Plain JDBC against whatever table the legacy system already has — no ORM, no connection pool.
 * A production deployment fronting a real subscriber table would want pooling (the connection
 * supplier is the seam for that: swap the {@code DriverManager}-based one the factory builds for
 * a pooled {@code DataSource::getConnection}), but a raw connection per call keeps this workbench
 * dependency-light and keeps the SQL itself the thing under test, not a pool's behavior.
 */
public class LegacyUserRepository {

    private final Supplier<Connection> connectionSupplier;
    private final LegacyTableConfig table;

    public LegacyUserRepository(Supplier<Connection> connectionSupplier, LegacyTableConfig table) {
        this.connectionSupplier = connectionSupplier;
        this.table = table;
    }

    public Optional<LegacyUser> findByUsername(String username) {
        return findOneWhere(table.usernameColumn(), username);
    }

    public Optional<LegacyUser> findByEmail(String email) {
        return findOneWhere(table.emailColumn(), email);
    }

    public Optional<LegacyUser> findById(String id) {
        return findOneWhere(table.idColumn(), id);
    }

    private Optional<LegacyUser> findOneWhere(String column, String value) {
        ISpan span = startSpan("legacy_db.find_one", "SELECT by " + column);
        String sql = "SELECT * FROM " + table.tableName() + " WHERE LOWER(" + column + ") = LOWER(?)";
        try (Connection conn = connectionSupplier.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                Optional<LegacyUser> result = rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                span.finish(SpanStatus.OK);
                return result;
            }
        } catch (SQLException e) {
            span.finish(SpanStatus.INTERNAL_ERROR);
            Sentry.captureException(e);
            throw new LegacyUserStorageException("failed to look up user by " + column, e);
        }
    }

    public List<LegacyUser> search(String term, Integer firstResult, Integer maxResults) {
        ISpan span = startSpan("legacy_db.search", "search legacy users");
        String sql = "SELECT * FROM " + table.tableName() + " WHERE "
                + "LOWER(" + table.usernameColumn() + ") LIKE LOWER(?) OR "
                + "LOWER(" + table.emailColumn() + ") LIKE LOWER(?) OR "
                + "LOWER(" + table.firstNameColumn() + ") LIKE LOWER(?) OR "
                + "LOWER(" + table.lastNameColumn() + ") LIKE LOWER(?) "
                + "ORDER BY " + table.usernameColumn();
        String pattern = "%" + (term == null ? "" : term) + "%";

        try (Connection conn = connectionSupplier.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, pattern);
            List<LegacyUser> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                int skipped = 0;
                int firstIndex = firstResult != null && firstResult > 0 ? firstResult : 0;
                int limit = maxResults != null && maxResults >= 0 ? maxResults : Integer.MAX_VALUE;
                while (rs.next() && results.size() < limit) {
                    if (skipped < firstIndex) {
                        skipped++;
                        continue;
                    }
                    results.add(mapRow(rs));
                }
            }
            span.setData("result_count", results.size());
            span.finish(SpanStatus.OK);
            return results;
        } catch (SQLException e) {
            span.finish(SpanStatus.INTERNAL_ERROR);
            Sentry.captureException(e);
            throw new LegacyUserStorageException("failed to search legacy users", e);
        }
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM " + table.tableName();
        try (Connection conn = connectionSupplier.get();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            Sentry.captureException(e);
            throw new LegacyUserStorageException("failed to count legacy users", e);
        }
    }

    /**
     * Child span of whatever transaction Keycloak's request-tracing filter has open, or a no-op
     * {@code NoOpSpan} if Sentry isn't initialized (see LegacyUserStorageProviderFactory#init) or
     * no transaction is active — either way this never throws and never touches the network
     * on its own.
     */
    private ISpan startSpan(String operation, String description) {
        ISpan parent = Sentry.getSpan();
        return parent != null ? parent.startChild(operation, description) : NoOpSpan.getInstance();
    }

    LegacyUser mapRow(ResultSet rs) throws SQLException {
        return new LegacyUser(
                rs.getString(table.idColumn()),
                rs.getString(table.usernameColumn()),
                rs.getString(table.emailColumn()),
                rs.getString(table.firstNameColumn()),
                rs.getString(table.lastNameColumn()),
                rs.getString(table.passwordHashColumn()),
                rs.getString(table.passwordSaltColumn()),
                rs.getBoolean(table.enabledColumn())
        );
    }
}
