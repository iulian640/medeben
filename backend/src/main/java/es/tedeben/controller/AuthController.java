package es.tedeben.controller;

import es.tedeben.dto.LoginRequest;
import es.tedeben.dto.RegistroRequest;
import es.tedeben.dto.TokenResponse;
import es.tedeben.dto.UsuarioResponse;
import es.tedeben.service.AuthService;
import es.tedeben.service.TokenEmitido;
import jakarta.validation.Valid;
import es.tedeben.config.RequiereBaseDeDatos;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro y login (D13.4). Fuera del perfil `local` (sin BD no hay usuarios;
 * ese perfil es solo para consultar convenios).
 */
@RestController
@RequestMapping("/api/v1")
@RequiereBaseDeDatos
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest peticion) {
        return new UsuarioResponse(auth.registra(peticion.email(), peticion.password()).getEmail());
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest peticion) {
        TokenEmitido emitido = auth.login(peticion.email(), peticion.password());
        return new TokenResponse(emitido.token(), emitido.expiraEn());
    }

    /** Quién soy: valida el token y devuelve el email del usuario autenticado. */
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new UsuarioResponse(jwt.getClaimAsString("email"));
    }
}
