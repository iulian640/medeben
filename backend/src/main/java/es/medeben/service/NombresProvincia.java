package es.medeben.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Grafías equivalentes del nombre de UNA MISMA provincia (#233): el perfil usa
 * el vocabulario del ámbito territorial de los convenios ("Cáceres", "Bizkaia",
 * "Illes Balears") y los anexos provinciales de la colectiva publican el suyo
 * ("Caceres", "Vizcaya", "Islas Baleares"). Aquí solo se pliegan acentos y se
 * cruzan los nombres cooficiales de la misma provincia — JAMÁS se acercan dos
 * provincias distintas: un nombre que no pliega igual no casa, y ese caso lo
 * resuelve el 422 con las opciones reales, no una adivinanza.
 */
final class NombresProvincia {

    /** Nombre cooficial → forma castellana de los boletines, ambos YA plegados (sin acentos, en minúscula). */
    private static final Map<String, String> COOFICIALES = Map.of(
            "bizkaia", "vizcaya",
            "gipuzkoa", "guipuzcoa",
            "illes balears", "islas baleares");

    private NombresProvincia() {
    }

    /** true si ambos nombres designan la misma provincia (misma grafía plegada o cooficialidad conocida). */
    static boolean mismaProvincia(String uno, String otro) {
        return clave(uno).equals(clave(otro));
    }

    private static String clave(String nombre) {
        String plegado = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return COOFICIALES.getOrDefault(plegado, plegado);
    }
}
