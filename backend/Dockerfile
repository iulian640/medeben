# Build en dos etapas: compila con Maven y ejecuta con un JRE mínimo.
# OJO: el contexto de build es la RAÍZ del repo (ver docker-compose.prod.yml),
# no backend/ — el pom mapea ../convenios como recurso Maven y esos JSON
# tienen que estar dentro del contexto o el jar sale sin catálogo (el
# ConvenioCatalog hace fail-fast al arrancar, y hace bien).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app/backend
COPY backend/pom.xml .
# Las dependencias se cachean en su propia capa: los builds repetidos vuelan.
RUN mvn -B dependency:go-offline -q
# El corpus de convenios, donde el pom lo espera (../convenios).
COPY convenios /app/convenios
COPY backend/src ./src
# Los tests corren en CI; la imagen solo empaqueta.
RUN mvn -B package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario sin privilegios: el contenedor no corre como root.
RUN useradd --system --uid 1001 medeben
USER medeben
COPY --from=build /app/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
