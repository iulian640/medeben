package es.tedeben.service;

/**
 * Una cita de fuente (D34/D18: "no me creas, compruébalo"): el texto en
 * cristiano y, cuando existe, el enlace al documento oficial — el PDF del
 * boletín para citas del convenio, el BOE consolidado para las del Estatuto
 * de los Trabajadores. `url` puede ser null (8 convenios sin URL de fuente).
 */
public record Cita(String texto, String url) {

    /** Texto consolidado del Estatuto de los Trabajadores en el BOE. */
    public static final String URL_ESTATUTO_TRABAJADORES =
            "https://www.boe.es/buscar/act.php?id=BOE-A-2015-11430";

    public static Cita delEstatuto(String texto) {
        return new Cita(texto, URL_ESTATUTO_TRABAJADORES);
    }
}
