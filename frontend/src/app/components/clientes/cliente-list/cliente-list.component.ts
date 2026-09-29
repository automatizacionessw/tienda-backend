import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClienteService } from '../../../services/cliente.service';
import { Cliente, ClienteRequest } from '../../../models/cliente.model';

@Component({
  selector: 'app-cliente-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <!-- Top Bar -->
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Gestión de Clientes</h1>
          <p class="text-sm text-gray-500 mt-0.5">Listado y administración de clientes registrados vía Telegram o manualmente.</p>
        </div>
        <button (click)="abrirModalCrear()"
                class="inline-flex items-center px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-medium rounded-lg text-sm transition shadow-sm gap-2">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
          </svg>
          Nuevo Cliente
        </button>
      </div>

      <!-- Table Container -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div class="p-4 border-b border-gray-100 bg-gray-50/50 flex items-center justify-between">
          <input type="text" [(ngModel)]="filtro" placeholder="Buscar cliente por nombre o username..."
                 class="w-full sm:w-80 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none"/>
          <span class="text-xs text-gray-500 font-medium">Total: {{ clientesFiltrados.length }} clientes</span>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse">
            <thead>
              <tr class="bg-gray-100/70 text-gray-600 text-xs uppercase tracking-wider font-semibold">
                <th class="px-6 py-3">ID</th>
                <th class="px-6 py-3">Telegram ID</th>
                <th class="px-6 py-3">Username</th>
                <th class="px-6 py-3">Nombre Completo</th>
                <th class="px-6 py-3">Idioma</th>
                <th class="px-6 py-3">Fecha Alta</th>
                <th class="px-6 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 text-sm">
              <tr *ngFor="let cliente of clientesFiltrados" class="hover:bg-indigo-50/30 transition">
                <td class="px-6 py-4 font-semibold text-gray-700">#{{ cliente.id }}</td>
                <td class="px-6 py-4">
                  <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">
                    {{ cliente.telegramUserId }}
                  </span>
                </td>
                <td class="px-6 py-4 text-gray-600 font-mono text-xs">
                  {{ cliente.username ? '@' + cliente.username : '-' }}
                </td>
                <td class="px-6 py-4 font-medium text-gray-900">
                  {{ cliente.nombre }} {{ cliente.apellido || '' }}
                </td>
                <td class="px-6 py-4 text-gray-500 uppercase text-xs">{{ cliente.idioma || 'es' }}</td>
                <td class="px-6 py-4 text-gray-500 text-xs">{{ cliente.fechaAlta | date:'medium' }}</td>
                <td class="px-6 py-4 text-right space-x-2">
                  <button (click)="abrirModalEditar(cliente)" class="text-indigo-600 hover:text-indigo-900 font-medium text-xs">
                    Editar
                  </button>
                  <button (click)="eliminarCliente(cliente.id)" class="text-red-600 hover:text-red-900 font-medium text-xs">
                    Eliminar
                  </button>
                </td>
              </tr>
              <tr *ngIf="clientesFiltrados.length === 0">
                <td colspan="7" class="px-6 py-8 text-center text-gray-500">
                  No se encontraron clientes registrados.
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
            {{ editando ? 'Editar Cliente' : 'Nuevo Cliente' }}
          </h3>
          <button (click)="cerrarModal()" class="text-gray-400 hover:text-gray-600">&times;</button>
        </div>

        <form (ngSubmit)="guardarCliente()" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-gray-700 uppercase">Telegram User ID *</label>
            <input type="number" [(ngModel)]="formData.telegramUserId" name="telegramUserId" required
                   class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                   placeholder="Ej. 987654321"/>
          </div>

          <div>
            <label class="block text-xs font-semibold text-gray-700 uppercase">Username (Telegram)</label>
            <input type="text" [(ngModel)]="formData.username" name="username"
                   class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                   placeholder="Ej. juan_perez"/>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase">Nombre *</label>
              <input type="text" [(ngModel)]="formData.nombre" name="nombre" required
                     class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                     placeholder="Juan"/>
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 uppercase">Apellido</label>
              <input type="text" [(ngModel)]="formData.apellido" name="apellido"
                     class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                     placeholder="Pérez"/>
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-gray-700 uppercase">Idioma</label>
            <input type="text" [(ngModel)]="formData.idioma" name="idioma"
                   class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                   placeholder="es"/>
          </div>

          <div *ngIf="errorMessage" class="text-red-600 text-xs bg-red-50 p-2 rounded border border-red-200">
            {{ errorMessage }}
          </div>

          <div class="flex justify-end space-x-2 pt-2 border-t">
            <button type="button" (click)="cerrarModal()" class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium shadow-xs">
              {{ editando ? 'Actualizar' : 'Guardar' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class ClienteListComponent implements OnInit {
  clientes: Cliente[] = [];
  filtro = '';

  mostrarModal = false;
  editando = false;
  clienteIdEdicion?: number;

  formData: ClienteRequest = {
    telegramUserId: 0,
    username: '',
    nombre: '',
    apellido: '',
    idioma: 'es'
  };

  errorMessage = '';

  constructor(private clienteService: ClienteService) {}

  ngOnInit(): void {
    this.cargarClientes();
  }

  cargarClientes(): void {
    this.clienteService.listar().subscribe({
      next: (data) => (this.clientes = data),
      error: (err) => console.error('Error al cargar clientes:', err)
    });
  }

  get clientesFiltrados(): Cliente[] {
    if (!this.filtro.trim()) return this.clientes;
    const term = this.filtro.toLowerCase();
    return this.clientes.filter(
      (c) =>
        c.nombre.toLowerCase().includes(term) ||
        (c.username && c.username.toLowerCase().includes(term)) ||
        c.telegramUserId.toString().includes(term)
    );
  }

  abrirModalCrear(): void {
    this.editando = false;
    this.clienteIdEdicion = undefined;
    this.formData = { telegramUserId: 0, username: '', nombre: '', apellido: '', idioma: 'es' };
    this.errorMessage = '';
    this.mostrarModal = true;
  }

  abrirModalEditar(cliente: Cliente): void {
    this.editando = true;
    this.clienteIdEdicion = cliente.id;
    this.formData = {
      telegramUserId: cliente.telegramUserId,
      username: cliente.username || '',
      nombre: cliente.nombre,
      apellido: cliente.apellido || '',
      idioma: cliente.idioma || 'es'
    };
    this.errorMessage = '';
    this.mostrarModal = true;
  }

  cerrarModal(): void {
    this.mostrarModal = false;
  }

  guardarCliente(): void {
    if (!this.formData.telegramUserId || !this.formData.nombre.trim()) {
      this.errorMessage = 'El ID de Telegram y el Nombre son requeridos';
      return;
    }

    if (this.editando && this.clienteIdEdicion) {
      this.clienteService.actualizar(this.clienteIdEdicion, this.formData).subscribe({
        next: () => {
          this.cargarClientes();
          this.cerrarModal();
        },
        error: (err) => (this.errorMessage = err.error?.mensaje || 'Error al actualizar cliente')
      });
    } else {
      this.clienteService.crear(this.formData).subscribe({
        next: () => {
          this.cargarClientes();
          this.cerrarModal();
        },
        error: (err) => (this.errorMessage = err.error?.mensaje || 'Error al crear cliente')
      });
    }
  }

  eliminarCliente(id: number): void {
    if (confirm('¿Está seguro de eliminar este cliente?')) {
      this.clienteService.eliminar(id).subscribe({
        next: () => this.cargarClientes(),
        error: (err) => alert('Error al eliminar cliente: ' + (err.error?.mensaje || err.message))
      });
    }
  }
}
