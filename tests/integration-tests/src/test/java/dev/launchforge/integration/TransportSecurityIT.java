package dev.launchforge.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.launchforge.configedge.LaunchForgeConfigEdgeApplication;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.config.SaslConfigs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Proves that application clients require both authentication and TLS for Kafka and Redis. */
@Testcontainers(disabledWithoutDocker = true)
class TransportSecurityIT {
  private static final String STORE_PASSWORD = "transport-test-keystore-password";
  private static final String KAFKA_USER = "launchforge";
  private static final String KAFKA_PASSWORD = "transport-test-kafka-password";
  private static final String REDIS_USER = "launchforge";
  private static final String REDIS_PASSWORD = "transport-test-redis-password";
  private static final int KAFKA_HOST_PORT = availablePort();
  private static final TlsMaterial TLS = TlsMaterial.create(STORE_PASSWORD);
  private static final String VERIFICATION_KEYS = verificationKeys();

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-bookworm")
          .withDatabaseName("launchforge_transport_security_test")
          .withUsername("launchforge_transport_security_test")
          .withPassword("integration-test-only");

  @Container private static final GenericContainer<?> REDIS = redisContainer();

  @Container private static final GenericContainer<?> KAFKA = kafkaContainer();

  private ConfigurableApplicationContext edge;

