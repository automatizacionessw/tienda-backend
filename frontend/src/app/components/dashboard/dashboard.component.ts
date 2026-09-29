import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ClienteService } from '../../services/cliente.service';
import { UsuarioService } from '../../services/usuario.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">Panel de Administración de Ventas</h1>
          <p class="text-gray-500 text-sm mt-1">Gestión de Clientes, Dueños de Tienda y Orquestación MCP con Telegram</p>
        </div>
        <div class="flex items-center space-x-2">
          <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-800 border border-green-200">
            <span class="w-2 h-2 mr-1.5 bg-green-500 rounded-full animate-pulse"></span> Backend & DB Conectados
          </span>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <!-- Card Clientes -->
        <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Clientes Registrados</p>
              <h3 class="text-3xl font-extrabold text-indigo-600 mt-2">{{ totalClientes }}</h3>
            </div>
            <div class="p-3 bg-indigo-50 rounded-lg text-indigo-600 flex items-center justify-center" style="width: 52px; height: 52px;">
              <svg style="width: 32px; height: 32px;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"/>
              </svg>
            </div>
          </div>
          <div class="mt-4 pt-4 border-t border-gray-100">
            <a routerLink="/clientes" class="text-sm text-indigo-600 font-semibold hover:text-indigo-800 flex items-center gap-1">
              Administrar Clientes &rarr;
            </a>
          </div>
        </div>

        <!-- Card Usuarios / Dueños -->
        <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Dueños y Vendedores</p>
              <h3 class="text-3xl font-extrabold text-amber-600 mt-2">{{ totalUsuarios }}</h3>
            </div>
            <div class="p-3 bg-amber-50 rounded-lg text-amber-600 flex items-center justify-center" style="width: 52px; height: 52px;">
              <svg style="width: 32px; height: 32px;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
              </svg>
            </div>
          </div>
          <div class="mt-4 pt-4 border-t border-gray-100">
            <a routerLink="/usuarios" class="text-sm text-amber-600 font-semibold hover:text-amber-800 flex items-center gap-1">
              Gestionar Usuarios &rarr;
            </a>
          </div>
        </div>

        <!-- Card Backend Status -->
        <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Especificación API</p>
              <h3 class="text-2xl font-extrabold text-emerald-600 mt-2">Swagger UI</h3>
            </div>
            <div class="p-3 bg-emerald-50 rounded-lg text-emerald-600 flex items-center justify-center" style="width: 52px; height: 52px;">
              <svg style="width: 32px; height: 32px;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"/>
              </svg>
            </div>
          </div>
          <div class="mt-4 pt-4 border-t border-gray-100">
            <a href="http://localhost:8080/swagger-ui.html" target="_blank" class="text-sm text-emerald-600 font-semibold hover:text-emerald-800 flex items-center gap-1">
              Explorar Endpoints REST &rarr;
            </a>
          </div>
        </div>
      </div>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  totalClientes = 0;
  totalUsuarios = 0;

  constructor(
    private clienteService: ClienteService,
    private usuarioService: UsuarioService
  ) {}

  ngOnInit(): void {
    this.clienteService.listar().subscribe({
      next: (data) => (this.totalClientes = data.length),
      error: () => (this.totalClientes = 0)
    });

    this.usuarioService.listar().subscribe({
      next: (data) => (this.totalUsuarios = data.length),
      error: () => (this.totalUsuarios = 0)
    });
  }
}
