# Request Timing Spring Boot Starter — Step-by-Step Guide

**Languages:** [English](README.md) · [Русский](README.ru.md)

A small, real Spring Boot 3 starter for learning how auto-configuration works. Add **one dependency** to a Servlet-based Spring Boot application, and HTTP request processing times appear in the application logs automatically. This guide walks through the complete journey from an ordinary Java class to an independent Gradle library published to **Maven Local (`~/.m2`)**. **Maven Central** is discussed **for reference only**; this project does not publish there.

> **Project state.** This guide describes the independent version with Java packages under `ru.almetsc.httptiming` and coordinates `ru.almetsc:request-timing-spring-boot-starter:1.0.0`. The original educational ZIP used `com.example.httptiming` and only the Gradle `java-library` plugin. The package migration and publication configuration needed for Maven Local are explained below. This is a learning project, not a production-grade monitoring solution.

## Table of contents

1. [What is a Spring Boot starter?](#1-what-is-a-spring-boot-starter)
2. [Understanding group, artifact, and version](#2-understanding-group-artifact-and-version)
3. [Standalone project structure](#3-standalone-project-structure)
4. [Step 1 — Create the Gradle Java library](#4-step-1--create-the-gradle-java-library)
5. [Step 2 — Implement the HTTP timing filter](#5-step-2--implement-the-http-timing-filter)
6. [Step 3 — Implement auto-configuration](#6-step-3--implement-auto-configuration)
7. [Step 4 — Register the auto-configuration](#7-step-4--register-the-auto-configuration)
8. [Step 5 — Understand the lifecycle class](#8-step-5--understand-the-lifecycle-class)
9. [Step 6 — Build and publish to Maven Local](#9-step-6--build-and-publish-to-maven-local)
10. [Step 7 — Use the published dependency in another application](#10-step-7--use-the-published-dependency-in-another-application)
11. [What happens when Spring Boot starts?](#11-what-happens-when-spring-boot-starts)
12. [Maven Central — theory only](#12-maven-central--theory-only)
13. [Troubleshooting and limitations](#13-troubleshooting-and-limitations)
14. [Next steps and further reading](#14-next-steps-and-further-reading)

## 1. What is a Spring Boot starter?

Imagine that you want request timing logs in five independent Spring Boot applications. Without a reusable library, you could end up copying the filter, registering the bean, and maintaining the same configuration five times.

With a starter, you only need the dependency:

```groovy
implementation 'ru.almetsc:request-timing-spring-boot-starter:1.0.0'
```

Spring Boot automatically registers the filter without any changes to your controllers. For example:

```text
HTTP POST /subscribe -> 202 (45 ms)
```

The duration above is **illustrative**. We measure synchronous HTTP request processing through the Servlet filter chain — **not** the duration of background jobs that a controller may enqueue.

Three concepts are related but distinct:

| Concept | Responsibility |
| --- | --- |
| Regular Java library | Provides classes and reusable APIs. |
| Spring Boot auto-configuration | Creates and configures beans automatically when conditions match. |
| Spring Boot starter | Offers a convenient set of dependencies, typically including auto-configuration. |

Larger projects can separate `core`, `autoconfigure`, and `starter` into different Gradle modules. For a small learning project, a **single combined JAR** is completely reasonable. In the traditional multi-module layout, the actual starter artifact may contain little more than dependency declarations; here we use the word *starter* for the combined artifact.

## 2. Understanding group, artifact, and version

Gradle and Maven identify a published artifact with **GAV** coordinates (Group, Artifact, Version):

```text
ru.almetsc : request-timing-spring-boot-starter : 1.0.0
   group               artifactId                version
```

- **`group = 'ru.almetsc'`** identifies the publisher's namespace. Reversing the controlled domain `almetsc.ru` is a conventional choice.
- **`artifactId = 'request-timing-spring-boot-starter'`** identifies the artifact, not a Java package.
- **`version = '1.0.0'`** identifies a particular release.
- **`package ru.almetsc.httptiming`** is a Java package name. It does not have to match the group exactly, and Java package names cannot contain hyphens.

For comparison, `org.jobrunr:jobrunr-spring-boot-3-starter:9.0.3` uses the same three-part convention.

**Do not confuse these Gradle dependency forms:**

```groovy
// Another module of the SAME Gradle multi-project build:
implementation project(':request-timing-spring-boot-starter')

// A PUBLISHED artifact resolved from a Maven repository:
implementation 'ru.almetsc:request-timing-spring-boot-starter:1.0.0'
```

Writing `project('ru.almetsc:...:1.0.0')` is **incorrect**: `project(...)` takes a Gradle project path, not Maven coordinates.

## 3. Standalone project structure

Create a **plain Java library project** using Gradle's **Groovy DSL** (`build.gradle`). You do not need Spring Initializr, a Spring Boot application main class, or an embedded web server.

```text
request-timing-spring-boot-starter/
├── settings.gradle
├── build.gradle
├── README.md                     # English
├── README.ru.md                  # Russian
├── src/
│   └── main/
│       ├── java/
│       │   └── ru/almetsc/httptiming/
│       │       ├── RequestTimingFilter.java
│       │       └── autoconfigure/
│       │           ├── RequestTimingAutoConfiguration.java
│       │           └── RequestTimingStarter.java
│       └── resources/
│           └── META-INF/spring/
│               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── ...
```

The library and its consuming application can live in **separate directories and repositories**:

```text
JavaProjects/
├── demo_jobs_runs/                    # standalone Spring Boot app
└── request-timing-spring-boot-starter/ # standalone library
```

The old arrangement, with the starter nested under `demo_jobs_runs/`, is valid for a multi-project build, but it uses `include` plus `project(...)`. Here we are deliberately practicing a different workflow: **publish the library first**, then consume it by coordinates.

## 4. Step 1 — Create the Gradle Java library

`settings.gradle`:

```groovy
rootProject.name = 'request-timing-spring-boot-starter'
```

Full example `build.gradle` for a standalone library and **local Maven publication**:

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
}

group = 'ru.almetsc'
version = '1.0.0'

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType(JavaCompile).configureEach {
    options.release = 17
}

repositories {
    mavenCentral()
}

dependencies {
    api 'org.springframework.boot:spring-boot-starter-web:3.5.13'
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            artifactId = 'request-timing-spring-boot-starter'

            pom {
                name = 'Request Timing Spring Boot Starter'
                description = 'Automatic HTTP request timing for Spring Boot 3 Servlet applications'
                url = 'https://almetsc.ru'
            }
        }
    }
}
```

Why each part exists:

| Configuration | Purpose |
| --- | --- |
| `java-library` | Creates a standard library JAR, not an executable `bootJar`. |
| `maven-publish` | Creates Maven publications with artifacts and metadata. |
| `group`, `version`, `artifactId` | Define the dependency coordinates. |
| `withSourcesJar()` | Publishes source code as a separate JAR. |
| `withJavadocJar()` | Publishes Javadoc as a separate JAR. |
| `options.release = 17` | Targets Java 17 bytecode and APIs; the build needs JDK 17 or newer. |
| `api` | Makes the Spring Web dependency visible transitively to consumers. |
| `from components.java` | Publishes the Java component and its dependency metadata. |
| `pom { ... }` | Populates the generated Maven POM; this is not a Maven `pom.xml` build. |

**Why no `@SpringBootApplication` or `org.springframework.boot` Gradle plugin?** The starter is not an executable application. It is a library loaded **by** an application.

**Dependency compatibility note:** we deliberately keep `spring-boot-starter-web:3.5.13` from the educational source. A production starter should define its supported Spring Boot versions and avoid accidentally forcing an incompatible Boot dependency version onto consumers.

## 5. Step 2 — Implement the HTTP timing filter

`src/main/java/ru/almetsc/httptiming/RequestTimingFilter.java`:

```java
package ru.almetsc.httptiming;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RequestTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("HTTP {} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMs);
        }
    }
}
```

How it works:

- `OncePerRequestFilter` is a convenient Spring abstraction for Servlet filters.
- `filterChain.doFilter(...)` passes control to the remaining filters and eventually the controller. Without it, request processing would stop here.
- `System.nanoTime()` is appropriate for **elapsed time** measurement, not wall-clock timestamps.
- The `finally` block records the duration even when something downstream throws an exception.
- Only the request URI is logged — **not the query string**, so the example's `email=...` parameter is not logged by this filter.

Notice there is **no `@Component` annotation**. This is an ordinary Java class until Spring registers an instance as a bean.

## 6. Step 3 — Implement auto-configuration

`src/main/java/ru/almetsc/httptiming/autoconfigure/RequestTimingAutoConfiguration.java`:

```java
package ru.almetsc.httptiming.autoconfigure;

import ru.almetsc.httptiming.RequestTimingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "request-timing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RequestTimingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestTimingFilter.class)
    public RequestTimingFilter requestTimingFilter() {
        return new RequestTimingFilter();
    }

    @Bean
    @ConditionalOnMissingBean(RequestTimingStarter.class)
    public RequestTimingStarter requestTimingStarter(RequestTimingFilter filter) {
        return new RequestTimingStarter(filter);
    }
}
```

**Annotation walkthrough:**

- `@AutoConfiguration`: marks a class that can participate in Spring Boot auto-configuration.
- `@ConditionalOnWebApplication(type = SERVLET)`: activates it only in Servlet-based web applications, not WebFlux-only applications.
- `@ConditionalOnProperty(...)`: turns the feature on when `request-timing.enabled=true` **or the property is absent** (`matchIfMissing = true`). Setting `false` disables this auto-configuration.
- `@Bean`: registers the returned object in the application context.
- `@ConditionalOnMissingBean`: supplies the default bean only if no matching user-provided bean exists.

Keeping integration code in an `autoconfigure` package is a good convention, but **the package name itself has no magic**. Boot discovers the class through a registration resource.

## 7. Step 4 — Register the auto-configuration

Create exactly this **single file with a long dotted filename** (not multiple nested directories):

```text
src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Its content is **one fully qualified class name**:

```text
ru.almetsc.httptiming.autoconfigure.RequestTimingAutoConfiguration
```

This file is the key registration mechanism. At startup, Spring Boot looks for `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in JARs on the classpath and processes the listed configuration classes.

There is **no need** to change the application's `@ComponentScan` to cover `ru.almetsc`: auto-configurations should be discovered through this imports file, not ordinary component scanning.

## 8. Step 5 — Understand the lifecycle class

`src/main/java/ru/almetsc/httptiming/autoconfigure/RequestTimingStarter.java`:

```java
package ru.almetsc.httptiming.autoconfigure;

import ru.almetsc.httptiming.RequestTimingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public class RequestTimingStarter implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingStarter.class);
    private final RequestTimingFilter filter;

    public RequestTimingStarter(RequestTimingFilter filter) {
        this.filter = filter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startup() {
        log.info("Request timing is ready. Filter: {}", filter.getClass().getSimpleName());
    }

    @Override
    public void destroy() {
        log.info("Request timing application context is shutting down");
    }
}
```

The bean factory method in `RequestTimingAutoConfiguration` creates it. `@EventListener(ApplicationReadyEvent.class)` observes when the application is ready, and `DisposableBean.destroy()` runs when the Spring context shuts down cleanly.

**Important:** this class **does not start or stop the Servlet filter**. It only logs lifecycle events. It is an **optional educational component**; actual Java libraries with their own workers or servers may need a lifecycle coordinator for real resource management.

## 9. Step 6 — Build and publish to Maven Local

Run these commands **from the standalone starter project's root directory**:

```powershell
# If Gradle Wrapper exists in the project:
.\gradlew.bat clean build
.\gradlew.bat publishToMavenLocal

# If it does not, but Gradle is installed:
gradle clean build
gradle publishToMavenLocal
```

> The original ZIP contained a Gradle **module** and no `gradlew.bat`. For an independent GitHub repository, it is good practice to generate and commit a Wrapper (`gradle wrapper` using an installed Gradle). The Wrapper makes the Gradle version reproducible.

What the build does:

1. `compileJava` compiles the code.
2. `jar` packages the classes and `AutoConfiguration.imports`.
3. `sourcesJar` and `javadocJar` produce additional JARs.
4. The `maven-publish` plugin creates a Maven POM with coordinates and dependency metadata.
5. `publishToMavenLocal` copies the publication to the user's Maven Local repository.

Typical paths: `C:\Users\<username>\.m2\repository` on Windows and `~/.m2/repository` on Linux/macOS (unless Maven settings override the default).

```text
.m2/repository/
└── ru/almetsc/request-timing-spring-boot-starter/1.0.0/
    ├── request-timing-spring-boot-starter-1.0.0.jar
    ├── request-timing-spring-boot-starter-1.0.0.pom
    ├── request-timing-spring-boot-starter-1.0.0-sources.jar
    └── request-timing-spring-boot-starter-1.0.0-javadoc.jar
```

*Other metadata or checksum files depend on Gradle version and publishing settings.*

**Why do we have a POM when using Gradle?** Maven-compatible repositories need standardized metadata describing artifact coordinates and dependencies. Gradle generates this descriptor automatically so both Maven and Gradle consumers can resolve the library.

`publishToMavenLocal` does **not** send anything to Maven Central or the Internet. There is no need to add `publishing.repositories { mavenLocal() }` for this task.

## 10. Step 7 — Use the published dependency in another application

In a **separate** Spring Boot application's `build.gradle`:

```groovy
repositories {
    mavenLocal {
        content {
            includeGroup 'ru.almetsc'
        }
    }
    mavenCentral()
}

dependencies {
    implementation 'ru.almetsc:request-timing-spring-boot-starter:1.0.0'
    // Other application dependencies...
}
```

The `content { includeGroup 'ru.almetsc' }` filter restricts the local repository to our own group. Use `mavenLocal()` as a **temporary local-development mechanism**, not as a required production repository.

If the application previously used a nested Gradle module, remove `implementation project(':request-timing-spring-boot-starter')` and the matching `include` from its `settings.gradle`.

Application configuration (`application.yaml`):

```yaml
request-timing:
  enabled: true  # optional: the default is enabled
```

To disable:

```yaml
request-timing:
  enabled: false
```

The original demo application has a `POST /subscribe` endpoint:

```powershell
curl.exe -X POST "http://localhost:8080/subscribe?email=test@example.com"
```

Illustrative application log output:

```text
Request timing is ready. Filter: RequestTimingFilter
HTTP POST /subscribe -> 202 (45 ms)
```

That specific demo application's startup may require **PostgreSQL and JobRunr**. **This starter itself requires neither** and can be tested in any simple Spring Boot Servlet web application with an endpoint.

Set `request-timing.enabled: false`, restart the application, and verify that the timing log entries disappear. The property is evaluated while the Spring context is being created; it is **not** a live switch that reconfigures the filter without restarting.

## 11. What happens when Spring Boot starts?

```text
App declares ru.almetsc:...:1.0.0 dependency
             │
             ▼
Gradle resolves the JAR and its transitive dependencies
             │
             ▼
Spring Boot reads META-INF/spring/...AutoConfiguration.imports
             │
             ▼
Finds RequestTimingAutoConfiguration
             │
             ▼
Checks Servlet application, enabled property, existing beans
             │
             ▼
Creates RequestTimingFilter and RequestTimingStarter
             │
             ▼
Spring Boot registers the Servlet Filter bean automatically
             │
             ▼
HTTP request → filter → controller → timing log
```

A useful learning exercise is to put breakpoints into `requestTimingFilter()`, `requestTimingStarter()`, and `startup()`. Start the consuming Spring Boot app with `--debug` to inspect its **Condition Evaluation Report**, including why auto-configuration conditions match or fail.

## 12. Maven Central — theory only

**This project intentionally stops at Maven Local. Nothing is published to Maven Central.** Nevertheless, understanding public release requirements is useful.

1. Create an account at [Sonatype Central Portal](https://central.sonatype.com/).
2. Register the `ru.almetsc` namespace and prove control of `almetsc.ru` via a **DNS TXT** record. A dedicated IP address or self-hosted HTTP server is not required for this check.
3. Supply complete Maven POM metadata: project name, description, URL, license, developer, and source-control (SCM) links.
4. Include the binary JAR, `-sources.jar`, `-javadoc.jar`, GPG/PGP signatures, and required checksums.
5. Obtain a publishing token and use a tool that supports the **Central Publishing Portal**, for example the community Vanniktech Gradle publishing plugin. Upload, validate, and release the deployment through the Portal workflow.
6. After the release reaches Maven Central, consumers could depend on `ru.almetsc:request-timing-spring-boot-starter:1.0.0` with only `mavenCentral()`.

**Important distinction:** the ordinary Gradle `maven-publish` plugin is sufficient for Maven Local and many Maven-compatible servers, but it does **not** implement today's Central Portal release flow on its own. Legacy OSSRH publishing is no longer the normal path.

For reference, [Vanniktech's guide](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) explains `publishToMavenCentral()`, `signAllPublications()`, and the upload/release tasks. We deliberately **do not configure any of these** here. Never commit private signing keys or publishing tokens.

Maven Central releases are immutable: publish a new version (`1.0.1`, `1.1.0`, etc.) for changes instead of overwriting `1.0.0`.

## 13. Troubleshooting and limitations

| Symptom | What to check |
| --- | --- |
| `Could not find ru.almetsc:...:1.0.0` | Did you run `publishToMavenLocal`? Is `mavenLocal()` enabled in the consumer? Are `group`, `artifactId`, and `version` identical? |
| JAR builds, but no timing logs appear | Check the exact path and content of `AutoConfiguration.imports`, the presence of a Servlet application, and `request-timing.enabled`. |
| Default `@Bean` is not created | A `@Conditional...` condition might not match, or the consumer might already define the bean type. Use the `--debug` report. |
| Code changes are not visible in the consumer | Republish the JAR, refresh the consumer dependencies, or bump the library version. `--refresh-dependencies` may help during experimentation. |
| `gradlew.bat` is missing | The standalone project has no Gradle Wrapper yet. Generate one using installed Gradle, or run `gradle` directly. |
| Using `project('ru.almetsc:...')` fails | Use a string dependency: `implementation 'ru.almetsc:...:1.0.0'`. |
| `request-timing.slow-threshold-ms` has no effect | That setting is **not implemented**. It would require `@ConfigurationProperties` and new filter logic. |
| The `-javadoc.jar` is largely empty | The Javadoc artifact exists, but detailed Java API comments have not been written yet. |

**Current design limitations:** no Micrometer/Prometheus integration, slow-request threshold, stored timing statistics, auto-configuration tests, or special asynchronous Servlet processing. This is intentional for a minimal learning example. A production solution should also consider `ApplicationContextRunner` tests, `@ConfigurationProperties`, dependency/version compatibility, log policy, and sensitive-data handling.

## 14. Next steps and further reading

Good follow-up exercises:

- Add `ApplicationContextRunner` tests for enabled/disabled properties, a consumer-defined replacement bean, and non-Servlet environments.
- Introduce `@ConfigurationProperties` and implement a real `slow-threshold-ms` option.
- Publish a new `1.0.1` locally, then update the consumer to that version.
- Explore splitting a larger implementation into `core`, `autoconfigure`, and `starter` modules.
- If preparing a public repository, add a **chosen** `LICENSE`, a Gradle Wrapper, and CI checks. Do not assume a license without the owner's decision.

**Official references:**

- [Spring Boot — Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Gradle — Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Gradle — Repository Types and Maven Local caveats](https://docs.gradle.org/current/userguide/supported_repository_types.html)
- [Sonatype — Register a Namespace](https://central.sonatype.org/register/namespace/)
- [Sonatype — Publishing Requirements](https://central.sonatype.org/publish/requirements/)
- [Vanniktech — Central Portal publishing](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)

---

**The point of this project** is not to copy three classes, but to understand the whole path: **plain Java class → Spring bean → auto-configuration → JAR → local Maven publication → external dependency**.
