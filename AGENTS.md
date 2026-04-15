# Repository Guidelines

## Project Structure & Module Organization
This repository is a Java 17 multi-module Maven monorepo. Backend services live in `tj-*` modules such as `tj-gateway`, `tj-user`, and `tj-learning`; shared code lives in `tj-common` and cross-service contracts in `tj-api`. Standard backend layout is `src/main/java`, `src/main/resources`, and `src/test/java`. The frontend lives in `tj-front/tj-portal-src`. Ops assets are kept in `nacos/`, `sql/`, `docs/`, `startup.sh`, and `Dockerfile`.

## Build, Test, and Development Commands
Use Maven from the repository root for backend work:

- `mvn clean test` runs the backend test suite across modules.
- `mvn clean package -DskipTests` builds all service jars.
- `mvn -pl tj-user -am spring-boot:run -Dspring-boot.run.profiles=local` starts one service plus its local dependencies.
- `sh startup.sh -c gateway -n tj-gateway -d tj-gateway -p 8080` packages a service into Docker.

Use `tj-front/tj-portal-src` for frontend work:

- `npm install` installs dependencies from `package-lock.json`.
- `npm run dev` starts Vite in development mode on port `18082`.
- `npm run build:test` creates a test-mode production build.

## Coding Style & Naming Conventions
Follow the existing codebase: 4 spaces in Java, 2 spaces in Vue templates/scripts, UTF-8 resources, and package names under `com.tianji`. Use `PascalCase` for classes, `camelCase` for methods and fields, and preserve suffixes such as `*Controller`, `*Service`, `*ServiceImpl`, `*Mapper`, `*DTO`, `*VO`, `*PO`, and `*Query`. Mapper XML files belong in `src/main/resources/mapper` and should match the mapper interface name.

## Testing Guidelines
Backend tests use JUnit 5 through `spring-boot-starter-test`, typically with `@SpringBootTest`. Place tests in `src/test/java` and name them `*Test.java`, for example `StudentServiceImplTest`. There is no coverage gate in the POMs, so add tests for changed service, mapper, or controller paths and document manual verification when changes depend on Nacos, Redis, MQ, or payment/live integrations. No frontend test runner is currently configured.

## Commit & Pull Request Guidelines
Recent history mostly follows Conventional Commit-style prefixes such as `feat(aigc): ...` and `docs(api): ...`; use that format and scope commits by module when possible. Avoid vague messages like `test`. No PR template is checked in, so include a short summary, affected modules, config or SQL changes, test commands run, and screenshots for `tj-front` UI changes.

## Configuration & Environment Tips
Local development expects Nacos, MySQL, Redis, RabbitMQ, and related middleware described in `README.md` and `nacos/`. Keep secrets out of Git, prefer `application-local.yml` plus environment variables such as `TJ_NACOS_ADDR`, `TJ_HOST`, and `TJ_ES_URI`, and update matching entries in `sql/` or `nacos/` when a feature requires schema or config changes.
