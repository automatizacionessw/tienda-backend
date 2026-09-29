export interface Cliente {
  id: number;
  telegramUserId: number;
  username?: string;
  nombre: string;
  apellido?: string;
  idioma?: string;
  fechaAlta: string;
}

export interface ClienteRequest {
  telegramUserId: number;
  username?: string;
  nombre: string;
  apellido?: string;
  idioma?: string;
}
