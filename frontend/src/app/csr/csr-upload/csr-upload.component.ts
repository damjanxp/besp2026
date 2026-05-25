import { Component, HostListener, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CsrService } from '../../shared/services/csr.service';
import { CertificateService } from '../../shared/services/certificate.service';
import { CsrInfo, CsrSignResponse } from '../../core/models/csr.model';
import { Certificate } from '../../core/models/certificate.model';

@Component({
  selector: 'app-csr-upload',
  templateUrl: './csr-upload.component.html',
  styleUrls: ['./csr-upload.component.scss']
})
export class CsrUploadComponent implements OnInit {
  isDragOver = false;
  isLoading = false;
  csrInfo: CsrInfo | null = null;
  signedCert: CsrSignResponse | null = null;
  selectedFile: File | null = null;
  errorMessage = '';

  caList: Certificate[] = [];
  uploadForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private csrService: CsrService,
    private certificateService: CertificateService,
    private snackBar: MatSnackBar
  ) {
    this.uploadForm = this.fb.group({
      caId: [null, Validators.required],
      validDays: [365, [Validators.required, Validators.min(1), Validators.max(3650)]]
    });
  }

  ngOnInit(): void {
    this.certificateService.getCertificates().subscribe({
      next: (certs) => {
        this.caList = certs.filter(
          c => (c.type === 'ROOT' || c.type === 'INTERMEDIATE') && c.status === 'ACTIVE'
        );
      },
      error: () => {}
    });
  }

  @HostListener('dragover', ['$event'])
  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  @HostListener('dragleave', ['$event'])
  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  @HostListener('drop', ['$event'])
  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.handleFile(files[0]);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFile(input.files[0]);
    }
  }

  handleFile(file: File): void {
    this.errorMessage = '';
    this.csrInfo = null;
    this.signedCert = null;
    this.selectedFile = file;

    const reader = new FileReader();
    reader.readAsText(file);
    reader.onload = () => {
      this.isLoading = true;
      this.csrService.previewCsr(file).subscribe({
        next: (info) => {
          this.isLoading = false;
          this.csrInfo = info;
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err.error?.message || 'Greška pri parsiranju CSR fajla.';
        }
      });
    };
    reader.onerror = () => {
      this.errorMessage = 'Greška pri čitanju fajla.';
    };
  }

  getSelectedCa(): Certificate | null {
    const caId = this.uploadForm.get('caId')?.value;
    return this.caList.find(c => c.id === caId) ?? null;
  }

  getMaxDaysHint(): string {
    const ca = this.getSelectedCa();
    if (!ca) return '';
    const validTo = new Date(ca.validTo);
    const today = new Date();
    const diffDays = Math.floor((validTo.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
    return `Maksimalno ${diffDays} dana (do isteka izabranog CA)`;
  }

  onSubmit(): void {
    if (!this.selectedFile || this.uploadForm.invalid) return;
    const { caId, validDays } = this.uploadForm.value;
    this.isLoading = true;
    this.csrService.uploadCsr(this.selectedFile, caId, validDays).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.signedCert = response;
        this.snackBar.open(response.message || 'CSR uspješno potpisano', 'Zatvori', { duration: 4000 });
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Greška pri slanju CSR zahtjeva.';
      }
    });
  }

  downloadCertificate(): void {
    if (!this.signedCert) return;
    const element = document.createElement('a');
    element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(this.signedCert.certificateData));
    element.setAttribute('download', `${this.signedCert.commonName}-cert.pem`);
    element.style.display = 'none';
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  }

  newRequest(): void {
    this.csrInfo = null;
    this.signedCert = null;
    this.selectedFile = null;
    this.errorMessage = '';
    this.uploadForm.reset({ validDays: 365 });
  }
}


