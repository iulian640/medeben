package es.medeben.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/**
 * Rate limiting transversal para {@code /api/**}, con dos presupuestos
 * independientes (ver {@link RateLimitProperties}):
 *
 * <ul>
 *   <li>{@code /api/v1/auth/**} (login, registro): estricto, siempre por IP
 *       — antes de autenticar no hay usuario todavía, así que es la única
 *       clave posible (y es justo lo que protege de fuerza bruta).</li>
 *   <li>Resto de {@code /api/**}: por el {@code sub} del JWT si la cabecera
 *       {@code Authorization: Bearer} trae un token sintácticamente válido
 *       (no hace falta verificar la firma: aquí solo se usa como clave de
 *       cubeta, la verificación real la hace Spring Security más adelante
 *       en la cadena); si no, por IP.</li>
 * </ul>
 *
 * <p>Se registra en {@link es.medeben.config.SecurityConfig} ANTES del
 * filtro de autenticación Bearer, para frenar la fuerza bruta en
 * {@code /auth/**} sin gastar CPU validando JWT primero, y para que el
 * límite se aplique aunque el token no llegue a autenticar.
 *
 * <p>Al superar el presupuesto responde 429 con un {@link ProblemDetail}
 * (RFC 7807, mismo estilo que {@code GlobalExceptionHandler}) y cabecera
 * {@code Retry-After}. El detalle es un mensaje fijo: nunca repite nada de
 * lo que envió el cliente.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIJO_API = "/api/";
    private static final String PREFIJO_AUTH = "/api/v1/auth/";
    private static final String RUTA_HEALTH = "/api/v1/health";
    private static final String PREFIJO_BEARER = "Bearer ";
    private static final String CABECERA_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String MENSAJE_LIMITE =
            "Has superado el límite de peticiones. Inténtalo de nuevo en unos segundos.";

    /**
     * Longitud máxima del claim {@code sub} usable como clave de cubeta. Los
     * subs legítimos son UUIDs (36 chars); un sub kilométrico solo puede ser
     * un token forjado intentando inflar la memoria del registro → cae a IP.
     */
    private static final int MAX_LONGITUD_SUB = 64;

    /** Payload Base64 más grande que esto ni se decodifica (tokens forjados gigantes). */
    private static final int MAX_LONGITUD_PAYLOAD = 4096;

    private final RateLimitProperties propiedades;
    private final RegistroCubetas registro;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitProperties propiedades, Clock reloj, ObjectMapper objectMapper) {
        this.propiedades = propiedades;
        this.registro = new RegistroCubetas(reloj);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!propiedades.habilitado()) {
            return true;
        }
        // El preflight CORS (OPTIONS) no consume presupuesto: un 429 sin
        // cabeceras CORS se vería como error de CORS, no como límite.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String ruta = rutaNormalizada(request);
        // El health check vive bajo /api/ y lo sondean balanceadores/monitores
        // desde una misma IP: limitarlo marcaría instancias sanas como caídas.
        return !ruta.startsWith(PREFIJO_API) || ruta.equals(RUTA_HEALTH);
    }

    /**
     * URI con las barras duplicadas colapsadas antes de decidir el presupuesto.
     * StrictHttpFirewall (activo por defecto) ya rechaza con 400 las rutas con
     * {@code //}, {@code ;} o escapes sospechosos ANTES de llegar aquí, así que
     * esto es cinturón y tirantes: si algún día alguien relaja el firewall,
     * {@code /api/v1//auth/login} seguiría cayendo en el presupuesto estricto.
     */
    private static String rutaNormalizada(HttpServletRequest request) {
        return request.getRequestURI().replaceAll("/{2,}", "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        boolean esAuth = rutaNormalizada(request).startsWith(PREFIJO_AUTH);
        RateLimitProperties.Presupuesto presupuesto = esAuth ? propiedades.auth() : propiedades.api();
        String clave = (esAuth ? "auth:" : "api:") + claveDelCliente(request, esAuth);

        boolean permitido = registro.intentaConsumir(clave, presupuesto.capacidad(), presupuesto.recargaPorMinuto());
        if (permitido) {
            filterChain.doFilter(request, response);
            return;
        }
        escribeRespuestaDemasiadasPeticiones(response, registro.segundosHastaReintento(clave));
    }

    private String claveDelCliente(HttpServletRequest request, boolean esAuth) {
        if (!esAuth) {
            Optional<String> sub = extraeSubDelBearer(request);
            if (sub.isPresent()) {
                return "sub:" + sub.get();
            }
        }
        return "ip:" + resuelveIp(request);
    }

    /**
     * IP del cliente. Solo se confía en {@code X-Forwarded-For} cuando
     * {@code medeben.rate-limit.confiar-en-proxy} está activo (por defecto
     * no); en caso contrario se usa siempre {@code getRemoteAddr()}. Confiar
     * en esa cabecera sin un proxy de confianza delante permitiría a
     * cualquier cliente falsear su IP y saltarse el límite.
     */
    private String resuelveIp(HttpServletRequest request) {
        if (propiedades.confiarEnProxy()) {
            String cabecera = request.getHeader(CABECERA_X_FORWARDED_FOR);
            if (cabecera != null && !cabecera.isBlank()) {
                return cabecera.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Lee el claim {@code sub} de un JWT Bearer sin verificar su firma (la
     * verificación la hace más adelante el resource server de Spring
     * Security). Solo sirve para elegir la cubeta; un token con firma
     * inválida devolverá igualmente 401 en la cadena de seguridad.
     */
    private Optional<String> extraeSubDelBearer(HttpServletRequest request) {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
            return Optional.empty();
        }
        String[] partes = cabecera.substring(PREFIJO_BEARER.length()).trim().split("\\.");
        if (partes.length != 3) {
            return Optional.empty();
        }
        if (partes[1].length() > MAX_LONGITUD_PAYLOAD) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(partes[1]);
            JsonNode nodo = objectMapper.readTree(payload);
            String sub = nodo.path("sub").asText(null);
            if (sub == null || sub.isBlank() || sub.length() > MAX_LONGITUD_SUB) {
                // Sin sub usable (o un sub kilométrico forjado): cae a IP.
                return Optional.empty();
            }
            return Optional.of(sub);
        } catch (IllegalArgumentException | IOException e) {
            // Token no es Base64/JSON válido: no es "sintácticamente válido", cae a IP.
            return Optional.empty();
        }
    }

    private void escribeRespuestaDemasiadasPeticiones(HttpServletResponse response, long retryAfterSegundos)
            throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, MENSAJE_LIMITE);
        problema.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(retryAfterSegundos, 1)));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problema);
    }
}
