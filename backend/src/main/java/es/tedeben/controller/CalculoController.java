package es.tedeben.controller;

import es.tedeben.domain.convenio.Convenio;
import es.tedeben.dto.CalculoHorasExtraRequest;
import es.tedeben.dto.CalculoHorasExtraResponse;
import es.tedeben.dto.SalarioBaseRequest;
import es.tedeben.dto.SalarioBaseResponse;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.service.CalculoConvenioService;
import es.tedeben.service.HorasExtraCalculadas;
import es.tedeben.service.TablaSalarialService;
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

    public CalculoController(ConvenioCatalog convenios, CalculoConvenioService calculo,
                             TablaSalarialService tablas) {
        this.convenios = convenios;
        this.calculo = calculo;
        this.tablas = tablas;
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

        return new CalculoHorasExtraResponse(resultado.precioHora(), resultado.importe(), resultado.citas());
    }

    @PostMapping("/salario-base")
    public SalarioBaseResponse salarioBase(@Valid @RequestBody SalarioBaseRequest peticion) {
        return tablas.salarioBaseMinimo(peticion.convenioId(), peticion.dimensiones(), peticion.fecha())
                .map(r -> new SalarioBaseResponse(r.importe(), r.unidad(), r.citas()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Sin tabla salarial aplicable para esas dimensiones y fecha en "
                                + peticion.convenioId()));
    }
}
