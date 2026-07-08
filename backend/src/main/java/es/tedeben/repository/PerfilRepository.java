package es.tedeben.repository;

import es.tedeben.domain.usuario.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PerfilRepository extends JpaRepository<Perfil, UUID> {
}
