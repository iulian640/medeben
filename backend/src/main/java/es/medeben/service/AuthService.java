package es.medeben.service;

import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import es.medeben.config.RequiereBaseDeDatos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro y login (D13.4: email + contraseña, JWT). Reglas de seguridad:
 * BCrypt para el hash (nunca se guarda ni se registra la contraseña en claro),
 * mensaje de login único (anti enumeración), y verificación de contraseña
 * también cuando el email no existe (coste constante, anti timing).
 */
@Service
@RequiereBaseDeDatos
public class AuthService {

    static final int MIN_CARACTERES_PASSWORD = 10;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration duracionToken;

    /** Hash real de una contraseña aleatoria: iguala el coste del matches() cuando el email no existe (anti timing). */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarios,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       @Value("${medeben.seguridad.jwt.duracion:PT24H}") Duration duracionToken) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.duracionToken = duracionToken;
        this.hashSenuelo = passwordEncoder.encode("señuelo-" + java.util.UUID.randomUUID());
    }

    @Transactional
    public Usuario registra(String email, String password) {
        String emailNormalizado = normaliza(email);
        if (password == null || password.length() < MIN_CARACTERES_PASSWORD) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + MIN_CARACTERES_PASSWORD + " caracteres");
        }
        // BCrypt solo usa los primeros 72 BYTES (ojo tildes/eñes en UTF-8: 2 bytes)
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 bytes)");
        }
        if (usuarios.findByEmail(emailNormalizado).isPresent()) {
            throw new EmailYaRegistradoException();
        }
        try {
            return usuarios.saveAndFlush(new Usuario(emailNormalizado, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // Carrera con otro registro simultáneo del mismo email: la restricción
            // UNIQUE de la BD es la barrera real; el findByEmail solo da mejor mensaje.
            throw new EmailYaRegistradoException();
        }
    }

    @Transactional(readOnly = true)
    public TokenEmitido login(String email, String password) {
        Optional<Usuario> usuario = usuarios.findByEmail(normaliza(email));
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashSenuelo);
        boolean coincide = passwordEncoder.matches(password, hash);
        if (usuario.isEmpty() || !coincide) {
            throw new CredencialesInvalidasException();
        }
        return emiteToken(usuario.get());
    }

    private TokenEmitido emiteToken(Usuario usuario) {
        Instant ahora = Instant.now();
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
        return new TokenEmitido(token, expira);
    }

    private static String normaliza(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
