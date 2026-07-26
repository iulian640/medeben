import { describe, expect, it } from 'vitest'
import {
  TEXTO_CONSENTIMIENTO_UBICACION_V1_0,
  VERSION_TEXTO_CONSENTIMIENTO_UBICACION,
} from './textoConsentimientoUbicacion'

// Copia independiente del texto CANÓNICO (decisiones-implementacion.md), tal
// cual debe reproducirlo también el backend (ConsentimientoUbicacionTexto.java)
// y docs/legal/consentimiento-ubicacion-v1.0.md. Ancla la cadena COMPLETA, no
// palabras sueltas: el hallazgo HIGH del review era justo que un test de
// keywords no detecta una paráfrasis — el usuario debe leer, carácter a
// carácter, el mismo texto que el servidor sella con SHA-256.
const TEXTO_ESPERADO = `Anotar dónde fichas — consentimiento (v1.0)

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
  el art. 90 de la LOPDGDD.`

describe('texto de consentimiento de ubicación v1.0', () => {
  it('la versión es "1.0"', () => {
    expect(VERSION_TEXTO_CONSENTIMIENTO_UBICACION).toBe('1.0')
  })

  it('el texto es literal, carácter a carácter, al del contrato (lo que el servidor hashea)', () => {
    expect(TEXTO_CONSENTIMIENTO_UBICACION_V1_0).toBe(TEXTO_ESPERADO)
  })
})
