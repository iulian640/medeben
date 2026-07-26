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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /*
     * OJO: aquí NO hay (ya) mapping de "email ya registrado" → 409. Ese 409
     * era el oráculo de enumeración de cuentas (auditoría R7): el registro
     * responde uniforme desde entonces. No lo reintroduzcas — un handler
     * "muerto" que solo mapea es la puerta a que un throw futuro reabra la fuga.
     */

    @ExceptionHandler(es.medeben.service.VerificacionInvalidaException.class)
    public ProblemDetail verificacionInvalida(es.medeben.service.VerificacionInvalidaException e) {
        // Detalle FIJO: token desconocido, caducado o ya usado responden igual.
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
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

    /** 403 explícito, sin 204 mudo (contrato §Backend): el cliente apaga la feature y avisa. */
    @ExceptionHandler(es.medeben.service.ConsentimientoUbicacionRequeridoException.class)
    public ProblemDetail consentimientoUbicacionRequerido(es.medeben.service.ConsentimientoUbicacionRequeridoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(es.medeben.service.UbicacionNoElegibleException.class)
    public ProblemDetail ubicacionNoElegible(es.medeben.service.UbicacionNoElegibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(es.medeben.service.UbicacionYaRegistradaException.class)
    public ProblemDetail ubicacionYaRegistrada(es.medeben.service.UbicacionYaRegistradaException e) {
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
     *
     * <p>El field path se sanea antes de concatenarlo: para las violaciones en
     * claves o valores de un mapa (dimensiones), Spring mete la clave LITERAL
     * del cliente en el path ("dimensiones[<clave>]") — sin truncar y con
     * cualquier carácter, NUL incluido. Sin el saneado, el detail ecoaría
     * texto arbitrario del cliente (criterio no-echo del issue #232).
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(err -> campoSaneado(err.getField()) + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respuesta400(detalle, e, headers, request);
    }

    /**
     * Path de campo que solo lleva vocabulario benigno: propiedades del DTO,
     * índices de lista y claves de mapa con pinta de identificador acotado
     * (las del catálogo de convenios lo son todas). Cualquier otra cosa viene
     * del cliente y no debe ecoarse.
     */
    private static final Pattern CAMPO_SEGURO = Pattern.compile(
            "[A-Za-z0-9_]{1,60}(\\[[A-Za-z0-9_-]{1,40}\\])?(\\.[A-Za-z0-9_]{1,60}(\\[[A-Za-z0-9_-]{1,40}\\])?)*");

    private static final Pattern PREFIJO_PROPIEDAD = Pattern.compile("[A-Za-z0-9_]{1,60}");

    /**
     * Si el path completo es benigno se deja tal cual (así "dimensiones[nivel]"
     * sigue orientando al cliente); si no, se recorta a la primera propiedad
     * más "[…]" para no ecoar jamás la clave recibida.
     */
    private static String campoSaneado(String campo) {
        if (CAMPO_SEGURO.matcher(campo).matches()) {
            return campo;
        }
        Matcher prefijo = PREFIJO_PROPIEDAD.matcher(campo);
        return prefijo.lookingAt() ? prefijo.group() + "[…]" : "[…]";
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
                // Texto fijo compartido con JacksonConfig, nunca el mensaje de la
                // excepción: así ningún valor recibido puede colarse en el detalle.
                return prefijo(campo) + es.medeben.config.JacksonConfig.MENSAJE_FECHA_ESTRICTA;
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

    /**
     * "dias[0].tramos[1].entrada" — la ruta del campo dentro del body.
     *
     * <p>Cuando el error ocurre dentro de un mapa (dimensiones), el fieldName
     * de la referencia de Jackson ES la clave literal del cliente (hasta
     * ~50.000 caracteres, con lo que sea). Solo se concatena si tiene pinta
     * de identificador benigno; si no, se colapsa a "[…]" — mismo criterio
     * no-echo que en {@link #campoSaneado(String)}.
     */
    private static String rutaDelCampo(JsonMappingException e) {
        StringBuilder ruta = new StringBuilder();
        for (JsonMappingException.Reference referencia : e.getPath()) {
            String nombre = referencia.getFieldName();
            if (nombre == null) {
                ruta.append('[').append(referencia.getIndex()).append(']');
            } else if (PREFIJO_PROPIEDAD.matcher(nombre).matches()) {
                if (!ruta.isEmpty()) {
                    ruta.append('.');
                }
                ruta.append(nombre);
            } else {
                ruta.append("[…]");
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
