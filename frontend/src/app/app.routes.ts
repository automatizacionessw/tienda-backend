import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { ClienteListComponent } from './components/clientes/cliente-list/cliente-list.component';
import { UsuarioListComponent } from './components/usuarios/usuario-list/usuario-list.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'clientes', component: ClienteListComponent },
  { path: 'usuarios', component: UsuarioListComponent },
  { path: '**', redirectTo: '' }
];
