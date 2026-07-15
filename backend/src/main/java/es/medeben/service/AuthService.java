package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.usuario.Sesion;
import es.medeben.domain.usuario.Usuario;
import es.medeben.domain.usuario.VerificacionEmail;
import es.medeben.repository.SesionRepository;
import es.medeben.repository.UsuarioRepository;
import es.medeben.repository.VerificacionEmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro, login y ciclo de la sesión (D13.4 + B4). Reglas de seguridad:
 * BCrypt para el hash (nunca se guarda ni se registra la contraseña en claro),
 * mensaje de login único (anti enumeración), y verificación de contraseña
 * también cuando el email no existe (coste constante, anti timing).
 *
 * <p>Sesión en dos piezas (B4): un access JWT CORTO y stateless (los endpoints
 * no tocan BD para validarlo) y un refresh OPACO largo guardado hasheado en
 * {@code sesiones}, que sí se puede revocar (logout real; el borrado de cuenta
 * lo arrastra el cascade). El refresh ROTA en cada uso: gastar dos veces el
 * mismo token delata un robo y revoca todas las sesiones del usuario.
 */
@Service
@RequiereBaseDeDatos
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final int MIN_CARACTERES_PASSWORD = 10;

    /** 256 bits de entropía para el refresh opaco. */
    private static final int BYTES_REFRESH = 32;

    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final VerificacionEmailRepository verificaciones;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock reloj;
    private final Duration duracionToken;
    private final Duration duracionRefresh;
    private final RegistroDeUsuario registroDeUsuario;
    private final EmisorVerificacion emisorVerificacion;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Hash real de una contraseña aleatoria: iguala el coste del matches() cuando el email no existe (anti timing). */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarios,
                       SesionRepository sesiones,
                       VerificacionEmailRepository verificaciones,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Clock reloj,
                       @Value("${medeben.seguridad.jwt.duracion:PT15M}") Duration duracionToken,
                       @Value("${medeben.seguridad.refresh.duracion:P7D}") Duration duracionRefresh,
                       RegistroDeUsuario registroDeUsuario,
                       EmisorVerificacion emisorVerificacion) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.verificaciones = verificaciones;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.reloj = reloj;
        this.duracionToken = duracionToken;
        this.duracionRefresh = duracionRefresh;
        this.registroDeUsuario = registroDeUsuario;
        this.emisorVerificacion = emisorVerificacion;
        this.hashSenuelo = passwordEncoder.encode("señuelo-" + java.util.UUID.randomUUID());
    }

    /**
     * Registro con respuesta UNIFORME (cierre de la enumeración, R7): email
     * nuevo, existente sin verificar y existente verificado devuelven
     * EXACTAMENTE lo mismo — la señal real viaja solo por correo. Reglas:
     *
     * <ul>
     *   <li>Nuevo: se crea SIN verificar y se emite token de verificación,
     *       ambas cosas en la transacción PROPIA de {@link RegistroDeUsuario}
     *       (ver su javadoc: confina la carrera del UNIQUE de email a esa
     *       transacción para que no envenene esta).</li>
     *   <li>Existente (verificado O sin verificar): NO-OP. No se toca la
     *       contraseña, no se emite token, no se envía nada — una cuenta que
     *       ya existe no se puede pisar vía registro (esto es justo lo que
     *       revirtió el hallazgo CRITICAL de la revisión de seguridad de
     *       2026-07-15: la rama que sobrescribía la contraseña de una cuenta
     *       sin verificar permitía un account takeover con solo conocer el
     *       email de la víctima, agravado porque el login no exige
     *       verificación).</li>
     * </ul>
     *
     * <p>No es necesario que este método sea {@code @Transactional}: no
     * escribe nada directamente — la rama de email nuevo delega TODA la
     * escritura (usuario + token) en {@link RegistroDeUsuario}, y la rama de
     * email existente es un NO-OP puro.
     */
    public String registra(String email, String password) {
        String emailNormalizado = normaliza(email);
        if (password == null || password.length() < MIN_CARACTERES_PASSWORD) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + MIN_CARACTERES_PASSWORD + " caracteres");
        }
        // BCrypt solo usa los primeros 72 BYTES (ojo tildes/eñes en UTF-8: 2 bytes)
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 bytes)");
        }
        // El BCrypt se paga SIEMPRE, también cuando no se va a usar (cuenta ya
        // existente): si solo la rama que escribe pagara el hash, el tiempo de
        // respuesta sería un oráculo de enumeración (anti timing, mismo truco
        // que el señuelo del login).
        String hashNuevo = passwordEncoder.encode(password);

        Optional<Usuario> existente = usuarios.findByEmail(emailNormalizado);
        if (existente.isEmpty()) {
            try {
                registroDeUsuario.registra(emailNormalizado, hashNuevo);
            } catch (DataIntegrityViolationException e) {
                // Carrera con otro registro simultáneo del mismo email: la
                // restricción UNIQUE es la barrera real. Resultado uniforme
                // también aquí — el que llegó antes ya recibió su correo. Al
                // vivir en la transacción PROPIA de RegistroDeUsuario, este
                // catch no hereda ningún estado envenenado (ver su javadoc).
            }
        }
        // Email existente (verificado o no): NO-OP a propósito.
        // MEJORA FUTURA: reclamación de email por enlace al buzón (ver review seguridad 2026-07-15)
        return emailNormalizado;
    }

    /**
     * Consume el token del correo (atómico, un solo uso, ver
     * {@link VerificacionEmailRepository#marcaUsadaSiIntacta}) y sella al
     * usuario como verificado. Desconocido, caducado o ya usado: la MISMA
     * excepción, sin decir cuál (sin oráculo).
     */
    @Transactional
    public void verificaEmail(String token) {
        Instant ahora = Instant.now(reloj);
        VerificacionEmail verificacion = verificaciones.findByTokenHash(Sha256.hex(token))
                .orElseThrow(VerificacionInvalidaException::new);
        if (verificaciones.marcaUsadaSiIntacta(verificacion.getId(), ahora) == 0) {
            throw new VerificacionInvalidaException();
        }
        Usuario usuario = usuarios.findById(verificacion.getUsuarioId())
                .orElseThrow(VerificacionInvalidaException::new);
        usuario.marcaVerificado(ahora);
    }

    /**
     * Reenvía el correo de verificación. UNIFORME hacia fuera: email
     * desconocido o ya verificado no hacen nada y responden igual — este
     * endpoint no confirma la existencia de ninguna cuenta.
     */
    @Transactional
    public void reenviaVerificacion(String email) {
        usuarios.findByEmail(normaliza(email))
                .filter(usuario -> !usuario.isEmailVerificado())
                .ifPresent(emisorVerificacion::emite);
    }

    /**
     * Estado del usuario autenticado, FRESCO de BD (el claim del JWT envejece
     * 15 minutos y en una tablet compartida eso mezcla estados). Un usuario
     * borrado con token todavía vivo recibe el mismo 401 de siempre.
     */
    @Transactional(readOnly = true)
    public es.medeben.dto.MeResponse me(java.util.UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(CredencialesInvalidasException::new);
        return new es.medeben.dto.MeResponse(usuario.getEmail(), usuario.isEmailVerificado());
    }

    /** OJO: ya no es readOnly — abrir sesión PERSISTE la fila del refresh. */
    @Transactional
    public SesionEmitida login(String email, String password) {
        Optional<Usuario> usuario = usuarios.findByEmail(normaliza(email));
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashSenuelo);
        boolean coincide = passwordEncoder.matches(password, hash);
        if (usuario.isEmpty() || !coincide) {
            throw new CredencialesInvalidasException();
        }
        return emiteSesion(usuario.get());
    }

    /**
     * Cambia un refresh vivo por una sesión nueva (access + refresh ROTADO).
     * Nunca se dice el porqué de un rechazo: 401 idéntico para token
     * desconocido, caducado, revocado o reutilizado (sin oráculo).
     *
     * <p>{@code noRollbackFor} es SEGURIDAD, no un detalle: el camino del reuso
     * revoca todas las sesiones y DESPUÉS lanza el 401 — sin esta cláusula, la
     * excepción haría rollback de la propia revocación y el ladrón seguiría
     * dentro (bug real cazado en la verificación en vivo, no por los mocks).
     * Los demás rechazos no escriben nada, así que no les afecta.
     */
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public SesionEmitida refresca(String refreshToken) {
        Instant ahora = Instant.now(reloj);
        Sesion sesion = sesiones.findByTokenHash(Sha256.hex(refreshToken))
                .orElseThrow(CredencialesInvalidasException::new);
        if (sesion.getRevocadaEn() != null || ahora.isAfter(sesion.getCaducaEn())) {
            throw new CredencialesInvalidasException();
        }
        if (sesiones.marcaUsadaSiIntacta(sesion.getId(), ahora) == 0) {
            // Un refresh ya gastado vuelve a llegar: o carrera de dos pestañas o
            // un token robado. No se distingue → se cierra TODO para ese usuario
            // (coste: volver a hacer login; beneficio: el ladrón se queda fuera).
            sesiones.revocaTodas(sesion.getUsuarioId(), ahora);
            log.warn("Refresh reutilizado: sesiones revocadas para el usuario {}", sesion.getUsuarioId());
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = usuarios.findById(sesion.getUsuarioId())
                .orElseThrow(CredencialesInvalidasException::new);
        return emiteSesion(usuario);
    }

    /** Logout real: revoca el refresh en el servidor. Idempotente y sin eco. */
    @Transactional
    public void cierraSesion(String refreshToken) {
        sesiones.revocaPorHash(Sha256.hex(refreshToken), Instant.now(reloj));
    }

    private SesionEmitida emiteSesion(Usuario usuario) {
        Instant ahora = Instant.now(reloj);
        Instant expira = ahora.plus(duracionToken);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("medeben")
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .issuedAt(ahora)
                .expiresAt(expira)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();

        byte[] crudo = new byte[BYTES_REFRESH];
        aleatorio.nextBytes(crudo);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);
        Instant refreshCaduca = ahora.plus(duracionRefresh);
        sesiones.save(new Sesion(usuario.getId(), Sha256.hex(refresh), ahora, refreshCaduca));

        return new SesionEmitida(token, expira, refresh, refreshCaduca);
    }

    private static String normaliza(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
