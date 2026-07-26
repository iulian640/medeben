package es.medeben.service;

/**
 * 403: no hay consentimiento vigente para "Anotar dónde fichas" (contrato
 * §Backend, corrección vinculante — NO 204 mudo). Primer filtro de todos los
 * endpoints de /ubicacion y /centro-trabajo: el cliente apaga la feature y avisa.
 */
public class ConsentimientoUbicacionRequeridoException extends RuntimeException {

    public ConsentimientoUbicacionRequeridoException() {
        super("No hay consentimiento vigente para anotar dónde fichas");
    }
}
