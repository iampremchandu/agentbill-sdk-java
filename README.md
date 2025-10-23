# AgentBill Java SDK

OpenTelemetry-based SDK for automatically tracking and billing AI agent usage.

## Installation

### From GitHub (Recommended)
Add to your `pom.xml`:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.YOUR-ORG</groupId>
    <artifactId>agentbill-java</artifactId>
    <version>v1.0.0</version>
</dependency>
```

### From Maven Central
```xml
<dependency>
    <groupId>com.agentbill</groupId>
    <artifactId>agentbill-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.YOUR-ORG:agentbill-java:v1.0.0'
}
```

### From Source
```bash
git clone https://github.com/YOUR-ORG/agentbill-java.git
cd agentbill-java
mvn clean install
```

## File Structure

```
agentbill-java/
├── README.md
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── agentbill/
│                   └── AgentBill.java
└── examples/
    └── BasicUsage.java
```

## Quick Start

```java
import com.agentbill.AgentBill;
import java.util.*;

public class Example {
    public static void main(String[] args) {
        // Initialize AgentBill
        AgentBill.Config config = new AgentBill.Config("your-api-key")
                .setCustomerId("customer-123")
                .setDebug(true);
        
        AgentBill agentbill = AgentBill.init(config);
        
        // Wrap your OpenAI client
        AgentBill.OpenAIWrapper openai = agentbill.wrapOpenAI();
        
        // Use normally - all calls are automatically tracked!
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "Hello!");
        messages.add(message);
        
        Map<String, Object> response = openai.chatCompletion("gpt-4", messages);
        System.out.println("Response: " + response);
        
        // Flush telemetry
        agentbill.flush();
    }
}
```

## Features

- ✅ Zero-config instrumentation
- ✅ Accurate token & cost tracking
- ✅ Multi-provider support (OpenAI, Anthropic)
- ✅ Rich metadata capture
- ✅ OpenTelemetry-based

## Configuration

```java
AgentBill.Config config = new AgentBill.Config("your-api-key")
        .setBaseUrl("https://...")         // Optional
        .setCustomerId("customer-123")     // Optional
        .setDebug(true);                   // Optional

AgentBill agentbill = AgentBill.init(config);
```

## Publishing to Maven Central

### Prerequisites
1. Create Sonatype JIRA account
2. Configure GPG keys for signing
3. Update `pom.xml` with distribution settings

### Publishing Steps
```bash
# Build and install locally
mvn clean install

# Deploy to Maven Central
mvn clean deploy -P release
```

## GitHub Repository Setup

1. Create repository: `agentbill-java`
2. Push all files including `pom.xml` and `src/` directory
3. Tag releases: `git tag v1.0.0 && git push origin v1.0.0`
4. Enable JitPack for easier distribution

## License

MIT
