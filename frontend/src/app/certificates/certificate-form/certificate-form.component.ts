import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateService } from '../../shared/services/certificate.service';
import { AuthService } from '../../shared/services/auth.service';
@Component({
  selector: 'app-certificate-form',
  templateUrl: './certificate-form.component.html',
  styleUrls: ['./certificate-form.component.scss']
})
export class CertificateFormComponent implements OnInit {
  certForm!: FormGroup;
  isLoading = false;
  errorMessage = '';
  expiryDate: Date | null = null;
  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}
  ngOnInit(): void {
    if (this.authService.getRole() !== 'ADMIN') {
      this.router.navigate(['/certificates']);
      return;
    }
    this.certForm = this.fb.group({
      commonName: ['', [Validators.required]],
      organization: ['', [Validators.required]],
      organizationalUnit: [''],
      country: ['', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
      state: [''],
      locality: [''],
      email: ['', [Validators.email]],
      validDays: [365, [Validators.required, Validators.min(1), Validators.max(3650)]],
      keySize: [2048]
    });
    this.certForm.get('validDays')?.valueChanges.subscribe(days => {
      if (days && days > 0) {
        const date = new Date();
        date.setDate(date.getDate() + days);
        this.expiryDate = date;
      } else {
        this.expiryDate = null;
      }
    });
    // Trigger initial calculation
    this.expiryDate = new Date();
    this.expiryDate.setDate(this.expiryDate.getDate() + 365);
  }
  onSubmit(): void {
    if (this.certForm.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';
    this.certificateService.createRootCertificate(this.certForm.value).subscribe({
      next: () => {
        this.snackBar.open('Root sertifikat uspjesno kreiran!', 'OK', { duration: 4000 });
        this.router.navigate(['/certificates']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Greska prilikom kreiranja sertifikata.';
      }
    });
  }
}