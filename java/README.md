# Ucotron Java SDK

Java/Kotlin client for [Ucotron](https://github.com/Ucotron/ucotron) — cognitive trust infrastructure for AI.

Three modules available:

| Module | Target |
|--------|--------|
| `ucotron-sdk` | JVM 11+ (OkHttp + Gson) |
| `ucotron-sdk-android` | Android / Kotlin |
| `ucotron-spring-boot-starter` | Spring Boot 3.2+ auto-configuration |

## Install

### Gradle

```groovy
implementation 'com.ucotron:ucotron-sdk:0.1.0'
```

### Maven

```xml
<dependency>
  <groupId>com.ucotron</groupId>
  <artifactId>ucotron-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Usage

```java
UcotronClient client = new UcotronClient("http://localhost:8420");

// Augment
AugmentResponse result = client.augment("Tell me about the user");
System.out.println(result.getContextText());

// Learn
client.learn("User prefers dark mode and speaks Spanish.");

// Search
SearchResponse search = client.search("preferences");
```

### Spring Boot

```yaml
# application.yml
ucotron:
  server-url: http://localhost:8420
  default-namespace: production
```

```java
@Autowired
private UcotronClient ucotron;
```

## License

MIT