  @BeforeAll
  static void migrateDatabase() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }

  @AfterEach
  void closeApplication() {
    if (edge != null) {
      edge.close();
    }
  }

  @Test
  void connectsOnlyWithTrustedCertificatesAndValidServiceCredentials() throws Exception {
    edge = startEdge();

    RedisConnectionFactory redisFactory = edge.getBean(RedisConnectionFactory.class);
    try (RedisConnection connection = redisFactory.getConnection()) {
      assertEquals("PONG", connection.ping());
    }

    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate = edge.getBean(KafkaTemplate.class);
    kafkaTemplate
        .send("launchforge.transport-security.v1", "transport-proof", "authenticated-and-tls")
        .get(10, TimeUnit.SECONDS);

    KafkaAdmin kafkaAdmin = edge.getBean(KafkaAdmin.class);
    Map<String, Object> invalidKafkaCredentials =
        new HashMap<>(kafkaAdmin.getConfigurationProperties());
    invalidKafkaCredentials.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, 2000);
    invalidKafkaCredentials.put(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000);
    invalidKafkaCredentials.put(
        SaslConfigs.SASL_JAAS_CONFIG,
        "org.apache.kafka.common.security.plain.PlainLoginModule required "
            + "username=\"launchforge\" password=\"wrong-password\";");
    try (Admin admin = Admin.create(invalidKafkaCredentials)) {
      assertThrows(
          ExecutionException.class, () -> admin.listTopics().names().get(5, TimeUnit.SECONDS));
    }

    ExecResult wrongRedisPassword =
        REDIS.execInContainer(
            "redis-cli",
            "--tls",
            "--cacert",
            "/run/launchforge/server.crt",
            "--user",
            REDIS_USER,
            "--pass",
            "wrong-password",
            "PING");
    String redisDenial = wrongRedisPassword.getStdout() + wrongRedisPassword.getStderr();
    assertTrue(redisDenial.contains("WRONGPASS"), redisDenial);
  }

  private static ConfigurableApplicationContext startEdge() {
    return new SpringApplicationBuilder(LaunchForgeConfigEdgeApplication.class)
        .web(WebApplicationType.REACTIVE)
        .run(
            arguments(
                "server.port=0",
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude="
                    + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
                    + "ReactiveOAuth2ClientAutoConfiguration,"
                    + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
                    + "ReactiveOAuth2ClientWebSecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.web.reactive."
                    + "ReactiveWebSecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.actuate.web.reactive."
                    + "ReactiveManagementWebSecurityAutoConfiguration",
                "launchforge.sdk-keys.peppers.v1="
                    + "transport-security-pepper-with-more-than-thirty-two-bytes",
                "launchforge.config-edge.revision-poll-interval=60s",
                "launchforge.config-edge.materialization-verification-keys=" + VERIFICATION_KEYS,
                "spring.data.redis.ssl.enabled=true",
                "spring.data.redis.ssl.bundle=redis",
                "spring.ssl.bundle.pem.redis.truststore.certificate=" + TLS.certificate().toUri(),
                "spring.kafka.security.protocol=SASL_SSL",
                "spring.kafka.properties[sasl.mechanism]=PLAIN",
                "spring.kafka.jaas.enabled=true",
                "spring.kafka.jaas.login-module="
                    + "org.apache.kafka.common.security.plain.PlainLoginModule",
                "spring.kafka.jaas.options.username=" + KAFKA_USER,
                "spring.kafka.jaas.options.password=" + KAFKA_PASSWORD,
                "spring.kafka.ssl.bundle=kafka",
                "spring.ssl.bundle.pem.kafka.truststore.certificate=" + TLS.certificate().toUri(),
                "spring.kafka.producer.properties.delivery.timeout.ms=5000",
                "spring.kafka.producer.properties.request.timeout.ms=2000",
                "spring.kafka.producer.properties.max.block.ms=5000"));
  }

  private static String[] arguments(String... applicationArguments) {
    List<String> arguments = new ArrayList<>();
    arguments.add("--spring.datasource.url=" + POSTGRES.getJdbcUrl());
    arguments.add("--spring.datasource.username=" + POSTGRES.getUsername());
    arguments.add("--spring.datasource.password=" + POSTGRES.getPassword());
    arguments.add("--spring.data.redis.host=" + REDIS.getHost());
    arguments.add("--spring.data.redis.port=" + REDIS.getMappedPort(6379));
    arguments.add("--spring.data.redis.username=" + REDIS_USER);
    arguments.add("--spring.data.redis.password=" + REDIS_PASSWORD);
    arguments.add("--spring.kafka.bootstrap-servers=" + KAFKA.getHost() + ':' + KAFKA_HOST_PORT);
    for (String argument : applicationArguments) {
      arguments.add("--" + argument);
    }
    return arguments.toArray(String[]::new);
  }

  private static GenericContainer<?> redisContainer() {
    Path aclFile = TLS.directory().resolve("users.acl");
    try {
      Files.writeString(
          aclFile,
          "user default off\nuser " + REDIS_USER + " on >" + REDIS_PASSWORD + " ~* &* +@all\n",
          UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create the Redis TLS ACL fixture", exception);
    }
    return new GenericContainer<>(
            DockerImageName.parse(
                "redis:8.2.8-bookworm@sha256:2f7462b9e93e0a7ae2edf3a0a0babc8a4d29f8bfc50849b906b7caaef925edc1"))
        .withCopyFileToContainer(
            MountableFile.forHostPath(TLS.certificate()), "/run/launchforge/server.crt")
        .withCopyFileToContainer(
            MountableFile.forHostPath(TLS.privateKey()), "/run/launchforge/server.key")
        .withCopyFileToContainer(MountableFile.forHostPath(aclFile), "/run/launchforge/users.acl")
        .withCommand(
            "redis-server",
            "--port",
            "0",
            "--tls-port",
            "6379",
            "--tls-cert-file",
            "/run/launchforge/server.crt",
            "--tls-key-file",
            "/run/launchforge/server.key",
            "--tls-ca-cert-file",
            "/run/launchforge/server.crt",
            "--tls-auth-clients",
            "no",
            "--aclfile",
            "/run/launchforge/users.acl",
            "--save",
            "",
            "--appendonly",
            "no")
        .withExposedPorts(6379)
        .waitingFor(
            Wait.forLogMessage(".*Ready to accept connections tls.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
  }

  private static GenericContainer<?> kafkaContainer() {
    GenericContainer<?> container =
        new GenericContainer<>(
                DockerImageName.parse(
                    "apache/kafka:4.3.1@sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837"))
            .withCopyFileToContainer(
                MountableFile.forHostPath(TLS.keyStore()), "/run/launchforge/server.p12")
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_LISTENERS", "INTERNAL://:9092,CONTROLLER://:9093,EXTERNAL://:9094")
            .withEnv(
                "KAFKA_ADVERTISED_LISTENERS",
                "INTERNAL://localhost:9092,EXTERNAL://localhost:" + KAFKA_HOST_PORT)
            .withEnv(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:SASL_SSL")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL")
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_NUM_PARTITIONS", "1")
            .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN")
            .withEnv(
                "KAFKA_LISTENER_NAME_EXTERNAL_PLAIN_SASL_JAAS_CONFIG",
                "org.apache.kafka.common.security.plain.PlainLoginModule required "
                    + "username=\"broker\" password=\"broker-password\" "
                    + "user_"
                    + KAFKA_USER
                    + "=\""
                    + KAFKA_PASSWORD
                    + "\";")
            .withEnv("KAFKA_SSL_KEYSTORE_TYPE", "PKCS12")
            .withEnv("KAFKA_SSL_KEYSTORE_LOCATION", "/run/launchforge/server.p12")
            .withEnv("KAFKA_SSL_KEYSTORE_PASSWORD", STORE_PASSWORD)
            .withEnv("KAFKA_SSL_KEY_PASSWORD", STORE_PASSWORD)
            .withEnv("KAFKA_SSL_CLIENT_AUTH", "none")
            .withExposedPorts(9094)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
    container.setPortBindings(List.of(KAFKA_HOST_PORT + ":9094"));
    return container;
  }

  private static int availablePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to reserve a Kafka test port", exception);
    }
  }

  private static String verificationKeys() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
      KeyPair keyPair = generator.generateKeyPair();
      return "transport-v1:"
          + Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(keyPair.getPublic().getEncoded());
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to create materialization test keys", exception);
    }
  }

  private record TlsMaterial(Path directory, Path keyStore, Path certificate, Path privateKey) {
    private static TlsMaterial create(String password) {
      try {
        Path directory = Files.createTempDirectory("launchforge-transport-security-");
        Path keyStore = directory.resolve("server.p12");
        Path certificate = directory.resolve("server.crt");
        Path privateKey = directory.resolve("server.key");
        runKeytool(
            "-genkeypair",
            "-alias",
            "server",
            "-keystore",
            keyStore.toString(),
            "-storetype",
            "PKCS12",
            "-storepass",
            password,
            "-keypass",
            password,
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "2",
            "-dname",
            "CN=localhost",
            "-ext",
            "SAN=dns:localhost,ip:127.0.0.1",
            "-ext",
            "EKU=serverAuth");
        runKeytool(
            "-exportcert",
            "-rfc",
            "-alias",
            "server",
            "-keystore",
            keyStore.toString(),
            "-storepass",
            password,
            "-file",
            certificate.toString());

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (java.io.InputStream input = Files.newInputStream(keyStore)) {
          store.load(input, password.toCharArray());
        }
        PrivateKey key = (PrivateKey) store.getKey("server", password.toCharArray());
        String encodedKey =
            Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(key.getEncoded());
        Files.writeString(
            privateKey,
            "-----BEGIN PRIVATE KEY-----\n" + encodedKey + "\n-----END PRIVATE KEY-----\n",
            UTF_8);
        return new TlsMaterial(directory, keyStore, certificate, privateKey);
      } catch (IOException | java.security.GeneralSecurityException exception) {
        throw new IllegalStateException("Unable to create TLS test material", exception);
      }
    }

    private static void runKeytool(String... arguments) throws IOException {
      Path javaBin = Path.of(System.getProperty("java.home"), "bin");
      Path executable = javaBin.resolve(isWindows() ? "keytool.exe" : "keytool");
      List<String> command = new ArrayList<>();
      command.add(executable.toString());
      command.addAll(List.of(arguments));
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String output;
      try {
        output = new String(process.getInputStream().readAllBytes(), UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          throw new IllegalStateException("keytool timed out");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while generating TLS test material", exception);
      }
      if (process.exitValue() != 0) {
        throw new IllegalStateException("keytool failed: " + output);
      }
    }

    private static boolean isWindows() {
      return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");
    }
  }
}
