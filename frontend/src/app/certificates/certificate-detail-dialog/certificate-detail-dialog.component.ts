import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateDetails } from '../../core/models/certificate.model';
import { CertificateService } from '../../shared/services/certificate.service';

interface CertificateDetailDialogData {
  certificateId: number;
}

@Component({
  selector: 'app-certificate-detail-dialog',
  templateUrl: './certificate-detail-dialog.component.html',
  styleUrls: ['./certificate-detail-dialog.component.scss']
})
export class CertificateDetailDialogComponent implements OnInit {
  details?: CertificateDetails;
  isLoading = true;
  errorMessage = '';

  constructor(
    private dialogRef: MatDialogRef<CertificateDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: CertificateDetailDialogData,
    private certificateService: CertificateService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.certificateService.getById(this.data.certificateId).subscribe({
      next: (details) => {
        this.details = details;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Ne mogu da ucitam detalje sertifikata.';
        this.isLoading = false;
      }
    });
  }

  close(): void {
    this.dialogRef.close();
  }

  download(format: 'PEM' | 'DER'): void {
    if (!this.details) {
      return;
    }
    this.certificateService.downloadCertificate(this.details.id, format).subscribe({
      error: () => {
        this.snackBar.open('Greska pri preuzimanju sertifikata.', 'OK', { duration: 3000 });
      }
    });
  }

  formatKeyUsage(values?: string[]): string {
    if (!values || values.length === 0) {
      return '-';
    }
    return values.join(', ');
  }

  formatIssuer(details: CertificateDetails): string {
    return details.issuerCommonName ?? '-';
  }

  formatBoolean(value?: boolean): string {
    if (value === undefined || value === null) {
      return '-';
    }
    return value ? 'Da' : 'Ne';
  }
}

