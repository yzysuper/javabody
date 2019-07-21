package top.yzysuper.javabody;

import lombok.extern.log4j.Log4j2;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.*;
import java.util.Properties;

/**
 * @author yzysuer
 * @see java.sql.DriverManager
 * @see java.sql.Connection
 * @see java.sql.Statement
 * @see java.sql.PreparedStatement
 */
@Log4j2
public class JDBCTest {

    private static Connection conn;

    @BeforeClass
    public static void init() {
        // 获取驱动
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // 建立连接
        Properties properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", "root");
        try {
            long start = System.currentTimeMillis();
            conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1/testdb?useSSL=false", properties);
            long end = System.currentTimeMillis();
            log.info("连接耗时：[{}ms]", end - start);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void close() {
        // 关闭连接
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * statement测试用例
     */
    @Test
    public void jdbcTest1() {
        // 执行语句
        try (Statement statement = conn.createStatement()) {
            String sql = "select * from user";
            boolean execute = statement.execute(sql);
            log.info("查询是否成功：[{}]", execute);

            // Statement使用SQL拼接的方式，会引发SQL注入的问题
//            String id = "1 or 1=1";
//            String deleteSql = "delete from user where id=" + id;

            // 获取结果集
            try (ResultSet rs = statement.getResultSet()) {
                while (rs.next()) {
                    String result = rs.getObject(1)
                            + "--" + rs.getObject(2)
                            + "--" + rs.getObject(3);
                    log.info(result);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * prepareStatement测试用例
     */
    @Test
    public void jdbcTest2() {
        // 执行语句
        String sql = "select * from user where id = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, "1");
            boolean execute = statement.execute();
            log.info("查询是否成功：[{}]", execute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
