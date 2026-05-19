import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { CertificateService } from '../../shared/services/certificate.service';
import { AuthService } from '../../shared/services/auth.service';
import { CertificateResponse } from '../../core/models/certificate.model';
@Component({
  selector: 'app-certificate-list',
  templateUrl: './certificate-list.component.html',
  styleUrls: ['./certificate-list.component.scss']
})
export class CertificateListComponent implements OnInit {
  displayedColumns = ['serialNumber', 'type', 'commonName', 'organization', 'validFrom', 'validTo', 'status', 'actions'];
  dataSource = new MatTableDataSource<CertificateResponse>();
  isLoading = true;
  isAdmin = false;
  selectedCert: CertificateResponse | null = null;
  constructor(
    private certificateService: CertificateService,
    private authService: AuthService,
    private router: Router
  ) {}
  ngOnInit(): void {
    this.isAdmin = this.authService.getRole() === 'ADMIN';
    this.certificateService.getCertificates().subscribe({
      next: (certs) => {
        this.dataSource.data = certs;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }
  goToCreate(): void {
    this.router.navigate(['/certificates/new']);
  }
  getTypeColor(type: string): string {
    switch (type) {
      case 'ROOT': return 'purple';
      case 'INTERMEDIATE': return 'blue';
      case 'END_ENTITY': return 'green';
      default: return '';
    }
  }
  getStatusColor(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'green';
      case 'REVOKED': return 'red';
      case 'EXPIRED': return 'grey';
      default: return '';
    }
  }
  showDetails(cert: CertificateResponse): void {
    this.selectedCert = this.selectedCert?.id === cert.id ? null : cert;
  }
  downloadPem(cert: CertificateResponse): void {
    const blob = new Blob([cert.certificateData], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${cert.commonName.replace(/\s+/g, '_')}_${cert.type}.pem`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
