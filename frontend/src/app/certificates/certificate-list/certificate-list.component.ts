import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatSnackBar } from '@angular/material/snack-bar';
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
  isCaUser = false;
  selectedCert: CertificateResponse | null = null;

  revocationReasons = [
    'UNSPECIFIED', 'KEY_COMPROMISE', 'CA_COMPROMISE', 'AFFILIATION_CHANGED',
    'SUPERSEDED', 'CESSATION_OF_OPERATION', 'CERTIFICATE_HOLD',
    'PRIVILEGE_WITHDRAWN', 'AA_COMPROMISE'
  ];
  revokeTarget: CertificateResponse | null = null;
  selectedRevocationReason = 'UNSPECIFIED';
  isRevoking = false;

  constructor(
    private certificateService: CertificateService,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}
  ngOnInit(): void {
    this.isAdmin = this.authService.getRole() === 'ADMIN';
    this.isCaUser = this.authService.getRole() === 'CA_USER';
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
  openRevoke(cert: CertificateResponse): void {
    this.revokeTarget = cert;
    this.selectedRevocationReason = 'UNSPECIFIED';
  }

  cancelRevoke(): void {
    this.revokeTarget = null;
  }

  submitRevoke(): void {
    if (!this.revokeTarget) return;
    this.isRevoking = true;
    this.certificateService.revokeCertificate(this.revokeTarget.id, this.selectedRevocationReason)
      .subscribe({
        next: () => {
          this.isRevoking = false;
          this.revokeTarget = null;
          this.snackBar.open('Sertifikat je povučen.', 'OK', { duration: 3000 });
          this.ngOnInit();
        },
        error: (err) => {
          this.isRevoking = false;
          this.snackBar.open(err.error?.message || 'Greška pri povlačenju.', 'OK', { duration: 4000 });
        }
      });
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
