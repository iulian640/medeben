package es.medeben.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Errores como RFC 7807 (ProblemDetail). Los mensajes de negocio (404/422/400)
 * son seguros de exponer; los errores internos (500) se registran con detalle
 * pero salen con un mensaje genérico — nada de filtrar el estado interno.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para quedarse también con
 * los errores del framework (validación del body, JSON ilegible, parámetros
 * mal formados...): sin esto los atiende el handler de Spring Boot con su
 * genérico "Invalid request content." en inglés, sin decir qué campo falló
 * (issue #232). Al haber un bean de este tipo, el de Boot se retira solo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DatosConvenioPendientesException.class)
    public ProblemDetail datosPendientes(DatosConvenioPendientesException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(DimensionDesconocidaException.class)
    public ProblemDetail dimensionDesconocida(DimensionDesconocidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ResumenIncompletoException.class)
    public ProblemDetail resumenIncompleto(ResumenIncompletoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        // Código estable para que el frontend guíe sin adivinar sobre el texto.
        problema.setProperty("codigo", e.codigo().name());
        return problema;
    }

    @ExceptionHandler(es.medeben.service.EmailYaRegistradoException.class)
    public ProblemDetail emailYaRegistrado(es.medeben.service.EmailYaRegistradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(es.medeben.service.CredencialesInvalidasException.class)
    public ProblemDetail credencialesInvalidas(es.medeben.service.CredencialesInvalidasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ProblemDetail tokenInvalido(TokenInvalidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** 403, no 401: el usuario sigue autenticado — solo falló la re-confirmación. */
    @ExceptionHandler(es.medeben.service.PasswordIncorrectaException.class)
    public ProblemDetail passwordIncorrecta(es.medeben.service.PasswordIncorrectaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(es.medeben.service.SemanaSelladaException.class)
    public ProblemDetail semanaSellada(es.medeben.service.SemanaSelladaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(es.medeben.service.DiaSelladoException.class)
    public ProblemDetail diaSellado(es.medeben.service.DiaSelladoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail peticionInvalida(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Bean Validation del body: campo + motivo en castellano (los mensajes
     * viven en las anotaciones de los DTOs), igual que las validaciones hechas
     * a mano en los servicios. Orden alfabético para que la respuesta sea
     * estable aunque el validador recorra los campos como quiera.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respuesta400(detalle, e, headers, request);
    }

    /**
     * Cuerpo ilegible: JSON malformado, o un valor que el deserializador
     * rechaza (fecha con hora — ver JacksonConfig —, enum desconocido, tipo
     * cambiado). El detalle dice el campo y el motivo en castellano, pero
     * NUNCA el valor recibido: se ecoaría basura del cliente en la respuesta.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return respuesta400(detalleCuerpoIlegible(e.getCause()), e, headers, request);
    }

    private static String detalleCuerpoIlegible(Throwable causa) {
        if (causa instanceof InvalidFormatException formato) {
            String campo = rutaDelCampo(formato);
            if (formato.getTargetType() == LocalDate.class) {
                // El mensaje exacto vive en JacksonConfig; aquí solo se antepone el campo.
                return prefijo(campo) + formato.getOriginalMessage();
            }
            if (formato.getTargetType() != null && formato.getTargetType().isEnum()) {
                return prefijo(campo) + "no es un valor válido";
            }
            return prefijo(campo) + "no tiene el formato esperado";
        }
        if (causa instanceof JsonMappingException mapeo) {
            return prefijo(rutaDelCampo(mapeo)) + "no tiene el tipo esperado";
        }
        return "El cuerpo de la petición no es JSON válido";
    }

    private static String prefijo(String campo) {
        return campo.isEmpty() ? "" : campo + ": ";
    }

    /** "dias[0].tramos[1].entrada" — la ruta del campo dentro del body. */
    private static String rutaDelCampo(JsonMappingException e) {
        StringBuilder ruta = new StringBuilder();
        for (JsonMappingException.Reference referencia : e.getPath()) {
            if (referencia.getFieldName() != null) {
                if (!ruta.isEmpty()) {
                    ruta.append('.');
                }
                ruta.append(referencia.getFieldName());
            } else {
                ruta.append('[').append(referencia.getIndex()).append(']');
            }
        }
        return ruta.toString();
    }

    /**
     * Parámetro de query o path que no convierte a su tipo (una fecha que no
     * es yyyy-MM-dd, por ejemplo). El handler por defecto ecoa el valor
     * recibido ("Failed to convert 'fecha' with value: '...'"): aquí se dice
     * el parámetro y el formato esperado, y el valor jamás (criterio MesPath).
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String nombre = e instanceof MethodArgumentTypeMismatchException m ? m.getName() : e.getPropertyName();
        String pista = e.getRequiredType() == LocalDate.class ? " (fecha en formato yyyy-MM-dd)" : "";
        String detalle = (nombre == null ? "Hay un parámetro que" : "El parámetro '" + nombre + "'")
                + " no tiene el formato esperado" + pista;
        return respuesta400(detalle, e, headers, request);
    }

    private ResponseEntity<Object> respuesta400(String detalle, Exception e, HttpHeaders headers,
                                                WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        return handleExceptionInternal(e, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail errorInterno(IllegalStateException e) {
        log.error("Error interno de datos", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno de datos");
    }

    /** Red de seguridad explícita: nada inesperado sale con detalles internos. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e) {
        log.error("Error inesperado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");
    }
}
