/**
 * Perfil laboral persistido del usuario autenticado (PerfilRequest/PerfilResponse
 * del backend). El servidor resuelve el convenio desde provincia+subsector y
 * saca el id de usuario del JWT — aquí nunca se manda ninguno de los dos.
 *
 * OJO: el PUT es full-replace. Cualquier campo omitido se vacía en el servidor,
 * así que SIEMPRE se manda el objeto completo (ver stores/cuenta.ts).
 */
import { api } from './api'

export interface PerfilGuardado {
  provincia: string
  subsector: string
  /** Resuelto por el servidor; solo lectura en el cliente. */
  convenioId: string
  puestoId: string | null
  dimensiones: Record<string, string> | null
  salarioBaseMensual: number | null
  plusesAnuales: number | null
}

/** Cuerpo COMPLETO del PUT (full-replace): sin campos opcionales a nivel de tipo. */
export interface PerfilPeticion {
  provincia: string
  subsector: string
  puestoId: string | null
  dimensiones: Record<string, string> | null
  salarioBaseMensual: number | null
  plusesAnuales: number | null
}

export const getPerfilUsuario = () => api.get<PerfilGuardado>('/perfil')

export const putPerfilUsuario = (perfil: PerfilPeticion) =>
  api.put<PerfilGuardado>('/perfil', perfil)
