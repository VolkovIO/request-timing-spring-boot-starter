# Request Timing Spring Boot Starter — пошаговое руководство

**Язык:** [Русский](README.ru.md) · [English](README.md)

Учебный, но настоящий Spring Boot 3 Starter: после добавления **одной зависимости** он автоматически измеряет время обработки HTTP-запросов Servlet-приложения и пишет результат в лог. В руководстве показан полный путь от обычного Java-класса до самостоятельной библиотеки с публикацией в **локальный Maven-репозиторий (`~/.m2`)**. Публикация в **Maven Central** рассмотрена **только теоретически** — ничего туда не отправляем.

> **Состояние проекта.** Здесь описана самостоятельная версия с пакетами `ru.almetsc.httptiming` и Maven-координатами `ru.almetsc:request-timing-spring-boot-starter:1.0.0`. Первоначальный учебный модуль использовал пакеты `com.example.httptiming` и только плагин `java-library`; для отдельного проекта и публикации в `.m2` нужны изменения, приведённые ниже. Это пример для изучения, а не готовый инструмент мониторинга production.

## Содержание

1. [Что такое стартер и зачем он нужен](#1-что-такое-стартер-и-зачем-он-нужен)
2. [Координаты библиотеки: group, artifact, version](#2-координаты-библиотеки-group-artifact-version)
3. [Структура самостоятельного проекта](#3-структура-самостоятельного-проекта)
4. [Шаг 1. Создать Java-библиотеку в Gradle](#4-шаг-1-создать-java-библиотеку-в-gradle)
5. [Шаг 2. Реализовать фильтр](#5-шаг-2-реализовать-фильтр)
6. [Шаг 3. Написать автоконфигурацию](#6-шаг-3-написать-автоконфигурацию)
7. [Шаг 4. Зарегистрировать автоконфигурацию](#7-шаг-4-зарегистрировать-автоконфигурацию)
8. [Шаг 5. Класс жизненного цикла](#8-шаг-5-класс-жизненного-цикла)
9. [Шаг 6. Собрать и опубликовать в локальный Maven](#9-шаг-6-собрать-и-опубликовать-в-локальный-maven)
10. [Шаг 7. Подключить к другому приложению](#10-шаг-7-подключить-к-другому-приложению)
11. [Что происходит при запуске Spring Boot](#11-что-происходит-при-запуске-spring-boot)
12. [Maven Central — теоретическая справка](#12-maven-central--теоретическая-справка)
13. [Типичные ошибки и ограничения](#13-типичные-ошибки-и-ограничения)
14. [Дальнейшее развитие и источники](#14-дальнейшее-развитие-и-источники)

## 1. Что такое стартер и зачем он нужен

Представим, что в пяти Spring Boot-приложениях нужно измерять время HTTP-запросов. Без стартера придётся копировать фильтр, регистрировать его как Spring-бин и поддерживать одинаковую конфигурацию в каждом проекте.

Стартер позволяет подключить готовую функциональность так:

```groovy
implementation 'ru.almetsc:request-timing-spring-boot-starter:1.0.0'
```

После запуска приложения фильтр регистрируется **автоматически**, а контроллеры менять не нужно. Пример строки в логе:

```text
HTTP POST /subscribe -> 202 (45 ms)
```

Здесь `45 ms` — **пример**, а не постоянное значение. Фильтр измеряет время синхронной обработки запроса через Servlet filter chain, а не время выполнения фоновых заданий, которые контроллер мог поставить в очередь.

Три понятия важно не путать:

| Понятие | Что делает |
| --- | --- |
| Обычная Java-библиотека | Предоставляет классы и методы. |
| Spring Boot Auto-Configuration | При подходящих условиях сама создаёт и настраивает бины. |
| Spring Boot Starter | Предоставляет удобный набор зависимостей, часто включая автоконфигурацию. |

В крупных библиотеках `core`, `autoconfigure` и `starter` могут быть отдельными модулями. Для маленького учебного проекта нормально **объединить их в один JAR**. Строго говоря, в классической многомодульной схеме стартер — почти пустой модуль зависимостей; здесь словом «стартер» называем весь объединённый артефакт.

## 2. Координаты библиотеки: group, artifact, version

Maven и Gradle идентифицируют артефакт по **GAV** (Group, Artifact, Version):

```text
ru.almetsc : request-timing-spring-boot-starter : 1.0.0
   group              artifactId                version
```

- `group = 'ru.almetsc'` — пространство имён автора. Для домена `almetsc.ru` логично использовать обратный порядок `ru.almetsc`.
- `artifactId = 'request-timing-spring-boot-starter'` — имя публикуемой библиотеки, не имя Java-пакета.
- `version = '1.0.0'` — версия опубликованного артефакта.
- `package ru.almetsc.httptiming` — имя Java-пакета; оно **не обязано** совпадать с `group`, а дефисы в Java-пакетах недопустимы.

Для сравнения: `org.jobrunr:jobrunr-spring-boot-3-starter:9.0.3` строится из таких же трёх частей.

**Важное различие синтаксиса Gradle:**

```groovy
// Другой модуль внутри ОДНОЙ multi-project сборки:
implementation project(':request-timing-spring-boot-starter')

// Уже опубликованный артефакт в Maven-репозитории:
implementation 'ru.almetsc:request-timing-spring-boot-starter:1.0.0'
```

`project('ru.almetsc:...:1.0.0')` писать **нельзя**: метод `project(...)` принимает путь Gradle-проекта, а не Maven-координаты.

## 3. Структура самостоятельного проекта

Создаём **обычный Gradle Java-проект**, не Spring Boot Application и не Spring Initializr-проект. В примере используется **Groovy DSL** (`build.gradle`).

```text
request-timing-spring-boot-starter/
├── settings.gradle
├── build.gradle
├── README.md                        # English
├── README.ru.md                     # Русский
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

Каталог стартера может лежать **рядом** с потребляющим приложением:

```text
JavaProjects/
├── demo_jobs_runs/                    # независимое Spring Boot-приложение
└── request-timing-spring-boot-starter/ # независимая библиотека
```

Старый вариант, где стартер вложен в `demo_jobs_runs/`, тоже корректен для multi-project сборки, но тогда используется `include` + `project(...)`. В этом руководстве цель другая: **два независимых проекта**, связанные только опубликованной зависимостью.

## 4. Шаг 1. Создать Java-библиотеку в Gradle

`settings.gradle`:

```groovy
rootProject.name = 'request-timing-spring-boot-starter'
```

`build.gradle` — полный минимальный пример для самостоятельной библиотеки и локальной Maven-публикации:

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

Что здесь важно:

| Элемент | Для чего нужен |
| --- | --- |
| `java-library` | Собирает обычный библиотечный JAR, а не запускаемый `bootJar`. |
| `maven-publish` | Создаёт публикацию Maven (JAR, POM и другие артефакты). |
| `group`, `version`, `artifactId` | Определяют координаты зависимости. |
| `withSourcesJar()`, `withJavadocJar()` | Создают отдельные JAR с исходниками и Javadoc. |
| `options.release = 17` | Компилирует с уровнем API/байткода Java 17; нужен JDK не ниже 17. |
| `api` | Зависимость Spring Web становится транзитивно доступной потребителю. |
| `from components.java` | Публикует Java-компонент вместе с его метаданными зависимостей. |
| `pom { ... }` | Заполняет публикуемый POM — это **не** `pom.xml` проекта Maven. |

**Почему нет `@SpringBootApplication` и Gradle-плагина `org.springframework.boot`?** Потому что это не приложение с `main()`, а библиотека, которая работает *внутри* чужого Spring Boot-приложения.

**О совместимости:** здесь специально сохранён Spring Boot Web `3.5.13` из исходного учебного примера. Для реального публичного стартера нужно продумать поддерживаемые версии Boot и управление транзитивными зависимостями; фиксированная версия может конфликтовать с другими версиями приложения.

## 5. Шаг 2. Реализовать фильтр

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

- `OncePerRequestFilter` — удобная основа Spring для Servlet-фильтра.
- `filterChain.doFilter(...)` продолжает обработку; без него запрос «застрянет» в фильтре.
- `System.nanoTime()` подходит для измерения **длительности**, а не текущей даты.
- `finally` позволяет записать время, даже если ниже по цепочке выброшено исключение.
- Логируется `requestURI`, **без** query string; параметры URL, в частности `email=...`, не выводятся этим кодом.

Здесь **нет** `@Component`: класс остаётся обычным Java-объектом, пока Spring не зарегистрирует его как бин.

## 6. Шаг 3. Написать автоконфигурацию

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

**Разбор аннотаций:**

- `@AutoConfiguration` — помечает класс автоконфигурации, которую Spring Boot может загрузить из специального файла.
- `@ConditionalOnWebApplication(type = SERVLET)` — требует Servlet-приложение. Для WebFlux это условие не выполнится.
- `@ConditionalOnProperty(...)` — включает конфигурацию при `request-timing.enabled=true` **или отсутствии** этого свойства (`matchIfMissing = true`). При `false` конфигурация отключится.
- `@Bean` — результат метода становится Spring-бином.
- `@ConditionalOnMissingBean` — не создаёт **дефолтный** бин, если разработчик уже объявил бин соответствующего типа.

Такой класс профессионально держать в пакете `autoconfigure`, отдельно от основной функциональности. Но название пакета — соглашение; Spring Boot не ищет его по имени.

## 7. Шаг 4. Зарегистрировать автоконфигурацию

Создай файл **с одним длинным именем**, а не цепочку вложенных каталогов:

```text
src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Его **единственная строка**:

```text
ru.almetsc.httptiming.autoconfigure.RequestTimingAutoConfiguration
```

Это главный «секрет» стартера. При запуске Spring Boot ищет ресурс `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` в JAR, узнаёт полные имена конфигураций и обрабатывает их условия.

**Не нужно** расширять `@ComponentScan` до `ru.almetsc`: автоконфигурации должны регистрироваться через `AutoConfiguration.imports`, а не через сканирование пакетов приложения.

## 8. Шаг 5. Класс жизненного цикла

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

Его создаёт `@Bean` в `RequestTimingAutoConfiguration`. `@EventListener(ApplicationReadyEvent.class)` срабатывает после готовности приложения; `DisposableBean.destroy()` вызывается при штатном закрытии Spring-контекста.

**Важно:** этот класс только пишет сообщения о жизненном цикле. Он **не запускает и не останавливает** Servlet-фильтр. Для данного стартера он учебный и необязательный. В библиотеках с собственными потоками или серверами аналогичный класс может действительно управлять их жизненным циклом.

## 9. Шаг 6. Собрать и опубликовать в локальный Maven

Из **корня самостоятельного проекта стартера**, где лежат `settings.gradle` и `build.gradle`:

```powershell
# Если в проекте есть Gradle Wrapper:
.\gradlew.bat clean build
.\gradlew.bat publishToMavenLocal

# Если Wrapper отсутствует, но Gradle установлен:
gradle clean build
gradle publishToMavenLocal
```

> Изначальный ZIP содержал только Gradle-модуль, без `gradlew.bat`. Для отдельного репозитория полезно сгенерировать Wrapper командой `gradle wrapper` и закоммитить созданные Gradle Wrapper-файлы. Наличие `gradle` в системе — необходимое условие для этой генерации.

Что происходит по шагам:

1. `compileJava` компилирует классы.
2. `jar` создаёт JAR с классами и `AutoConfiguration.imports`.
3. `sourcesJar` и `javadocJar` готовят дополнительные артефакты.
4. `maven-publish` генерирует POM с координатами и зависимостями.
5. `publishToMavenLocal` копирует публикацию в Maven Local.

Обычно в Windows это `C:\Users\<username>\.m2\repository`, а на Linux/macOS — `~/.m2/repository` (если путь не переопределён настройками Maven).

```text
.m2/repository/
└── ru/almetsc/request-timing-spring-boot-starter/1.0.0/
    ├── request-timing-spring-boot-starter-1.0.0.jar
    ├── request-timing-spring-boot-starter-1.0.0.pom
    ├── request-timing-spring-boot-starter-1.0.0-sources.jar
    └── request-timing-spring-boot-starter-1.0.0-javadoc.jar
```

*Дополнительные файлы метаданных и контрольных сумм зависят от конфигурации и версии Gradle.*

**Что такое POM, если сборка на Gradle?** Это опубликованный Maven-дескриптор: он сообщает координаты библиотеки, её зависимости и другие сведения. Он позволяет потребителям использовать артефакт как из Maven, так и из Gradle.

`publishToMavenLocal` **не означает** публикацию в интернет или Maven Central. Настраивать `publishing.repositories { mavenLocal() }` для этой команды не требуется.

## 10. Шаг 7. Подключить к другому приложению

В отдельном Spring Boot приложении `demo_jobs_runs` в `build.gradle`:

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
    // другие зависимости приложения...
}
```

Фильтр `content { includeGroup 'ru.almetsc' }` ограничивает использование локального репозитория нашей группой. `mavenLocal()` стоит использовать для локальных экспериментов, **не** как обязательную зависимость production-сборки.

Удалите прежнее подключение `implementation project(':request-timing-spring-boot-starter')` и соответствующий `include` в `settings.gradle` приложения, если ранее использовалась многомодульная сборка.

Настройки потребителя (`application.yaml`):

```yaml
request-timing:
  enabled: true  # настройку можно не писать: по умолчанию включено
```

Для выключения:

```yaml
request-timing:
  enabled: false
```

В старом демо-приложении есть контроллер `POST /subscribe`:

```powershell
curl.exe -X POST "http://localhost:8080/subscribe?email=test@example.com"
```

Ожидаемая строка в логах приложения (число мс условное):

```text
Request timing is ready. Filter: RequestTimingFilter
HTTP POST /subscribe -> 202 (45 ms)
```

Для запуска **именно этого демо** могут понадобиться его PostgreSQL и JobRunr. **Наш стартер не зависит ни от JobRunr, ни от PostgreSQL.** Его можно проверить и в гораздо более простом Spring Boot Web-приложении с одним контроллером.

Если изменить `request-timing.enabled: false` и перезапустить приложение, сообщения нашего фильтра исчезнут. Обратите внимание, что настройки применяются при создании Spring-контекста; это не переключатель, меняющийся без перезапуска.

## 11. Что происходит при запуске Spring Boot

```text
Приложение содержит зависимость ru.almetsc:...:1.0.0
             │
             ▼
Gradle разрешает JAR и его транзитивные зависимости
             │
             ▼
Spring Boot читает META-INF/spring/...AutoConfiguration.imports
             │
             ▼
Обнаруживает RequestTimingAutoConfiguration
             │
             ▼
Проверяет Servlet-приложение, enabled, отсутствие своих бинов
             │
             ▼
Создаёт RequestTimingFilter и RequestTimingStarter
             │
             ▼
Spring Boot автоматически регистрирует Servlet Filter-бин
             │
             ▼
HTTP-запрос → фильтр → контроллер → лог времени запроса
```

Полезно поставить breakpoint в `requestTimingFilter()`, `requestTimingStarter()` и `startup()`. В приложении можно запустить Spring Boot с `--debug` и просмотреть **Condition Evaluation Report**, чтобы увидеть причины срабатывания или пропуска автоконфигурации.

## 12. Maven Central — теоретическая справка

**В рамках этого проекта мы ограничиваемся локальным `.m2` и ничего в Maven Central не публикуем.** Но полезно знать, что понадобилось бы для публичного релиза.

1. Создать аккаунт на [Sonatype Central Portal](https://central.sonatype.com/).
2. Зарегистрировать namespace `ru.almetsc` и подтвердить контроль над `almetsc.ru` через **DNS TXT** запись. Выделенный IP и собственный HTTP-сервер сами по себе для подтверждения не требуются.
3. Подготовить метаданные POM: название, описание, URL проекта, сведения о разработчике, лицензию, SCM (адрес Git-репозитория).
4. Приложить основной JAR, `-sources.jar`, `-javadoc.jar`, подписи GPG/PGP и требуемые контрольные суммы.
5. Получить токен публикации, подключить инструмент, работающий с **Central Publishing Portal** (например, сообщественный Gradle-плагин Vanniktech), выполнить публикационную задачу и пройти проверку/выпуск в Portal.
6. После распространения в Maven Central потребитель смог бы подключить `implementation 'ru.almetsc:request-timing-spring-boot-starter:1.0.0'` **без** `mavenLocal()`.

**Не путать:** обычный `maven-publish` отлично подходит для локального и совместимого удалённого Maven-репозитория, но сам по себе не обеспечивает нынешний процесс публикации Maven Central через Central Portal. Старый OSSRH workflow завершён. Реальная публикация потребовала бы дополнительной настройки и проверки актуальных требований.

Для ориентира у [Vanniktech Gradle Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) есть `publishToMavenCentral()`, `signAllPublications()` и задачи публикации. Здесь мы **намеренно их не включаем** и не храним ключи или токены в Git.

Публичный релиз в Central нельзя перезаписать под той же версией: изменения требуют новой версии (`1.0.1`, `1.1.0` и т. п.).

## 13. Типичные ошибки и ограничения

| Проблема | Причина / решение |
| --- | --- |
| `Could not find ru.almetsc:...:1.0.0` | Не запускали `publishToMavenLocal`; не добавлен `mavenLocal()` у потребителя; отличаются `group`, `artifactId` или `version`. |
| Стартер собрался, но запросы не логируются | Проверить точный путь/содержимое `AutoConfiguration.imports`, наличие Servlet-приложения и значение `request-timing.enabled`. |
| `@Bean` не создаётся | Условие `@Conditional...` не выполнено либо уже есть бин нужного типа. Смотрите `--debug` report. |
| После изменения Java-кода поведение прежнее | Переопубликовать JAR, обновить зависимости приложения; для учебных итераций менять версию или использовать `--refresh-dependencies`. |
| `gradlew.bat` не найден | В проекте отсутствует Gradle Wrapper. Сгенерировать `gradle wrapper` или использовать установленный `gradle`. |
| Используется `project('ru.almetsc:...')` | Это не Maven-координаты. Использовать строку `implementation 'ru.almetsc:...:1.0.0'`. |
| `request-timing.slow-threshold-ms` ничего не меняет | В текущей реализации такого свойства **нет**. Нужны `@ConfigurationProperties` и дополнительная логика фильтра. |
| Нет Javadoc-текста в `-javadoc.jar` | JAR создаётся, но подробные API-комментарии пока не написаны. |

**Ограничения текущего примера:** он не содержит метрик Prometheus/Micrometer, настройки порога медленных запросов, накопления статистики, тестов автоконфигурации и отдельной обработки асинхронных Servlet-запросов. Это сознательно маленький учебный проект. Для реального использования полезны `ApplicationContextRunner`, `@ConfigurationProperties`, управление версией Spring Boot, тесты в нескольких приложениях и политика логирования.

## 14. Дальнейшее развитие и источники

Что стоит сделать после успешного локального подключения:

- Написать тесты `ApplicationContextRunner` для условий `enabled=true/false`, «свой бин вместо стандартного» и non-Servlet окружения.
- Вынести настройки в `@ConfigurationProperties`: например, `slow-threshold-ms` **после того**, как будет реализовано поведение фильтра.
- Попробовать версионирование `1.0.1` с повторной публикацией в `.m2`.
- Изучить разделение большого стартера на `core`, `autoconfigure`, `starter`.
- Добавить выбранную **автором** лицензию (`LICENSE`), GitHub Actions и Gradle Wrapper — если планируется публичный репозиторий.

**Официальные источники:**

- [Spring Boot — Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Gradle — Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Gradle — Repository Types and Maven Local caveats](https://docs.gradle.org/current/userguide/supported_repository_types.html)
- [Sonatype — Register a Namespace](https://central.sonatype.org/register/namespace/)
- [Sonatype — Publication Requirements](https://central.sonatype.org/publish/requirements/)
- [Vanniktech — Central Portal publishing](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)

---

**Идея проекта:** не просто скопировать готовый класс, а проследить весь путь от обычного Java-фильтра до внешней библиотеки, которую Spring Boot сам обнаруживает и конфигурирует.
