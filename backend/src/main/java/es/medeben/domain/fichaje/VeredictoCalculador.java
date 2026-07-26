package es.medeben.domain.fichaje;

/**
 * Fórmula del veredicto de ubicación (síntesis §7), con la corrección
 * vinculante del contrato §Backend: banda de error al 95 % en vez del
 * radio de confianza crudo. {@code Location.getAccuracy()} en Android está
 * documentado como radio de confianza al 68 % (1 sigma) — usarlo como cota
 * dura sobreafirma (~32 % de los fixes caen fuera de ese radio) y un perito
 * lo tumba en una frase. Se dobla ({@link #FACTOR_SIGMA}) para acercarse al
 * 95 %, y queda como constante nombrada y documentada para que un tercero
 * pueda recomputar cada resultado con el mismo criterio (anexo técnico).
 *
 * <p>Dominio puro: sin Spring, sin BD. {@code d} = distancia Haversine al
 * centro, {@code p} = precisión reportada por el sistema, {@code r} = radio
 * del centro declarado. Nunca devuelve {@link VeredictoUbicacion#SUPRIMIDA}:
 * ese valor lo asigna el servicio cuando el titular ejerce su supresión.
 */
public final class VeredictoCalculador {

    /** Radio de confianza de Android es 68 % (1σ); doblarlo se acerca al 95 %. */
    public static final int FACTOR_SIGMA = 2;

    /** Por encima de esta precisión cruda, un fix no afirma nada: guarda de cordura. */
    public static final int PRECISION_MAXIMA_METROS = 200;

    private VeredictoCalculador() {
    }

    public static VeredictoUbicacion calcula(int distanciaMetros, int precisionMetros, int radioMetros) {
        if (precisionMetros > PRECISION_MAXIMA_METROS) {
            return VeredictoUbicacion.NO_CONCLUYENTE;
        }
        int banda = FACTOR_SIGMA * precisionMetros;
        if (distanciaMetros + banda <= radioMetros) {
            return VeredictoUbicacion.DENTRO;
        }
        if (distanciaMetros - banda > radioMetros) {
            return VeredictoUbicacion.FUERA;
        }
        return VeredictoUbicacion.NO_CONCLUYENTE;
    }
}
