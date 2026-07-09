# Build en dos etapas: compila con Maven y ejecuta con un JRE mínimo.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Las dependencias se cachean en su propia capa: los builds repetidos vuelan.
RUN mvn -B dependency:go-offline -q
COPY src ./src
# Los tests corren en CI; la imagen solo empaqueta.
RUN mvn -B package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario sin privilegios: el contenedor no corre como root.
RUN useradd --system --uid 1001 medeben
USER medeben
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
