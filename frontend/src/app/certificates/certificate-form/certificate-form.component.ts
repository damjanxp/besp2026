import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateService } from '../../shared/services/certificate.service';
import { AuthService } from '../../shared/services/auth.service';
import { CertificateResponse, UserProfile } from '../../core/models/certificate.model';

@Component({
  selector: 'app-certificate-form',
  templateUrl: './certificate-form.component.html',
  styleUrls: ['./certificate-form.component.scss']
})
export class CertificateFormComponent implements OnInit {
  certForm!: FormGroup;
  isLoading = false;
  loadingIssuers = false;
  errorMessage = '';
  expiryDate: Date | null = null;
  maxValidDays = 3650;

  isAdmin = false;
  isCaUser = false;
  availableTypes: { value: string; label: string; desc: string }[] = [];
  availableIssuers: CertificateResponse[] = [];
  caUsers: UserProfile[] = [];
  loadingCaUsers = false;

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const role = this.authService.getRole();
    this.isAdmin = role === 'ADMIN';
    this.isCaUser = role === 'CA_USER';

    if (!this.isAdmin && !this.isCaUser) {
      this.router.navigate(['/certificates']);
      return;
    }

    if (this.isAdmin) {
      this.availableTypes = [
        { value: 'ROOT',        label: 'ROOT',        desc: 'Korijenski CA — self-signed, nijedan izdavač' },
        { value: 'INTERMEDIATE', label: 'INTERMEDIATE', desc: 'Posrednički CA — potpisuje drugi CA' },
        { value: 'END_ENTITY',  label: 'END_ENTITY',  desc: 'Krajnji korisnik — lista/web/email sertifikat' }
      ];
    } else {
      this.availableTypes = [
        { value: 'INTERMEDIATE', label: 'INTERMEDIATE', desc: 'Posrednički CA — potpisuje drugi CA' },
        { value: 'END_ENTITY',  label: 'END_ENTITY',  desc: 'Krajnji korisnik — lista/web/email sertifikat' }
      ];
    }

    const defaultType = this.availableTypes[0].value;

    this.certForm = this.fb.group({
      type:                [defaultType, Validators.required],
      issuerId:            [null],
      ownerEmail:          [null],
      commonName:          ['', Validators.required],
      organization:        ['', Validators.required],
      organizationalUnit:  [''],
      country:             ['', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
      state:               [''],
      locality:            [''],
      email:               ['', Validators.email],
      validDays:           [365, [Validators.required, Validators.min(1), Validators.max(3650)]],
      keySize:             [2048]
    });

    if (this.isAdmin) {
      this.loadCaUsers();
    }

    this.certForm.get('type')!.valueChanges.subscribe(t => this.onTypeChange(t));
    this.certForm.get('issuerId')!.valueChanges.subscribe(id => this.onIssuerChange(id));
    this.certForm.get('validDays')!.valueChanges.subscribe(d => this.updateExpiryDate(d));

    this.onTypeChange(defaultType);
    this.updateExpiryDate(365);
  }

  get needsIssuer(): boolean {
    return this.certForm.get('type')!.value !== 'ROOT';
  }

  get selectedIssuer(): CertificateResponse | null {
    const id = this.certForm.get('issuerId')!.value;
    return this.availableIssuers.find(i => i.id === id) ?? null;
  }

  onTypeChange(type: string): void {
    const issuerCtrl = this.certForm.get('issuerId')!;
    if (type === 'ROOT') {
      issuerCtrl.clearValidators();
      issuerCtrl.setValue(null);
      issuerCtrl.updateValueAndValidity();
      this.availableIssuers = [];
      this.maxValidDays = 3650;
      this.updateValidDaysMax(3650);
    } else {
      issuerCtrl.setValidators(Validators.required);
      issuerCtrl.updateValueAndValidity();
      this.loadIssuers();
    }
  }

  loadIssuers(): void {
    this.loadingIssuers = true;
    this.availableIssuers = [];
    this.certificateService.getAvailableIssuers().subscribe({
      next: (list) => { this.availableIssuers = list; this.loadingIssuers = false; },
      error: () => { this.loadingIssuers = false; }
    });
  }

  loadCaUsers(): void {
    this.loadingCaUsers = true;
    this.certificateService.getCaUsers().subscribe({
      next: (users) => { this.caUsers = users; this.loadingCaUsers = false; },
      error: (err) => {
        this.loadingCaUsers = false;
        console.error('Failed to load CA users:', err);
      }
    });
  }

  get showOwnerDropdown(): boolean {
    return this.isAdmin && this.certForm.get('type')!.value !== 'ROOT';
  }

  onIssuerChange(issuerId: number | null): void {
    if (!issuerId) return;
    const issuer = this.availableIssuers.find(i => i.id === issuerId);
    if (!issuer) return;
    const msLeft = new Date(issuer.validTo).getTime() - Date.now();
    const daysLeft = Math.max(1, Math.floor(msLeft / 86400000));
    this.maxValidDays = daysLeft;
    this.updateValidDaysMax(daysLeft);
    const cur = this.certForm.get('validDays')!.value;
    if (cur > daysLeft) this.certForm.get('validDays')!.setValue(daysLeft);
    this.updateExpiryDate(this.certForm.get('validDays')!.value);
  }

  updateValidDaysMax(max: number): void {
    const ctrl = this.certForm.get('validDays')!;
    ctrl.setValidators([Validators.required, Validators.min(1), Validators.max(max)]);
    ctrl.updateValueAndValidity({ emitEvent: false });
  }

  updateExpiryDate(days: number): void {
    if (days && days > 0) {
      const d = new Date();
      d.setDate(d.getDate() + Number(days));
      this.expiryDate = d;
    } else {
      this.expiryDate = null;
    }
  }

  goBack(): void {
    this.router.navigate(['/certificates']);
  }

  onSubmit(): void {
    if (this.certForm.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';
    const v = this.certForm.value;

    if (v.type === 'ROOT') {
      this.certificateService.createRootCertificate({
        commonName: v.commonName,
        organization: v.organization,
        organizationalUnit: v.organizationalUnit || undefined,
        country: v.country,
        state: v.state || undefined,
        locality: v.locality || undefined,
        email: v.email || undefined,
        validDays: v.validDays,
        keySize: v.keySize
      }).subscribe({
        next: () => {
          this.snackBar.open('Root sertifikat uspješno kreiran!', 'OK', { duration: 4000 });
          this.router.navigate(['/certificates']);
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err.error?.message || 'Greška pri kreiranju.';
        }
      });
    } else {
      this.certificateService.issueCertificate({
        type: v.type,
        issuerCertificateId: v.issuerId,
        commonName: v.commonName,
        organization: v.organization,
        organizationalUnit: v.organizationalUnit || undefined,
        country: v.country,
        state: v.state || undefined,
        locality: v.locality || undefined,
        email: v.email || undefined,
        validDays: v.validDays,
        keySize: v.keySize,
        ownerEmail: v.ownerEmail || undefined
      }).subscribe({
        next: () => {
          this.snackBar.open('Sertifikat uspješno izdat!', 'OK', { duration: 4000 });
          this.router.navigate(['/certificates']);
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err.error?.message || 'Greška pri izdavanju.';
        }
      });
    }
  }
}
