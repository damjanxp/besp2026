import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TemplateRequest, TemplateResponse } from '../../core/models/template.model';

@Injectable({ providedIn: 'root' })
export class TemplateService {
  private readonly API_URL = '/api/templates';

  constructor(private http: HttpClient) {}

  getMyTemplates(): Observable<TemplateResponse[]> {
    return this.http.get<TemplateResponse[]>(this.API_URL);
  }

  getTemplatesForCa(caId: number): Observable<TemplateResponse[]> {
    return this.http.get<TemplateResponse[]>(`${this.API_URL}/by-ca/${caId}`);
  }

  createTemplate(request: TemplateRequest): Observable<TemplateResponse> {
    return this.http.post<TemplateResponse>(this.API_URL, request);
  }

  updateTemplate(id: number, request: TemplateRequest): Observable<TemplateResponse> {
    return this.http.put<TemplateResponse>(`${this.API_URL}/${id}`, request);
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
