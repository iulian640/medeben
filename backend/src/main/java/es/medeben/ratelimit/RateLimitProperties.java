package es.medeben.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del rate limiting transversal (prefijo {@code medeben.rate-limit}).
 *
 * <p>Dos presupuestos independientes: uno estricto por IP para
 * {@code /api/v1/auth/**} (anti fuerza bruta) y otro más generoso para el
 * resto de {@code /api/**}, por usuario autenticado cuando hay un JWT Bearer
 * sintácticamente válido, o por IP en caso contrario.
 *
 * <p>OJO con el presupuesto de auth: la IP de salida se comparte (WiFi del
 * local con toda la plantilla, CGNAT de las operadoras móviles), y el token
 * dura 24 h, así que al empezar el turno TODOS los del mismo WiFi hacen login
 * casi a la vez. La ráfaga (30) debe absorber una plantilla entera; la defensa
 * fina contra fuerza bruta sobre UNA cuenta es el bloqueo por cuenta
 * (pendiente de decisión de producto), no estrangular la IP entera.
 *
 * <p>Los valores por defecto (ver {@code application.yml}) están pensados
 * para producción; el perfil {@code test} lo deshabilita ({@code habilitado: false})
 * para no interferir con las suites existentes.
 *
 * @param habilitado     activa o desactiva el filtro por completo.
 * @param confiarEnProxy si es {@code true}, la IP del cliente se toma de la
 *                       cabecera {@code X-Forwarded-For} (primer valor); si
 *                       es {@code false} (por defecto) se usa siempre
 *                       {@code request.getRemoteAddr()}. Activarlo sin un
 *                       proxy de confianza delante permite a cualquier
 *                       cliente falsear su IP y saltarse el límite
 *                       (IP-spoofing) — solo debe activarse cuando de verdad
 *                       hay un proxy/balanceador que sobreescribe esa cabecera.
 * @param auth           presupuesto para las rutas de autenticación.
 * @param api            presupuesto para el resto de la API.
 * @param informes       presupuesto para {@code /api/v1/informes/**}: generar
 *                       un PDF cuesta MUCHO más que un GET normal (recorre el
 *                       año entero y maqueta el documento), así que con el
 *                       presupuesto genérico una sola cuenta podría sostener
 *                       ~120 generaciones/minuto — amplificación de CPU. Nadie
 *                       legítimo baja más de un puñado de informes seguidos.
 */
@ConfigurationProperties(prefix = "medeben.rate-limit")
public record RateLimitProperties(
        boolean habilitado,
        boolean confiarEnProxy,
        Presupuesto auth,
        Presupuesto api,
        Presupuesto informes) {

    public RateLimitProperties {
        auth = auth != null ? auth : Presupuesto.DEFECTO_AUTH;
        api = api != null ? api : Presupuesto.DEFECTO_API;
        informes = informes != null ? informes : Presupuesto.DEFECTO_INFORMES;
    }

    /**
     * Un presupuesto de peticiones: capacidad máxima (ráfaga) y recarga
     * proporcional al tiempo, expresada en peticiones por minuto.
     *
     * @param capacidad        tope de peticiones que se pueden consumir de golpe (ráfaga).
     * @param recargaPorMinuto peticiones que se recargan cada minuto, en régimen permanente.
     */
    public record Presupuesto(int capacidad, int recargaPorMinuto) {

        static final Presupuesto DEFECTO_AUTH = new Presupuesto(30, 20);
        static final Presupuesto DEFECTO_API = new Presupuesto(40, 120);
        static final Presupuesto DEFECTO_INFORMES = new Presupuesto(5, 3);
    }
}
