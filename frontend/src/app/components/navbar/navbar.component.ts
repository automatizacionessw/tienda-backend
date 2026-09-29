import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="bg-indigo-900 text-white shadow-md">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <div class="flex items-center space-x-3">
            <div class="bg-indigo-600 p-2 rounded-lg flex items-center justify-center" style="width: 40px; height: 40px;">
              <svg class="text-white" style="width: 24px; height: 24px;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
              </svg>
            </div>
            <div>
              <span class="font-bold text-xl tracking-tight text-white">Tienda Online</span>
              <span class="ml-2 text-xs bg-indigo-700 text-indigo-200 px-2 py-0.5 rounded-full font-medium">Admin Portal</span>
            </div>
          </div>
          <nav class="flex items-center space-x-2">
            <a routerLink="/" routerLinkActive="bg-indigo-800 text-white" [routerLinkActiveOptions]="{exact: true}"
               class="px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-700 hover:text-white transition">
               📊 Dashboard
            </a>
            <a routerLink="/clientes" routerLinkActive="bg-indigo-800 text-white"
               class="px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-700 hover:text-white transition">
               👥 Clientes
            </a>
            <a routerLink="/usuarios" routerLinkActive="bg-indigo-800 text-white"
               class="px-3 py-2 rounded-md text-sm font-medium text-indigo-100 hover:bg-indigo-700 hover:text-white transition">
               👑 Dueños / Usuarios
            </a>
            <a href="http://localhost:8080/swagger-ui.html" target="_blank"
               class="px-3 py-2 rounded-md text-sm font-medium bg-emerald-600 hover:bg-emerald-700 text-white transition flex items-center gap-1 shadow-xs">
               <span>🚀 Swagger UI</span>
            </a>
          </nav>
        </div>
      </div>
    </header>
  `
})
export class NavbarComponent {}
