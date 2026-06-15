import { Component, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateService } from '../../shared/services/certificate.service';
import { AuthService } from '../../shared/services/auth.service';
import { Certificate } from '../../core/models/certificate.model';
import { CertificateDetailDialogComponent } from '../certificate-detail-dialog/certificate-detail-dialog.component';

@Component({
  selector: 'app-certificate-list',
  templateUrl: './certificate-list.component.html',
  styleUrls: ['./certificate-list.component.scss']
})
export class CertificateListComponent implements OnInit, AfterViewInit {
  displayedColumns = ['serialNumber', 'type', 'commonName', 'validFrom', 'validTo', 'status', 'actions'];
  dataSource = new MatTableDataSource<Certificate>();
  isLoading = true;
  isAdmin = false;
  isCaUser = false;
  selectedCert: Certificate | null = null;

  revocationReasons = [
    'UNSPECIFIED', 'KEY_COMPROMISE', 'CA_COMPROMISE', 'AFFILIATION_CHANGED',
    'SUPERSEDED', 'CESSATION_OF_OPERATION', 'CERTIFICATE_HOLD',
    'PRIVILEGE_WITHDRAWN', 'AA_COMPROMISE'
  ];
  revokeTarget: Certificate | null = null;
  selectedRevocationReason = 'UNSPECIFIED';
  isRevoking = false;

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  constructor(
    private certificateService: CertificateService,
    private authService: AuthService,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.authService.getRole() === 'ADMIN';
    this.isCaUser = this.authService.getRole() === 'CA_USER';
    this.certificateService.getCertificates().subscribe({
      next: (certs) => {
        this.dataSource.data = certs;
        this.isLoading = false;
        this.applyTableState();
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  ngAfterViewInit(): void {
    this.applyTableState();
  }

  goToCreate(): void {
    this.router.navigate(['/certificates/new']);
  }

  formatSerial(serialNumber: string): string {
    if (!serialNumber) {
      return '';
    }
    return serialNumber.length > 12 ? `${serialNumber.slice(0, 12)}...` : serialNumber;
  }

  getTypeColor(type: string): string {
    switch (type) {
      case 'ROOT':
        return 'type-root';
      case 'INTERMEDIATE':
        return 'type-intermediate';
      case 'END_ENTITY':
        return 'type-end-entity';
      default:
        return '';
    }
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'status-active';
      case 'REVOKED':
        return 'status-revoked';
      case 'EXPIRED':
        return 'status-expired';
      default:
        return '';
    }
  }

  openDetails(cert: Certificate): void {
    this.dialog.open(CertificateDetailDialogComponent, {
      width: '720px',
      data: { certificateId: cert.id }
    });
  }

  showDetails(cert: Certificate): void {
    this.selectedCert = this.selectedCert?.id === cert.id ? null : cert;
  }

  openRevoke(cert: Certificate): void {
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

  downloadCertificate(cert: Certificate, format: 'PEM' | 'DER'): void {
    this.certificateService.downloadCertificate(cert.id, format).subscribe({
      error: () => {
        this.snackBar.open('Greška pri preuzimanju sertifikata.', 'OK', { duration: 3000 });
      }
    });
  }

  downloadPem(cert: Certificate): void {
    this.downloadCertificate(cert, 'PEM');
  }

  private applyTableState(): void {
    if (this.dataSource && this.paginator) {
      this.dataSource.paginator = this.paginator;
    }
    if (this.dataSource && this.sort) {
      this.dataSource.sort = this.sort;
    }
  }
}
