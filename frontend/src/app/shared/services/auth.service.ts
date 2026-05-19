﻿import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoginRequest, RegisterRequest, AuthResponse } from '../../core/models/auth.model';
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = '/api/auth';
  private readonly TOKEN_KEY = 'auth_token';
  constructor(private http: HttpClient) {}
  login(email: string, password: string, captchaToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(this.API_URL + '/login', { email, password, captchaToken });
  }
  register(request: RegisterRequest): Observable<any> {
    return this.http.post(this.API_URL + '/register', request);
  }
  activateAccount(token: string): Observable<any> {
    return this.http.get(this.API_URL + '/activate', { params: { token } });
  }
  saveToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }
  getRole(): string | null {
    const payload = this.decodePayload();
    return payload ? payload.role : null;
  }
  getEmail(): string | null {
    const payload = this.decodePayload();
    return payload ? (payload.email || payload.sub) : null;
  }
  isLoggedIn(): boolean {
    const payload = this.decodePayload();
    if (!payload || !payload.exp) {
      return false;
    }
    return new Date(payload.exp * 1000) > new Date();
  }
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }
  private decodePayload(): any {
    const token = this.getToken();
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const decoded = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }
}
