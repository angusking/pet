package com.pet;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@Tag("integration")
class MySqlContainerTest {

  @Container
  static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
      .withDatabaseName("pet_demo")
      .withUsername("pet")
      .withPassword("pet123");

  @Test
  void mysqlContainerAcceptsConnections() throws Exception {
    try (Connection connection = java.sql.DriverManager.getConnection(
        mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
         Statement statement = connection.createStatement()) {
      ResultSet rs = statement.executeQuery("SELECT 1");
      rs.next();
      assertEquals(1, rs.getInt(1));
    }
  }
}
