import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateRootCertificateRequest, CertificateResponse, IssueCertificateRequest, UserProfile } from '../../core/models/certificate.model';
@Injectable({
  providedIn: 'root'
})
export class CertificateService {
  private readonly API_URL = '/api/certificates';
  constructor(private http: HttpClient) {}
  createRootCertificate(request: CreateRootCertificateRequest): Observable<CertificateResponse> {
    return this.http.post<CertificateResponse>(this.API_URL + '/root', request);
  }
  getCertificates(): Observable<CertificateResponse[]> {
    return this.http.get<CertificateResponse[]>(this.API_URL);
  }

  revokeCertificate(id: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/${id}/revoke`, { reason });
  }

  getAvailableIssuers(): Observable<CertificateResponse[]> {
    return this.http.get<CertificateResponse[]>(`${this.API_URL}/issuers`);
  }

  issueCertificate(request: IssueCertificateRequest): Observable<CertificateResponse> {
    return this.http.post<CertificateResponse>(`${this.API_URL}/issue`, request);
  }

  getCaUsers(): Observable<UserProfile[]> {
    return new Observable(observer => {
      this.http.get<UserProfile[]>('/api/users').subscribe({
        next: (users) => {
          observer.next(users.filter(u => u.role === 'CA_USER'));
          observer.complete();
        },
        error: (err) => observer.error(err)
      });
    });
  }
}
