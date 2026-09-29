export interface Usuario {
  id: number;
  username: string;
  nombre: string;
  email: string;
  telefono?: string;
  rol: string;
  activo: boolean;
  fechaCreacion: string;
}

export interface UsuarioRequest {
  username: string;
  nombre: string;
  email: string;
  telefono?: string;
  rol: string;
}
