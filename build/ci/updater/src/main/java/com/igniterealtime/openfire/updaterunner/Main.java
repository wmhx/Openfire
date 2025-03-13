/*
 * Copyright (C) 2021-2024 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igniterealtime.openfire.updaterunner;

import org.jivesoftware.database.*;
import org.jivesoftware.util.JiveGlobals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;

public class Main {
    
    public static void main(String[] args) {

        String connectionString = System.getenv("CONNECTION_STRING");
        String connectionDriver = System.getenv("CONNECTION_DRIVER");
        String connectionUsername = System.getenv("CONNECTION_USERNAME");
        String connectionPassword = System.getenv("CONNECTION_PASSWORD");
        System.out.println("Connection String: " + connectionString);
        System.out.println("Connection Driver: " + connectionDriver);
        System.out.println("Connection Username: " + connectionUsername);
        System.out.println("Connection Password: " + connectionPassword);

        /*
        DATABASE DRIVERS AND EXAMPLE QUERY STRINGS
        "MySQL","com.mysql.cj.jdbc.Driver","jdbc:mysql://HOSTNAME:3306/DATABASENAME?rewriteBatchedStatements=true&characterEncoding=UTF-8&characterSetResults=UTF-8&serverTimezone=UTC"
        "Oracle","oracle.jdbc.driver.OracleDriver","jdbc:oracle:thin:@HOSTNAME:1521:SID"
        "Microsoft SQL Server (legacy)","net.sourceforge.jtds.jdbc.Driver","jdbc:jtds:sqlserver://HOSTNAME/DATABASENAME;appName=Openfire"
        "PostgreSQL","org.postgresql.Driver","jdbc:postgresql://HOSTNAME:5432/DATABASENAME"
        "IBM DB2","com.ibm.db2.jcc.DB2Driver","jdbc:db2://HOSTNAME:50000/DATABASENAME"
        "Microsoft SQL Server","com.microsoft.sqlserver.jdbc.SQLServerDriver","jdbc:sqlserver://HOSTNAME:1433;databaseName=DATABASENAME;applicationName=Openfire"
        */
        
        try {
            Path parent = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            PropertiesReader reader = new PropertiesReader(parent.resolve("maven-archiver/pom.properties")); // TODO determine why we read properties but not use them. Is this an existence check only?

            // Create a dummy OPENFIRE_HOME, in which the config files are created where XML properties will be stored.
            final Path confDir = Files.createDirectory(parent.resolve("conf"));
            final Path propertyFile = Files.createFile(confDir.resolve("openfire.xml"));
            Files.writeString(propertyFile, "<jive/>");
            final Path securityFile = Files.createFile(confDir.resolve("security.xml"));
            Files.writeString(securityFile, "<security/>");
            JiveGlobals.setHomePath(parent);

            JiveGlobals.setXMLProperty("connectionProvider.className", "org.jivesoftware.database.DefaultConnectionProvider");
            JiveGlobals.setXMLProperty("database.defaultProvider.driver", connectionDriver);
            JiveGlobals.setXMLProperty("database.defaultProvider.serverURL", connectionString);
            JiveGlobals.setXMLProperty("database.defaultProvider.username", connectionUsername);
            JiveGlobals.setXMLProperty("database.defaultProvider.password", connectionPassword);
            JiveGlobals.setXMLProperty("database.defaultProvider.testSQL", DbConnectionManager.getTestSQL(connectionDriver));

            Thread.sleep(30_000L);

            //Try connecting
            DefaultConnectionProvider cp = new DefaultConnectionProvider();
            cp.start();
            DbConnectionManager.setConnectionProvider(cp);
            Connection conn = DbConnectionManager.getConnection();

            //Try updating explicitly. It'll already have happened once as a byproduct of the above, but we need to trap the result
            SchemaManager sm = new SchemaManager();
            boolean fixed = sm.checkOpenfireSchema(conn);
            if(!fixed){
                throw new Exception("Failed to install or upgrade the database! It is likely that the installation or upgrade SQL scripts contain a bug.");
            }
        } catch (Exception e) {
            System.out.println(e.getClass() + "\n" + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
}
