import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import {
  Certificate,
  CertificateDetails,
  CreateRootCertificateRequest
} from '../../core/models/certificate.model';

@Injectable({
  providedIn: 'root'
})
export class CertificateService {
  private readonly API_URL = '/api/certificates';

  constructor(private http: HttpClient) {}

  createRootCertificate(request: CreateRootCertificateRequest): Observable<Certificate> {
    return this.http.post<Certificate>(this.API_URL + '/root', request);
  }

  getAll(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(this.API_URL);
  }

  getById(id: number): Observable<CertificateDetails> {
    return this.http.get<CertificateDetails>(`${this.API_URL}/${id}`);
  }

  downloadCertificate(id: number, format: 'PEM' | 'DER'): Observable<void> {
    return this.http.get(`${this.API_URL}/${id}/download`, {
      params: { format },
      observe: 'response',
      responseType: 'blob'
    }).pipe(
      tap(response => this.triggerDownload(response, format, id)),
      map(() => undefined)
    );
  }

  private triggerDownload(response: HttpResponse<Blob>, format: 'PEM' | 'DER', id: number): void {
    const filename = this.resolveFilename(response, format, id);
    const blob = response.body ?? new Blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private resolveFilename(response: HttpResponse<Blob>, format: 'PEM' | 'DER', id: number): string {
    const disposition = response.headers.get('content-disposition');
    if (disposition) {
      const match = /filename=([^;]+)/i.exec(disposition);
      if (match?.[1]) {
        return match[1].replace(/"/g, '').trim();
      }
    }
    return `cert_${id}.${format.toLowerCase()}`;
  }

  // Legacy alias
  getCertificates(): Observable<Certificate[]> {
    return this.getAll();
  }

  getAvailableCas(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.API_URL}/available-cas`);
  }
}
