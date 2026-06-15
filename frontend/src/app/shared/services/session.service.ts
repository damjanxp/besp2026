import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserSession } from '../../core/models/session.model';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly API_URL = '/api/sessions';

  constructor(private http: HttpClient) {}

  getMySessions(): Observable<UserSession[]> {
    return this.http.get<UserSession[]>(this.API_URL);
  }

  revokeSession(jti: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${encodeURIComponent(jti)}`);
  }
}
