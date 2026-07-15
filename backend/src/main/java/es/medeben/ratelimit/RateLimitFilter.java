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
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Rate limiting transversal para {@code /api/**}, con varios presupuestos
 * independientes (ver {@link RateLimitProperties}):
 *
 * <ul>
 *   <li>{@code /api/v1/auth/**} (login, logout): estricto, siempre por IP
 *       — antes de autenticar no hay usuario todavía, así que es la única
 *       clave posible (y es justo lo que protege de fuerza bruta).</li>
 *   <li>{@code /api/v1/auth/registro}: presupuesto PROPIO, más estricto
 *       todavía que el de auth (el 409 de email duplicado permite enumerar
 *       cuentas), también siempre por IP.</li>
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
    private static final String PREFIJO_INFORMES = "/api/v1/informes/";
    private static final String RUTA_REFRESH = "/api/v1/auth/refresh";
    private static final String RUTA_REGISTRO = "/api/v1/auth/registro";
    private static final String RUTA_CUENTA = "/api/v1/cuenta";
    private static final String RUTA_HEALTH = "/api/v1/health";
    private static final String PREFIJO_BEARER = "Bearer ";
    private static final String CABECERA_X_FORWARDED_FOR = "X-Forwarded-For";
    /** Clave única del bucket global de /auth/registro (compartido por todas las IPs). */
    private static final String CLAVE_REGISTRO_GLOBAL = "registro:global";
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
    private final List<IpAddressMatcher> proxiesDeConfianza;

    public RateLimitFilter(RateLimitProperties propiedades, Clock reloj, ObjectMapper objectMapper) {
        this.propiedades = propiedades;
        this.registro = new RegistroCubetas(reloj);
        this.objectMapper = objectMapper;
        // Los matchers se compilan una vez aquí: IpAddressMatcher parsea el CIDR
        // en el constructor y un CIDR inválido en la config revienta el arranque
        // (mejor que descubrirlo en la primera petición).
        this.proxiesDeConfianza = propiedades.proxiesDeConfianza().stream()
                .map(IpAddressMatcher::new)
                .toList();
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
        String ruta = rutaNormalizada(request);
        // El refresh (B4) va ANTES que auth: cuelga de /auth/ pero es tráfico
        // sostenido legítimo (cada usuario activo, ~4/hora durante todo el
        // turno) y metería a la plantilla entera en el bucket estricto del
        // login (security review). Presupuesto propio, también por IP.
        boolean esRefresh = ruta.equals(RUTA_REFRESH);
        // El registro (auditoría): el 409 (email ya registrado) frente al 201
        // permite enumerar cuentas por fuerza bruta. Presupuesto PROPIO, mucho
        // más estrecho que el genérico de auth (pensado para que una plantilla
        // entera haga login a la vez, no para un evento raro por IP como
        // registrarse) — también por IP, antes de autenticar no hay otra clave.
        boolean esRegistro = !esRefresh && ruta.equals(RUTA_REGISTRO);
        boolean esAuth = !esRefresh && !esRegistro && ruta.startsWith(PREFIJO_AUTH);
        // Los informes PDF llevan su propio presupuesto, mucho más estrecho:
        // generarlos cuesta un año de recorrido + maquetado (ver Properties).
        boolean esInforme = !esRefresh && !esRegistro && !esAuth && ruta.startsWith(PREFIJO_INFORMES);
        // El borrado de cuenta re-confirma la contraseña: mismo control
        // anti-fuerza-bruta que el login → presupuesto ESTRICTO de auth
        // (security review). La clave sigue siendo ip|sub: el atacante con un
        // token queda confinado sin castigar a los legítimos de un WiFi común.
        boolean esCuenta = !esRefresh && !esRegistro && !esAuth && !esInforme && ruta.equals(RUTA_CUENTA);
        RateLimitProperties.Presupuesto presupuesto =
                esRefresh ? propiedades.refresh()
                        : esRegistro ? propiedades.registro()
                        : esAuth || esCuenta ? propiedades.auth()
                        : esInforme ? propiedades.informes() : propiedades.api();
        String grupo = esRefresh ? "refresh:"
                : esRegistro ? "registro:"
                : esAuth ? "auth:" : esInforme ? "informes:" : esCuenta ? "cuenta:" : "api:";
        // Igual que auth/refresh: antes de autenticar no hay sub legítimo
        // posible, así que la clave es SIEMPRE la IP (nunca intenta leer un
        // Bearer, que aquí solo podría ser un token forjado para esquivar el
        // tope rotando el claim sub).
        String clave = grupo + claveDelCliente(request, esAuth || esRefresh || esRegistro);

        boolean permitido = registro.intentaConsumir(clave, presupuesto.capacidad(), presupuesto.recargaPorMinuto());
        if (!permitido) {
            escribeRespuestaDemasiadasPeticiones(response, registro.segundosHastaReintento(clave));
            return;
        }
        // El registro lleva ADEMÁS un bucket GLOBAL, compartido por todas las
        // IPs (second review): el bucket por IP no frena la enumeración
        // distribuida (mil IPs, un email cada una); este acota el total del
        // sistema. Se consume DESPUÉS del de IP: así el tráfico de una IP
        // abusona sigue contando también contra su propia cubeta.
        if (esRegistro) {
            RateLimitProperties.Presupuesto global = propiedades.registroGlobal();
            boolean permitidoGlobal = registro.intentaConsumir(
                    CLAVE_REGISTRO_GLOBAL, global.capacidad(), global.recargaPorMinuto());
            if (!permitidoGlobal) {
                escribeRespuestaDemasiadasPeticiones(
                        response, registro.segundosHastaReintento(CLAVE_REGISTRO_GLOBAL));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Clave de la cubeta. La IP (resuelta con confianza en proxy) SIEMPRE forma
     * parte de la clave: así una sola IP no puede crear cubetas ilimitadas ni
     * relajar su presupuesto. Para rutas autenticadas se añade el {@code sub}
     * del JWT (solo sintáctico) para separar a usuarios legítimos tras una misma
     * IP compartida (WiFi del local, CGNAT), pero un atacante con un sub
     * rotatorio desde una IP sigue confinado a las cubetas de ESA IP.
     *
     * <p>Fallo de seguridad corregido: antes la rama con sub NO incluía la IP,
     * así que un token forjado con sub distinto en cada petición esquivaba el
     * tope por IP y podía llenar el registro entero desde una sola máquina.
     *
     * <p>Residual conocido (mejora futura): una IP con subs rotatorios aún puede
     * crear una cubeta por sub hasta el tope global {@code MAX_CUBETAS}; el
     * cierre completo (presupuesto agregado por IP, o mover el límite por
     * usuario a DESPUÉS de la autenticación con el principal verificado) es un
     * rediseño con trade-off de usabilidad en CGNAT/WiFi compartido.
     */
    private String claveDelCliente(HttpServletRequest request, boolean esAuth) {
        String ip = resuelveIp(request);
        if (!esAuth) {
            Optional<String> sub = extraeSubDelBearer(request);
            if (sub.isPresent()) {
                return "ip:" + ip + "|sub:" + sub.get();
            }
        }
        return "ip:" + ip;
    }

    /**
     * IP del cliente. Solo se confía en {@code X-Forwarded-For} cuando
     * {@code medeben.rate-limit.confiar-en-proxy} está activo (por defecto no)
     * Y la petición llega desde un peer de {@code proxies-de-confianza}
     * (second review): si el backend queda expuesto sin el nginx delante, un
     * cliente directo podría forjar la cabecera; al no estar su IP en la lista
     * de proxies, aquí se ignora y se usa {@code getRemoteAddr()}.
     *
     * <p>CLAVE (fallo de seguridad corregido): se toma el ÚLTIMO valor de la
     * lista, no el primero. Nuestro proxy de confianza (nginx con
     * {@code $proxy_add_x_forwarded_for}) ANEXA la IP real del cliente al final
     * de lo que llegue; los valores anteriores los pudo poner el propio cliente.
     * Coger el primero permitiría a cualquiera mandar {@code X-Forwarded-For:
     * 1.2.3.4} y, rotándolo, saltarse por completo el límite anti-fuerza-bruta
     * del login (la única defensa, no hay bloqueo por cuenta). El último valor
     * es el que escribió nuestro proxy y el cliente no controla.
     */
    private String resuelveIp(HttpServletRequest request) {
        if (propiedades.confiarEnProxy() && esProxyDeConfianza(request.getRemoteAddr())) {
            String cabecera = request.getHeader(CABECERA_X_FORWARDED_FOR);
            if (cabecera != null && !cabecera.isBlank()) {
                String[] saltos = cabecera.split(",");
                return saltos[saltos.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean esProxyDeConfianza(String remoteAddr) {
        return proxiesDeConfianza.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
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
