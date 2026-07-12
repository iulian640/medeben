package es.medeben.controller;

import es.medeben.domain.convenio.Convenio;
import es.medeben.dto.CalculoHorasExtraRequest;
import es.medeben.dto.CalculoHorasExtraResponse;
import es.medeben.dto.DesgloseValorHora;
import es.medeben.dto.SalarioBaseRequest;
import es.medeben.dto.SalarioBaseResponse;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.service.CalculoConvenioService;
import es.medeben.service.Cita;
import es.medeben.service.HorasExtraCalculadas;
import es.medeben.service.SalarioBaseResuelto;
import es.medeben.service.SmiService;
import es.medeben.service.TablaSalarialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cálculos sobre convenios. Sin datos personales: entradas anónimas (salario,
 * horas) y salidas con citas de artículo (D34). POST para llevar el cuerpo,
 * pero sin efectos: no se guarda nada.
 */
@RestController
@RequestMapping("/api/v1/calculo")
public class CalculoController {

    private final ConvenioCatalog convenios;
    private final CalculoConvenioService calculo;
    private final TablaSalarialService tablas;
    private final SmiService smi;

    public CalculoController(ConvenioCatalog convenios, CalculoConvenioService calculo,
                             TablaSalarialService tablas, SmiService smi) {
        this.convenios = convenios;
        this.calculo = calculo;
        this.tablas = tablas;
        this.smi = smi;
    }

    @PostMapping("/horas-extra")
    public CalculoHorasExtraResponse horasExtra(@Valid @RequestBody CalculoHorasExtraRequest peticion) {
        Convenio convenio = convenios.porId(peticion.convenioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Convenio no encontrado: " + peticion.convenioId()));

        // Las dimensiones (opcionales) viajan al motor: en la colectiva la
        // jornada y las pagas van por provincia (#231) — sin ellas, 422 honesto.
        HorasExtraCalculadas resultado = calculo.importeHorasExtra(
                        convenio, Year.of(peticion.anio()),
                        peticion.salarioBaseMensual(), peticion.plusesONada(), peticion.horas(),
                        peticion.dimensionesONada())
                .orElseThrow(() -> new DatosConvenioPendientesException(
                        "El convenio no tiene publicados los datos necesarios (jornada anual o pagas) para "
                                + peticion.anio()));

        return new CalculoHorasExtraResponse(resultado.precioHora(), resultado.importe(),
                DesgloseValorHora.desde(resultado.desglose()), resultado.citas());
    }

    @PostMapping("/salario-base")
    public SalarioBaseResponse salarioBase(@Valid @RequestBody SalarioBaseRequest peticion) {
        var resuelto = tablas.salarioBaseMinimo(peticion.convenioId(), peticion.dimensiones(), peticion.fecha())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Sin tabla salarial aplicable para esas dimensiones y fecha en "
                                + peticion.convenioId()));
        return conAvisoSmi(resuelto, peticion.convenioId(), peticion.dimensiones(),
                peticion.fecha().getYear());
    }

    /**
     * Añade el suelo del SMI: compara el salario del convenio en cómputo ANUAL
     * contra el SMI anual y, si no llega, marca {@code bajoSmi} y suma la cita
     * legal del SMI. Maneja las DOS unidades del corpus:
     * <ul>
     *   <li>EUR/año (Cuenca): el importe ya es anual, se compara directo.</li>
     *   <li>EUR/mes: importe × mensualidades del convenio (14 si no las publica).</li>
     * </ul>
     * El cómputo usa solo el salario base (pluses = 0): puede sobre-avisar en
     * categorías que con pluses llegarían, por eso el aviso dice "si no llega, la
     * diferencia es tuya" en vez de afirmar una cifra. Otras unidades (EUR/hora)
     * no se comparan como salario.
     */
    private SalarioBaseResponse conAvisoSmi(SalarioBaseResuelto r, String convenioId,
                                            Map<String, String> dimensiones, int anio) {
        BigDecimal mensualidades;
        if ("EUR/año".equals(r.unidad())) {
            mensualidades = BigDecimal.ONE; // el importe ya es el cómputo anual
        } else if ("EUR/mes".equals(r.unidad())) {
            // Resuelto por dimensiones (issue #231): la colectiva publica las
            // pagas por anexo provincial, no en la raíz. Solo se cae al 14 si no
            // constan en ningún sitio — no para toda la colectiva por defecto.
            mensualidades = convenios.porId(convenioId)
                    .flatMap(c -> calculo.mensualidades(c, dimensiones, Year.of(anio)))
                    .orElse(BigDecimal.valueOf(14));
        } else {
            return new SalarioBaseResponse(r.importe(), r.unidad(), false, null, null, null, r.citas());
        }
        // El suelo del SMI en la unidad de la respuesta: SMI anual repartido entre
        // las mensualidades de ESTE convenio (con EUR/año, mensualidades=1 y queda
        // el anual tal cual). La misma cuenta que aplica el resumen mensual, viva
        // una sola vez en SmiService para que pantalla y papel no se contradigan.
        SmiService.SueloSmi suelo = smi.aplicaSuelo(r.importe(), mensualidades, BigDecimal.ZERO, anio);
        if (!suelo.bajoSmi()) {
            // Legal pero con pinta de ilegal: un mensual bajo el SMI mensual que
            // cumple en cómputo anual gracias a >14 pagas (el caso Pontevedra /
            // Almería). Quien lo vea lo va a comparar con el SMI de los titulares:
            // se mandan los números de la cuenta anual para que la UI se adelante.
            SalarioBaseResponse.ComparativaSmi comparativa = null;
            List<Cita> citasAlcanza = r.citas();
            if ("EUR/mes".equals(r.unidad()) && r.importe().compareTo(smi.smiMensual(anio)) < 0) {
                comparativa = new SalarioBaseResponse.ComparativaSmi(
                        mensualidades,
                        r.importe().multiply(mensualidades).setScale(2, RoundingMode.HALF_UP),
                        smi.smiAnual(anio));
                citasAlcanza = new ArrayList<>(r.citas());
                citasAlcanza.add(smi.citaSmi(anio));
            }
            return new SalarioBaseResponse(r.importe(), r.unidad(), false, smi.smiMensual(anio), null,
                    comparativa, citasAlcanza);
        }
        List<Cita> citas = new ArrayList<>(r.citas());
        suelo.cita().ifPresent(citas::add);
        return new SalarioBaseResponse(r.importe(), r.unidad(), true, smi.smiMensual(anio),
                suelo.baseAplicada(), null, citas);
    }
}
