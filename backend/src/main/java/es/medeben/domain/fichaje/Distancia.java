package es.medeben.domain.fichaje;

import java.math.BigDecimal;

/**
 * Distancia entre dos puntos (Haversine), dominio puro: sin Spring, sin
 * PostGIS — no se mete una extensión de PostgreSQL en producción por veinte
 * líneas de trigonometría (síntesis §4). El resultado se redondea a metros
 * enteros porque es la unidad en la que se guarda y se compara (D6).
 */
public final class Distancia {

    private static final double RADIO_TIERRA_METROS = 6_371_000.0;

    private Distancia() {
    }

    /** Distancia en metros entre (lat1, lon1) y (lat2, lon2), siempre &gt;= 0. */
    public static int metrosEntre(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return (int) Math.round(RADIO_TIERRA_METROS * c);
    }
}
