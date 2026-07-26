package es.medeben.service;

/**
 * Texto de consentimiento de "Anotar dónde fichas", v1.0 — CANÓNICO. Copiado
 * literal del contrato de implementación (decisiones-implementacion.md); esta
 * constante es la fuente de verdad que el servidor hashea (SHA-256) para
 * acreditar QUÉ se consintió (art. 7.1 RGPD). Si el texto cambia, la versión
 * DEBE bumpearse — nunca editar este bloque sin subir {@link #VERSION_ACTUAL}.
 */
public final class ConsentimientoUbicacionTexto {

    public static final String VERSION_ACTUAL = "1.0";

    public static final String TEXTO_V1_0 = """
            Anotar dónde fichas — consentimiento (v1.0)

            Si activas esta opción, cuando fiches al momento la app anotará tu posición
            aproximada (precisión de barrio, no de portal) junto a ese fichaje.

            - Finalidad: acompañar tu registro horario con una anotación de que estabas en tu
              centro de trabajo, para reforzar la coherencia de tu libreta.
            - Base jurídica: tu consentimiento inequívoco y expreso (art. 6.1.a RGPD). La app
              funciona entera sin esto.
            - Qué se envía y a dónde: la posición aproximada sale de tu dispositivo y se guarda
              en el servidor de MeDeben, alojado en la Unión Europea. La resolución de la
              posición aproximada la realiza el proveedor de ubicación de tu sistema operativo
              (Google Play Services) según su propia política.
            - Qué NO se hace: nada en segundo plano, nunca se te sigue, solo se anota en el
              instante en que tú fichas. Tus coordenadas no aparecen en el informe que se
              entrega a terceros; solo en un anexo que controlas tú.
            - Conservación: hasta 15 meses desde cada fichaje, salvo que declares una
              reclamación en curso. Después se borra; tu fichaje permanece.
            - Puedes retirar este consentimiento en cualquier momento con un toque, sin que
              afecte a nada de lo demás, y borrar todo tu histórico de ubicaciones cuando
              quieras. Retirarlo no afecta a la licitud del tratamiento previo.
            - Derechos: acceso, rectificación, supresión y portabilidad escribiendo al
              responsable; también puedes reclamar ante la AEPD (aepd.es).
            - Importante: tu empresa no puede exigirte activar esto ni entregarle el anexo con
              tus coordenadas. Si te lo piden, eso es control por geolocalización y debe cumplir
              el art. 90 de la LOPDGDD.
            """;

    private ConsentimientoUbicacionTexto() {
    }
}
