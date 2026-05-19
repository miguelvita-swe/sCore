package br.com.skyy.core.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * SQLite com pool de conexões thread-safe.
 *
 * Implementação: ConcurrentLinkedQueue + Semaphore
 *
 * Por que não ArrayDeque + synchronized?
 *   ArrayDeque bloqueia a thread inteira com Object.wait() enquanto o pool
 *   está cheio. Com Semaphore, cada thread aguarda individualmente com timeout
 *   e o ConcurrentLinkedQueue é lock-free para push/poll — sem contenção.
 *
 * Por que WAL mode?
 *   SQLite no modo padrão (DELETE journal) só permite 1 conexão ativa por vez.
 *   WAL (Write-Ahead Logging) permite N leituras simultâneas + 1 escrita,
 *   eliminando o "database is locked" quando sMaquinas e sEconomia usam o DB
 *   ao mesmo tempo.
 *
 * Uso correto:
 *   Connection conn = db.getConnection();
 *   try {
 *       // usar conn
 *   } finally {
 *       db.releaseConnection(conn);
 *   }
 */
public class SCoreDatabaseSQLite implements SCoreDatabase {

    private static final Logger log      = Logger.getLogger("sCore-DB");
    private static final int    POOL_SIZE        = 4;
    private static final long   BORROW_TIMEOUT_S = 5L;

    private File dbFile;

    /** Conexões disponíveis para empréstimo — lock-free */
    private final Queue<Connection> pool = new ConcurrentLinkedQueue<>();

    /**
     * Semáforo com POOL_SIZE permissões.
     * Cada getConnection() adquire 1 permissão; cada releaseConnection() libera 1.
     * Se todas as permissões estiverem tomadas, a thread aguarda até BORROW_TIMEOUT_S.
     */
    private final Semaphore semaphore = new Semaphore(POOL_SIZE, true /* fair */);

    @Override
    public void initialize(JavaPlugin plugin) {
        dbFile = new File(plugin.getDataFolder(), "data.db");
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        for (int i = 0; i < POOL_SIZE; i++) {
            pool.offer(createConnection());
        }
        log.info("[sCore] SQLite pool iniciado (" + POOL_SIZE + " conexões WAL): " + dbFile.getName());
    }

    private Connection createConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            c.setAutoCommit(true);
            // WAL: leituras simultâneas sem bloquear escritas
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                // Aguarda até 10s por lock de escrita antes de lançar exception
                st.execute("PRAGMA busy_timeout=10000");
                // Melhora performance de escrita em lote
                st.execute("PRAGMA synchronous=NORMAL");
            }
            return c;
        } catch (Exception e) {
            throw new IllegalStateException("[sCore] Falha ao criar conexão SQLite: " + e.getMessage(), e);
        }
    }

    @Override
    public Connection getConnection() {
        try {
            // Aguarda uma permissão por até BORROW_TIMEOUT_S segundos
            if (!semaphore.tryAcquire(BORROW_TIMEOUT_S, TimeUnit.SECONDS)) {
                // Pool esgotado após timeout — cria conexão temporária (não vai ao pool)
                log.warning("[sCore] SQLite pool esgotado após " + BORROW_TIMEOUT_S
                    + "s — criando conexão temporária (verifique releaseConnection()).");
                return createConnection();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[sCore] Thread interrompida aguardando conexão SQLite", e);
        }

        Connection c = pool.poll();
        if (c == null) {
            // Não deveria acontecer (semaphore garante disponibilidade), mas é seguro
            semaphore.release();
            return createConnection();
        }

        // Valida a conexão antes de entregar
        try {
            if (!c.isValid(1)) {
                closeQuietly(c);
                c = createConnection();
            }
        } catch (Exception e) {
            closeQuietly(c);
            c = createConnection();
        }
        return c;
    }

    @Override
    public void releaseConnection(Connection connection) {
        if (connection == null) return;
        try {
            // Só devolve ao pool se a conexão ainda é válida
            if (!connection.isClosed() && connection.isValid(1)) {
                pool.offer(connection);
                semaphore.release();
            } else {
                // Conexão corrompida: descarta e cria uma nova no lugar
                closeQuietly(connection);
                pool.offer(createConnection());
                semaphore.release();
            }
        } catch (Exception e) {
            closeQuietly(connection);
            pool.offer(createConnection());
            semaphore.release();
        }
    }

    @Override
    public void ensureConnected() {
        // Drena o pool, valida e reinsere; conexões inválidas são substituídas
        int size = pool.size();
        for (int i = 0; i < size; i++) {
            Connection c = pool.poll();
            if (c == null) break;
            try {
                pool.offer(c.isValid(1) ? c : createConnection());
            } catch (Exception e) {
                pool.offer(createConnection());
            }
        }
    }

    @Override
    public void close() {
        // Esvazia o pool e fecha todas as conexões
        Connection c;
        while ((c = pool.poll()) != null) {
            closeQuietly(c);
        }
        log.info("[sCore] SQLite pool fechado.");
    }

    @Override
    public String getType() { return "SQLITE"; }

    @Override
    public boolean isConnected() {
        Connection c = pool.peek();
        if (c == null) return false;
        try { return c.isValid(1); } catch (Exception e) { return false; }
    }

    private void closeQuietly(Connection c) {
        try { if (c != null && !c.isClosed()) c.close(); }
        catch (Exception ignored) { /* fechamento silencioso */ }
    }
}
