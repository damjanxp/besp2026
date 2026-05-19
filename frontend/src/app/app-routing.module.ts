﻿import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { ActivateAccountComponent } from './auth/activate-account/activate-account.component';
import { CertificateListComponent } from './certificates/certificate-list/certificate-list.component';
import { CertificateFormComponent } from './certificates/certificate-form/certificate-form.component';
import { PasswordListComponent } from './password-manager/password-list/password-list.component';
import { CsrUploadComponent } from './csr/csr-upload/csr-upload.component';
import { authGuard } from './shared/guards/auth.guard';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'activate', component: ActivateAccountComponent },
  { path: 'certificates/new', component: CertificateFormComponent, canActivate: [authGuard] },
  { path: 'certificates', component: CertificateListComponent, canActivate: [authGuard] },
  { path: 'password-manager', component: PasswordListComponent, canActivate: [authGuard] },
  { path: 'csr-upload', component: CsrUploadComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

