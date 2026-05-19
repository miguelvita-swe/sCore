package br.com.skyy.core.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * MySQL com pool de conexões thread-safe.
 *
 * Implementação idêntica ao SQLite: ConcurrentLinkedQueue + Semaphore.
 *
 * Por que não HikariCP?
 *   sCore não usa maven-shade-plugin — adicionar HikariCP exigiria shadear
 *   a dependência ou exigir que o administrador instale outro JAR.
 *   Este pool cobre os casos de uso da suite s sem dependências extras.
 *
 * Reconexão automática:
 *   - releaseConnection() valida antes de devolver ao pool
 *   - Se a conexão caiu (MySQL wait_timeout, restart, etc.), descarta e cria nova
 *   - ensureConnected() pode ser chamado periodicamente para validar todo o pool
 */
public class SCoreDatabaseMySQL implements SCoreDatabase {

    private static final Logger log             = Logger.getLogger("sCore-DB");
    private static final int    POOL_SIZE        = 4;
    private static final long   BORROW_TIMEOUT_S = 5L;

    private String url;
    private String user;
    private String password;

    /** Conexões disponíveis para empréstimo — lock-free */
    private final Queue<Connection> pool = new ConcurrentLinkedQueue<>();

    /**
     * Semáforo com POOL_SIZE permissões — controla quantas threads
     * podem ter uma conexão emprestada ao mesmo tempo.
     */
    private final Semaphore semaphore = new Semaphore(POOL_SIZE, true /* fair */);

    @Override
    public void initialize(JavaPlugin plugin) {
        String host     = plugin.getConfig().getString("Database.Host",     "localhost");
        int    port     = plugin.getConfig().getInt(   "Database.Port",     3306);
        String db       = plugin.getConfig().getString("Database.Database", "score");
        user            = plugin.getConfig().getString("Database.User",     "root");
        password        = plugin.getConfig().getString("Database.Password", "");

        url = "jdbc:mysql://" + host + ":" + port + "/" + db
            + "?useSSL=false"
            + "&autoReconnect=true"
            + "&characterEncoding=utf8"
            + "&connectionTimeout=5000"
            + "&socketTimeout=30000";

        for (int i = 0; i < POOL_SIZE; i++) {
            pool.offer(createConnection());
        }
        log.info("[sCore] MySQL pool iniciado (" + POOL_SIZE + " conexões): "
            + host + ":" + port + "/" + db);
    }

    private Connection createConnection() {
        try {
            loadDriver();
            return DriverManager.getConnection(url, user, password);
        } catch (Exception e) {
            throw new IllegalStateException("[sCore] Falha ao criar conexão MySQL: " + e.getMessage(), e);
        }
    }

    /** Tenta driver moderno (8.x), faz fallback para legacy (5.x). */
    private void loadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException modern) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException legacy) {
                throw new IllegalStateException("[sCore] Nenhum driver MySQL encontrado (cj nem legacy)", legacy);
            }
        }
    }

    @Override
    public Connection getConnection() {
        try {
            if (!semaphore.tryAcquire(BORROW_TIMEOUT_S, TimeUnit.SECONDS)) {
                log.warning("[sCore] MySQL pool esgotado após " + BORROW_TIMEOUT_S
                    + "s — criando conexão temporária (verifique releaseConnection()).");
                return createConnection();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[sCore] Thread interrompida aguardando conexão MySQL", e);
        }

        Connection c = pool.poll();
        if (c == null) {
            semaphore.release();
            return createConnection();
        }

        // Valida antes de entregar (MySQL pode ter caído por wait_timeout)
        try {
            if (!c.isValid(2)) {
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
            if (!connection.isClosed() && connection.isValid(2)) {
                pool.offer(connection);
                semaphore.release();
            } else {
                // Conexão morta: substitui por uma nova antes de liberar o semáforo
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
        int size = pool.size();
        for (int i = 0; i < size; i++) {
            Connection c = pool.poll();
            if (c == null) break;
            try {
                pool.offer(c.isValid(2) ? c : createConnection());
            } catch (Exception e) {
                pool.offer(createConnection());
            }
        }
    }

    @Override
    public void close() {
        Connection c;
        while ((c = pool.poll()) != null) {
            closeQuietly(c);
        }
        log.info("[sCore] MySQL pool fechado.");
    }

    @Override
    public String getType() { return "MYSQL"; }

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
