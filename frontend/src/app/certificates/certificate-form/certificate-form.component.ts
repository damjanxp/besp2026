import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateService } from '../../shared/services/certificate.service';
import { AuthService } from '../../shared/services/auth.service';
import { TemplateService } from '../../shared/services/template.service';
import { CertificateResponse, UserProfile } from '../../core/models/certificate.model';
import { TemplateResponse } from '../../core/models/template.model';

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
  endEntityUsers: UserProfile[] = [];
  loadingEndEntityUsers = false;

  availableTemplates: TemplateResponse[] = [];
  loadingTemplates = false;
  activeTemplate: TemplateResponse | null = null;
  issuerMaxValidDays = 3650;

  readonly KEY_USAGE_OPTIONS = [
    'digitalSignature', 'nonRepudiation', 'keyEncipherment',
    'dataEncipherment', 'keyAgreement', 'keyCertSign', 'cRLSign',
    'encipherOnly', 'decipherOnly'
  ];

  readonly EKU_OPTIONS = [
    'serverAuth', 'clientAuth', 'codeSigning',
    'emailProtection', 'timeStamping', 'OCSPSigning'
  ];

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private templateService: TemplateService,
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
      templateId:          [null],
      ownerEmail:          [null],
      commonName:          ['', Validators.required],
      organization:        ['', Validators.required],
      organizationalUnit:  [''],
      country:             ['', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
      state:               [''],
      locality:            [''],
      email:               ['', Validators.email],
      san:                 [''],
      keyUsage:            [[]],
      extendedKeyUsage:    [[]],
      validDays:           [365, [Validators.required, Validators.min(1), Validators.max(3650)]],
      keySize:             [2048]
    });

    if (this.isAdmin) {
      this.loadCaUsers();
    }
    this.loadEndEntityUsers();

    this.certForm.get('type')!.valueChanges.subscribe(t => this.onTypeChange(t));
    this.certForm.get('issuerId')!.valueChanges.subscribe(id => this.onIssuerChange(id));
    this.certForm.get('templateId')!.valueChanges.subscribe(id => this.onTemplateChange(id));
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

  get currentType(): string {
    return this.certForm.get('type')!.value;
  }

  get showOwnerDropdown(): boolean {
    const type = this.currentType;
    if (this.isAdmin) return type !== 'ROOT';
    if (this.isCaUser) return type === 'END_ENTITY';
    return false;
  }

  get ownerDropdownUsers(): UserProfile[] {
    return this.currentType === 'END_ENTITY' ? this.endEntityUsers : this.caUsers;
  }

  get ownerDropdownLoading(): boolean {
    return this.currentType === 'END_ENTITY' ? this.loadingEndEntityUsers : this.loadingCaUsers;
  }

  get ownerDropdownLabel(): string {
    return this.currentType === 'END_ENTITY'
      ? 'Dodjeli krajnjem korisniku (opcionalno)'
      : 'Dodjeli CA korisniku (opcionalno)';
  }

  get ownerDropdownHint(): string {
    if (this.currentType === 'END_ENTITY') {
      return 'Izaberi korisnika kome se dodjeljuje sertifikat. Ako ostaviš prazno, sertifikat ostaje tebi.';
    }
    return 'Dodjeli CA korisniku da može koristiti sertifikat za izdavanje. Ako ostaviš prazno, dodjeljuje se tebi.';
  }

  get noUsersMessage(): string {
    if (this.currentType === 'END_ENTITY') {
      return 'Nema registrovanih krajnjih korisnika. Sertifikat će biti dodijeljen tebi.';
    }
    return 'Nema registrovanih CA korisnika. Sertifikat će biti dodijeljen adminu.';
  }

  onTypeChange(type: string): void {
    const issuerCtrl = this.certForm.get('issuerId')!;
    this.certForm.get('ownerEmail')!.setValue(null);

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

  loadEndEntityUsers(): void {
    this.loadingEndEntityUsers = true;
    this.certificateService.getEndEntityUsers().subscribe({
      next: (users) => { this.endEntityUsers = users; this.loadingEndEntityUsers = false; },
      error: (err) => {
        this.loadingEndEntityUsers = false;
        console.error('Failed to load end-entity users:', err);
      }
    });
  }

  onIssuerChange(issuerId: number | null): void {
    this.availableTemplates = [];
    this.activeTemplate = null;
    this.certForm.get('templateId')!.setValue(null, { emitEvent: false });

    if (!issuerId) return;
    const issuer = this.availableIssuers.find(i => i.id === issuerId);
    if (!issuer) return;
    const msLeft = new Date(issuer.validTo).getTime() - Date.now();
    const daysLeft = Math.max(1, Math.floor(msLeft / 86400000));
    this.issuerMaxValidDays = daysLeft;
    this.maxValidDays = daysLeft;
    this.updateValidDaysMax(daysLeft);
    const cur = this.certForm.get('validDays')!.value;
    if (cur > daysLeft) this.certForm.get('validDays')!.setValue(daysLeft);
    this.updateExpiryDate(this.certForm.get('validDays')!.value);

    if (this.isAdmin || this.isCaUser) {
      this.loadTemplatesForIssuer(issuerId);
    }
  }

  loadTemplatesForIssuer(caId: number): void {
    this.loadingTemplates = true;
    this.templateService.getTemplatesForCa(caId).subscribe({
      next: (templates) => { this.availableTemplates = templates; this.loadingTemplates = false; },
      error: () => { this.loadingTemplates = false; }
    });
  }

  onTemplateChange(templateId: number | null): void {
    if (!templateId) {
      this.activeTemplate = null;
      this.maxValidDays = this.issuerMaxValidDays;
      this.updateValidDaysMax(this.issuerMaxValidDays);
      return;
    }
    this.activeTemplate = this.availableTemplates.find(t => t.id === templateId) ?? null;
    if (!this.activeTemplate) return;

    if (this.activeTemplate.defaultKeyUsage) {
      const kuArray = this.activeTemplate.defaultKeyUsage.split(',').map(k => k.trim()).filter(Boolean);
      this.certForm.get('keyUsage')!.setValue(kuArray, { emitEvent: false });
    }
    if (this.activeTemplate.defaultExtendedKeyUsage) {
      const ekuArray = this.activeTemplate.defaultExtendedKeyUsage.split(',').map(k => k.trim()).filter(Boolean);
      this.certForm.get('extendedKeyUsage')!.setValue(ekuArray, { emitEvent: false });
    }
    if (this.activeTemplate.maxTtlDays) {
      const capped = Math.min(this.issuerMaxValidDays, this.activeTemplate.maxTtlDays);
      this.maxValidDays = capped;
      this.updateValidDaysMax(capped);
      const cur = this.certForm.get('validDays')!.value;
      if (cur > capped) this.certForm.get('validDays')!.setValue(capped);
      this.updateExpiryDate(this.certForm.get('validDays')!.value);
    }
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
        ownerEmail: v.ownerEmail || undefined,
        templateId: v.templateId || undefined,
        san: v.san || undefined,
        keyUsage: Array.isArray(v.keyUsage) && v.keyUsage.length > 0 ? v.keyUsage.join(',') : undefined,
        extendedKeyUsage: Array.isArray(v.extendedKeyUsage) && v.extendedKeyUsage.length > 0 ? v.extendedKeyUsage.join(',') : undefined
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
