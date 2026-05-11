package base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import base.LoadFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Oracle DB connection helper.
 *
 * Configuration is sourced from environment variables:
 *  - ORACLE_JDBC_URL  (e.g. jdbc:oracle:thin:@//host:1521/service)
 *  - ORACLE_USERNAME
 *  - ORACLE_PASSWORD
 *
 * Notes:
 *  - Requires Oracle JDBC driver on the classpath (e.g., com.oracle.database.jdbc:ojdbc8 or ojdbc11)
 *  - Use try-with-resources when you obtain a Connection from this class.
 */
public class DbConn {

    private static final Logger log = LoggerFactory.getLogger(DbConn.class);
    private static Connection dbCon;

    // Prefer external configuration over hardcoding secrets in source code.
    private final String url;
    private final String userName;
    private final String password;
    public boolean TEST_MODE = false;

    /**
     * Construct using environment variables.
     */
    public DbConn() {
        this(
            envOrDefault("ORACLE_JDBC_URL", "jdbc:oracle:thin:@eagnmnmed5f7:2022:dldis0"),
            envOrDefault("ORACLE_USERNAME", "dd32j0"),
            envOrDefault("ORACLE_PASSWORD", "1qaz2wsx3edc$RFV")
        );
    }

    /**
     * Construct using explicit configuration.
     */
    public DbConn(String url, String userName, String password) {
        this.url = url;
        this.userName = userName;
        this.password = password;
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * Obtain a new JDBC connection.
     *
     * Caller should close the connection (use try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        if (userName == null || userName.isBlank()) {
            throw new SQLException("Missing ORACLE_USERNAME (or empty username provided).");
        }
        if (password == null || password.isBlank()) {
            throw new SQLException("Missing ORACLE_PASSWORD (or empty password provided).");
        }

        // Ensure the driver is available (mainly for older/classic setups).
        // With JDBC 4+, this is usually optional if the driver is on the classpath.
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                "Oracle JDBC driver not found. Add ojdbc to the classpath (e.g., ojdbc8/ojdbc11).",
                e
            );
        }

        log.debug("Opening Oracle connection to {}", url);
        return DriverManager.getConnection(url, userName, password);
    }

    /**
     * Simple connectivity check.
     */
    public boolean testConnection() {
        try (Connection c = getConnection()) {
            return c != null && c.isValid(5);
        } catch (SQLException e) {
            log.error("Oracle connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convenience method to run a SELECT and return rows as a list of maps.
     * (Good for quick admin/tools usage; for app code, prefer typed DAOs.)
     */
    public List<Map<String, Object>> queryForList(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        String label = md.getColumnLabel(i);
                        row.put(label, rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    /**
     * Convenience method to run a SELECT and return rows as a list of Object arrays.
     */
    public List<Object[]> cmftQueryExecute(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                int cols = rs.getMetaData().getColumnCount();

                while (rs.next()) {
                    Object[] row = new Object[cols];
                    for (int i = 1; i <= cols; i++) {
                        row[i - 1] = rs.getObject(i);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    /**
     * Convenience method to run INSERT/UPDATE/DELETE.
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
    
    public static ResultSet execSQL(String sql) throws SQLException{
  	  
  	  if (LoadFile.TEST_MODE) System.out.println("||||||||||||||||||||||||||DbBean - ResultSet||||||||||||||||||||||||||||||||||||||"); 
    
  	  Statement s = dbCon.createStatement(); 
  	  ResultSet r = s.executeQuery(sql); 
  	  return (r == null) ? null : r; 
    }
    

}