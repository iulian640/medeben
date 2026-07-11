package es.medeben.config;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.Writer;

/**
 * Sustituye la página HTML de error de Tomcat por un RFC 7807 mínimo.
 *
 * <p>Hay errores que el contenedor corta ANTES de que la petición llegue a
 * Spring MVC — una barra codificada ({@code %2F}) en la ruta, por ejemplo — y
 * ahí ni el GlobalExceptionHandler ni la entry point pintan nada: respondía
 * el {@link ErrorReportValve} de serie con su HTML, rompiendo la promesa de
 * que todos los errores de la API salen como problem+json (issue #232).
 *
 * <p>El cuerpo es fijo por rango de estado: aquí no se sabe (ni se debe
 * contar) el porqué exacto, y la URI pedida es basura del cliente que no se
 * ecoa. La instancia la registra {@code TomcatErroresConfig}.
 */
public class ProblemDetailErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int estado = response.getStatus();
        // Mismas guardas que el valve de serie: solo errores, solo si nadie
        // escribió ya un cuerpo, y solo una vez.
        if (estado < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        try {
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("utf-8");
            Writer salida = response.getReporter();
            if (salida != null) {
                salida.write(cuerpo(estado));
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            // Si ni siquiera se puede escribir el cuerpo, el estado ya viaja solo.
        }
    }

    /** JSON construido a mano con textos fijos: nada del cliente acaba aquí. */
    private static String cuerpo(int estado) {
        String detalle = estado >= 500 ? "Error interno" : "Petición inválida";
        return "{\"type\":\"about:blank\",\"title\":\"" + titulo(estado)
                + "\",\"status\":" + estado + ",\"detail\":\"" + detalle + "\"}";
    }

    private static String titulo(int estado) {
        HttpStatus conocido = HttpStatus.resolve(estado);
        return conocido != null ? conocido.getReasonPhrase() : "Error";
    }
}
