package es.medeben.config;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link ProblemDetailErrorReportValve} en el host de Tomcat: los
 * errores que el contenedor genera por su cuenta (URI rechazada antes de
 * llegar a Spring) salen como problem+json, igual que el resto de la API.
 */
@Configuration
public class TomcatErroresConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> erroresDelContenedorComoProblemDetail() {
        return factory -> factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ProblemDetailErrorReportValve.class.getName());
            }
        });
    }
}
