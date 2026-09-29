import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UsuarioService } from '../../../services/usuario.service';
import { Usuario, UsuarioRequest } from '../../../models/usuario.model';

@Component({
  selector: 'app-usuario-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <!-- Top Bar -->
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Gestión de Dueños y Vendedores</h1>
          <p class="text-sm text-gray-500 mt-0.5">Administración de roles, dueños de tienda y personal autorizado para notificaciones.</p>
        </div>
        <button (click)="abrirModalCrear()"
                class="inline-flex items-center px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white font-medium rounded-lg text-sm transition shadow-sm gap-2">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
          </svg>
          Nuevo Usuario / Dueño
        </button>
      </div>

      <!-- Table Container -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div class="p-4 border-b border-gray-100 bg-gray-50/50 flex items-center justify-between">
          <input type="text" [(ngModel)]="filtro" placeholder="Buscar por nombre, email, username o rol..."
                 class="w-full sm:w-80 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500 focus:border-amber-500 outline-none"/>
          <span class="text-xs text-gray-500 font-medium">Total: {{ usuariosFiltrados.length }} usuarios</span>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse">
            <thead>
              <tr class="bg-gray-100/70 text-gray-600 text-xs uppercase tracking-wider font-semibold">
                <th class="px-6 py-3">ID</th>
                <th class="px-6 py-3">Username</th>
                <th class="px-6 py-3">Nombre</th>
                <th class="px-6 py-3">Email</th>
                <th class="px-6 py-3">Teléfono</th>
                <th class="px-6 py-3">Rol</th>
                <th class="px-6 py-3">Estado</th>
                <th class="px-6 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 text-sm">
              <tr *ngFor="let usuario of usuariosFiltrados" class="hover:bg-amber-50/30 transition">
                <td class="px-6 py-4 font-semibold text-gray-700">#{{ usuario.id }}</td>
                <td class="px-6 py-4 font-mono text-xs text-amber-700 font-medium">
                  &#64;{{ usuario.username }}
                </td>
                <td class="px-6 py-4 font-medium text-gray-900">{{ usuario.nombre }}</td>
                <td class="px-6 py-4 text-gray-600 text-xs">{{ usuario.email }}</td>
                <td class="px-6 py-4 text-gray-500 text-xs">{{ usuario.telefono || '-' }}</td>
                <td class="px-6 py-4">
                  <span [ngClass]="{
                    'bg-purple-50 text-purple-700 border-purple-200': usuario.rol === 'DUEÑO',
                    'bg-blue-50 text-blue-700 border-blue-200': usuario.rol === 'VENDEDOR',
                    'bg-gray-50 text-gray-700 border-gray-200': usuario.rol !== 'DUEÑO' && usuario.rol !== 'VENDEDOR'
                  }" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border">
                    {{ usuario.rol }}
                  </span>
                </td>
                <td class="px-6 py-4">
                  <span *ngIf="usuario.activo" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                    Activo
                  </span>
                  <span *ngIf="!usuario.activo" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                    Inactivo
                  </span>
                </td>
                <td class="px-6 py-4 text-right space-x-2">
                  <button (click)="abrirModalEditar(usuario)" class="text-amber-600 hover:text-amber-900 font-medium text-xs">
                    Editar
                  </button>
                  <button (click)="eliminarUsuario(usuario.id)" class="text-red-600 hover:text-red-900 font-medium text-xs">
                    Eliminar
                  </button>
                </td>
              </tr>
              <tr *ngIf="usuariosFiltrados.length === 0">
                <td colspan="8" class="px-6 py-8 text-center text-gray-500">
                  No se encontraron usuarios registrados.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Modal Form (Crear/Editar) -->
    <div *ngIf="mostrarModal" class="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
      <div class="bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4 animate-fade-in">
        <div class="flex justify-between items-center border-b pb-3">
          <h3 class="text-lg font-bold text-gray-800">
            {{ editando ? 'Editar Usuario/Dueño' : 'Nuevo Usuario/Dueño' }}
          </h3>
          <button (click)="cerrarModal()" class="text-gray-400 hover:text-gray-600">&times;</button>
        </div>

        <form (ngSubmit)="guardarUsuario()" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-gray-700 uppercase">Username *</label>
            <input type="text" [(ngModel)]="formData.username" name="username" required
                   class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500 outline-none"
                   placeholder="Ej. admin_dueno"/>
          </div>

          <div>
            <label class="block text-xs font-semibold text-gray-700 uppercase">Nombre Completo *</label>
            <input type="text" [(ngModel)]="formData.nombre" name="nombre" required
                   class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500 outline-none"
                   placeholder="Carlos Morales"/>
          </div>

          <div>
            <label class="block text-xs font-semibold text-gray-700 uppercase">Email *</label>
            <input type="email" [(ngModel)]="formData.email" name="email" required
                   class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500 outline-none"
                   placeholder="carlos@tienda.com"/>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase">Teléfono</label>
              <input type="text" [(ngModel)]="formData.telefono" name="telefono"
                     class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500 outline-none"
                     placeholder="+59177123456"/>
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase">Rol *</label>
              <select [(ngModel)]="formData.rol" name="rol" required
                      class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500 outline-none">
                <option value="DUEÑO">DUEÑO</option>
                <option value="VENDEDOR">VENDEDOR</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>
          </div>

          <div *ngIf="errorMessage" class="text-red-600 text-xs bg-red-50 p-2 rounded border border-red-200">
            {{ errorMessage }}
          </div>

          <div class="flex justify-end space-x-2 pt-2 border-t">
            <button type="button" (click)="cerrarModal()" class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" class="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-lg text-sm font-medium shadow-xs">
              {{ editando ? 'Actualizar' : 'Guardar' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class UsuarioListComponent implements OnInit {
  usuarios: Usuario[] = [];
  filtro = '';

  mostrarModal = false;
  editando = false;
  usuarioIdEdicion?: number;

  formData: UsuarioRequest = {
    username: '',
    nombre: '',
    email: '',
    telefono: '',
    rol: 'DUEÑO'
  };

  errorMessage = '';

  constructor(private usuarioService: UsuarioService) {}

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.usuarioService.listar().subscribe({
      next: (data) => (this.usuarios = data),
      error: (err) => console.error('Error al cargar usuarios:', err)
    });
  }

  get usuariosFiltrados(): Usuario[] {
    if (!this.filtro.trim()) return this.usuarios;
    const term = this.filtro.toLowerCase();
    return this.usuarios.filter(
      (u) =>
        u.nombre.toLowerCase().includes(term) ||
        u.username.toLowerCase().includes(term) ||
        u.email.toLowerCase().includes(term) ||
        u.rol.toLowerCase().includes(term)
    );
  }

  abrirModalCrear(): void {
    this.editando = false;
    this.usuarioIdEdicion = undefined;
    this.formData = { username: '', nombre: '', email: '', telefono: '', rol: 'DUEÑO' };
    this.errorMessage = '';
    this.mostrarModal = true;
  }

  abrirModalEditar(usuario: Usuario): void {
    this.editando = true;
    this.usuarioIdEdicion = usuario.id;
    this.formData = {
      username: usuario.username,
      nombre: usuario.nombre,
      email: usuario.email,
      telefono: usuario.telefono || '',
      rol: usuario.rol
    };
    this.errorMessage = '';
    this.mostrarModal = true;
  }

  cerrarModal(): void {
    this.mostrarModal = false;
  }

  guardarUsuario(): void {
    if (!this.formData.username.trim() || !this.formData.nombre.trim() || !this.formData.email.trim()) {
      this.errorMessage = 'Username, Nombre y Email son campos obligatorios';
      return;
    }

    if (this.editando && this.usuarioIdEdicion) {
      this.usuarioService.actualizar(this.usuarioIdEdicion, this.formData).subscribe({
        next: () => {
          this.cargarUsuarios();
          this.cerrarModal();
        },
        error: (err) => (this.errorMessage = err.error?.mensaje || err.error?.error || 'Error al actualizar usuario')
      });
    } else {
      this.usuarioService.crear(this.formData).subscribe({
        next: () => {
          this.cargarUsuarios();
          this.cerrarModal();
        },
        error: (err) => (this.errorMessage = err.error?.mensaje || err.error?.error || 'Error al crear usuario')
      });
    }
  }

  eliminarUsuario(id: number): void {
    if (confirm('¿Está seguro de eliminar este usuario?')) {
      this.usuarioService.eliminar(id).subscribe({
        next: () => this.cargarUsuarios(),
        error: (err) => alert('Error al eliminar usuario: ' + (err.error?.mensaje || err.message))
      });
    }
  }
}
