package com.coffeeshop.util;

import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Central place to get a JDBC Connection.
 *
 * Uses the JNDI DataSource "jdbc/coffeeshopDB" configured in META-INF/context.xml.
 * This gives you Tomcat-managed connection pooling instead of opening a raw
 * DriverManager connection per request.
 */
public class DBUtil {

    private static DataSource dataSource;

    private static DataSource getDataSource() throws NamingException {
        if (dataSource == null) {
            Context initContext = new InitialContext();
            Context envContext = (Context) initContext.lookup("java:/comp/env");
            dataSource = (DataSource) envContext.lookup("jdbc/coffeeshopDB");
        }
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        try {
            return getDataSource().getConnection();
        } catch (NamingException e) {
            throw new SQLException("Could not find JNDI DataSource 'jdbc/coffeeshopDB'. "
                    + "Check META-INF/context.xml and that the MySQL connector jar is on the classpath.", e);
        }
    }
}
