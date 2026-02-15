package com.pet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
class RedisContainerTest {

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Test
  void redisContainerAcceptsConnections() throws IOException {
    String host = redis.getHost();
    Integer port = redis.getMappedPort(6379);
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 2000);
      assertTrue(socket.isConnected());
    }
  }
}
