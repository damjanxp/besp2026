import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CsrInfo } from '../../core/models/csr.model';

@Injectable({
  providedIn: 'root'
})
export class CsrService {
  private readonly API_URL = '/api/csr';

  constructor(private http: HttpClient) {}

  previewCsr(file: File): Observable<CsrInfo> {
    const formData = new FormData();
    formData.append('csrFile', file);
    return this.http.post<CsrInfo>(`${this.API_URL}/preview`, formData);
  }

  uploadCsr(file: File, caId: number, validDays: number): Observable<any> {
    const formData = new FormData();
    formData.append('csrFile', file);
    formData.append('caId', caId.toString());
    formData.append('validDays', validDays.toString());
    return this.http.post(`${this.API_URL}/upload`, formData);
  }
}

