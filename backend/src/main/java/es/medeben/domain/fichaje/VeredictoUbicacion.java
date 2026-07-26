package es.medeben.domain.fichaje;

/**
 * Veredicto geométrico de la anotación de ubicación (síntesis §6/D6, corrección
 * vinculante del contrato: se añade {@link #SUPRIMIDA}).
 *
 * <ul>
 *   <li>{@link #DENTRO} — compatible con el centro incluso en el peor caso del margen de error.</li>
 *   <li>{@link #FUERA} — incompatible con el centro incluso en el mejor caso del margen de error.</li>
 *   <li>{@link #NO_CONCLUYENTE} — la banda de error cruza el borde, o la precisión reportada es demasiado mala para afirmar nada.</li>
 *   <li>{@link #SUPRIMIDA} — el titular retiró esta anotación (art. 17 RGPD, supresión granular); las coordenadas se anulan y el veredicto original no se conserva.</li>
 * </ul>
 */
public enum VeredictoUbicacion {
    DENTRO,
    FUERA,
    NO_CONCLUYENTE,
    SUPRIMIDA
}
