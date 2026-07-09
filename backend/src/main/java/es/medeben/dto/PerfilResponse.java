package es.medeben.dto;

import es.medeben.domain.usuario.Perfil;

import java.math.BigDecimal;
import java.util.Map;

public record PerfilResponse(
        String provincia,
        String subsector,
        String convenioId,
        String puestoId,
        Map<String, String> dimensiones,
        BigDecimal salarioBaseMensual,
        BigDecimal plusesAnuales
) {

    public static PerfilResponse desde(Perfil p) {
        return new PerfilResponse(p.getProvincia(), p.getSubsector(), p.getConvenioId(),
                p.getPuestoId(), p.getDimensiones(), p.getSalarioBaseMensual(), p.getPlusesAnuales());
    }
}
