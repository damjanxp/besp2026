import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PasswordEntry, SavePasswordRequest } from '../../core/models/password-manager.model';

export interface UserPublicProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  publicKeySpki: string | null;
}

@Injectable({ providedIn: 'root' })
export class PasswordManagerService {
  private readonly API_URL = '/api/password-manager';
  private readonly USERS_URL = '/api/users';

  constructor(private http: HttpClient) {}

  getMyPasswords(): Observable<PasswordEntry[]> {
    return this.http.get<PasswordEntry[]>(this.API_URL);
  }

  savePassword(request: SavePasswordRequest): Observable<PasswordEntry> {
    return this.http.post<PasswordEntry>(this.API_URL, request);
  }

  deletePassword(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  /** Save the user's RSA public key (SPKI PEM) to the server. */
  saveMyPublicKey(publicKeySpki: string): Observable<void> {
    return this.http.put<void>(`${this.USERS_URL}/me/public-key`, { publicKeySpki });
  }

  /** Get current user's stored public key profile. */
  getMyPublicKey(): Observable<UserPublicProfile> {
    return this.http.get<UserPublicProfile>(`${this.USERS_URL}/me/public-key`);
  }

  /** List all users with their public keys (for sharing). */
  listUsers(): Observable<UserPublicProfile[]> {
    return this.http.get<UserPublicProfile[]>(this.USERS_URL);
  }
}

