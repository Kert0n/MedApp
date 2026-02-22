# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.2/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.2/gradle-plugin/packaging-oci-image.html)
* [Coroutines section of the Spring Framework Documentation](https://docs.spring.io/spring-framework/reference/7.0.3/languages/kotlin/coroutines.html)
* [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/4.0.2/reference/testing/testcontainers.html#testing.testcontainers)
* [Testcontainers R2DBC support Reference Guide](https://java.testcontainers.org/modules/databases/r2dbc/)
* [Testcontainers Postgres Module Reference Guide](https://java.testcontainers.org/modules/databases/postgres/)
* [Spring Data R2DBC](https://docs.spring.io/spring-boot/4.0.2/reference/data/sql.html#data.sql.r2dbc)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.2/reference/using/devtools.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.2/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Spring Security](https://docs.spring.io/spring-boot/4.0.2/reference/web/spring-security.html)
* [Testcontainers](https://java.testcontainers.org/)
* [Validation](https://docs.spring.io/spring-boot/4.0.2/reference/io/validation.html)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/4.0.2/reference/web/reactive.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Accessing data with R2DBC](https://spring.io/guides/gs/accessing-data-r2dbc/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)

### Additional Links

These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
* [R2DBC Homepage](https://r2dbc.io)

### Docker Compose support

This project contains a Docker Compose file named `compose.yaml`.
In this file, the following services have been defined:

* postgres: [`postgres:latest`](https://hub.docker.com/_/postgres)

Please review the tags of the used images and set them to the same as you're running in production.

### Testcontainers support

This project
uses [Testcontainers at development time](https://docs.spring.io/spring-boot/4.0.2/reference/features/dev-services.html#features.dev-services.testcontainers).

Testcontainers has been configured to use the following Docker images:

* [`postgres:latest`](https://hub.docker.com/_/postgres)

Please review the tags of the used images and set them to the same as you're running in production.

