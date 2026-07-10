package es.medeben.controller;

import es.medeben.domain.convenio.Convenio;
import es.medeben.dto.CalculoHorasExtraRequest;
import es.medeben.dto.CalculoHorasExtraResponse;
import es.medeben.dto.DesgloseValorHora;
import es.medeben.dto.SalarioBaseRequest;
import es.medeben.dto.SalarioBaseResponse;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.service.CalculoConvenioService;
import es.medeben.service.HorasExtraCalculadas;
import es.medeben.service.SmiService;
import es.medeben.service.TablaSalarialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

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

        HorasExtraCalculadas resultado = calculo.importeHorasExtra(
                        convenio, Year.of(peticion.anio()),
                        peticion.salarioBaseMensual(), peticion.plusesONada(), peticion.horas())
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
        return conAvisoSmi(resuelto, peticion.convenioId(), peticion.fecha().getYear());
    }

    /**
     * Añade el suelo del SMI: si la tabla (EUR/mes) queda por debajo del SMI en
     * cómputo anual (base × mensualidades del convenio), marca {@code bajoSmi} y
     * suma la cita legal del SMI. Sin mensualidades publicadas se asume 14 (el
     * estándar) para no dejar de avisar. Otras unidades (Cuenca EUR/año) no se
     * comparan aquí: el motor las trata aparte.
     */
    private SalarioBaseResponse conAvisoSmi(es.medeben.service.SalarioBaseResuelto r,
                                            String convenioId, int anio) {
        if (!"EUR/mes".equals(r.unidad())) {
            return new SalarioBaseResponse(r.importe(), r.unidad(), false, null, r.citas());
        }
        java.math.BigDecimal mensualidades = convenios.porId(convenioId)
                .flatMap(calculo::mensualidades)
                .orElse(java.math.BigDecimal.valueOf(14));
        boolean alcanza = smi.alcanzaElSmi(r.importe(), mensualidades, java.math.BigDecimal.ZERO, anio);
        if (alcanza) {
            return new SalarioBaseResponse(r.importe(), r.unidad(), false, smi.smiMensual(anio), r.citas());
        }
        var citas = new java.util.ArrayList<>(r.citas());
        citas.add(smi.citaSmi(anio));
        return new SalarioBaseResponse(r.importe(), r.unidad(), true, smi.smiMensual(anio), citas);
    }
}
