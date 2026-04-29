import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateRootCertificateRequest, CertificateResponse } from '../../core/models/certificate.model';
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
}
