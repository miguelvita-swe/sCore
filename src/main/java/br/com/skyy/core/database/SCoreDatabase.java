package br.com.skyy.core.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;

public interface SCoreDatabase {
    /** Initialize and open the connection pool. Called during onEnable. */
    void initialize(JavaPlugin plugin);

    /**
     * Returns a connection from the pool.
     * MANDATORY: always call releaseConnection(conn) in the finally block after use.
     *
     * Correct example:
     *   Connection conn = SCore.getDatabase().getConnection();
     *   try {
     *       // use conn...
     *   } finally {
     *       SCore.getDatabase().releaseConnection(conn);
     *   }
     */
    Connection getConnection();

    /**
     * Releases the connection back to the pool.
     * Must be called in the finally block after each getConnection().
     */
    void releaseConnection(Connection connection);

    /** Closes the entire pool. Called in onDisable. */
    void close();

    /** Returns "SQLITE" or "MYSQL" */
    String getType();

    /** Returns true if the pool has at least one valid connection */
    boolean isConnected();

    /** Ensures the pool is active, reconnecting if necessary */
    void ensureConnected();
}
