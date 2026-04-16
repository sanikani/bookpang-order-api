# Local Standalone Run

This project can be started without Config Server or Eureka by using the `standalone` Spring profile.

## Prerequisites

- Java 21
- Local RabbitMQ running on `localhost:5672`
- RabbitMQ virtual host `sajotuna`
- RabbitMQ user `guest` / password `guest`

## Run

From the project root:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=standalone"
```

From an IDE:

- Set active profile to `standalone`
- Run `shop.sajotuna.order.OrderApplication`

## What Is Disabled

- Spring Cloud Config client
- Eureka client registration and registry fetch
- Discovery-based external service resolution at startup

## Notes

- The app still contains external API integrations such as `account-api` Feign and Toss Payments.
- Those integrations are not stubbed in this profile; they are expected to fail only when the related feature is actually called.
- H2 file database is used at `./data/orderdb`.
- H2 console is available at `/h2-console`.
- Health endpoint is available at `/actuator/health`.
