import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableDataSource } from '@angular/material/table';
import { TemplateService } from '../../shared/services/template.service';
import { CertificateService } from '../../shared/services/certificate.service';
import { AuthService } from '../../shared/services/auth.service';
import { TemplateResponse } from '../../core/models/template.model';
import { CertificateResponse } from '../../core/models/certificate.model';

@Component({
  selector: 'app-template-list',
  templateUrl: './template-list.component.html',
  styleUrls: ['./template-list.component.scss']
})
export class TemplateListComponent implements OnInit {
  displayedColumns = ['name', 'caIssuerCommonName', 'maxTtlDays', 'defaultKeyUsage', 'ownerEmail', 'createdAt', 'actions'];
  dataSource = new MatTableDataSource<TemplateResponse>();
  isLoading = true;
  isSubmitting = false;

  isAdmin = false;
  isCaUser = false;

  availableCAs: CertificateResponse[] = [];
  createForm!: FormGroup;
  showForm = false;

  constructor(
    private fb: FormBuilder,
    private templateService: TemplateService,
    private certificateService: CertificateService,
    private authService: AuthService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const role = this.authService.getRole();
    this.isAdmin = role === 'ADMIN';
    this.isCaUser = role === 'CA_USER';

    this.createForm = this.fb.group({
      name:                    ['', Validators.required],
      caIssuerId:              [null, Validators.required],
      maxTtlDays:              [null],
      defaultKeyUsage:         [''],
      defaultExtendedKeyUsage: [''],
      cnRegex:                 [''],
      sanRegex:                ['']
    });

    this.loadTemplates();
    this.loadAvailableCAs();
  }

  loadTemplates(): void {
    this.isLoading = true;
    this.templateService.getMyTemplates().subscribe({
      next: (list) => { this.dataSource.data = list; this.isLoading = false; },
      error: () => { this.isLoading = false; }
    });
  }

  loadAvailableCAs(): void {
    // Koristi /api/certificates/issuers — već filtrovano na aktivne CA sa keystoreom
    this.certificateService.getAvailableIssuers().subscribe({
      next: (list) => { this.availableCAs = list; },
      error: () => {}
    });
  }

  toggleForm(): void {
    this.showForm = !this.showForm;
    if (!this.showForm) this.createForm.reset();
  }

  onSubmit(): void {
    if (this.createForm.invalid) return;
    this.isSubmitting = true;
    const v = this.createForm.value;

    this.templateService.createTemplate({
      name:                    v.name,
      caIssuerId:              v.caIssuerId,
      maxTtlDays:              v.maxTtlDays || undefined,
      defaultKeyUsage:         v.defaultKeyUsage || undefined,
      defaultExtendedKeyUsage: v.defaultExtendedKeyUsage || undefined,
      cnRegex:                 v.cnRegex || undefined,
      sanRegex:                v.sanRegex || undefined
    }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.showForm = false;
        this.createForm.reset();
        this.snackBar.open('Šablon kreiran!', 'OK', { duration: 3000 });
        this.loadTemplates();
      },
      error: (err) => {
        this.isSubmitting = false;
        this.snackBar.open(err.error?.message || 'Greška pri kreiranju.', 'OK', { duration: 4000 });
      }
    });
  }

  deleteTemplate(template: TemplateResponse): void {
    this.templateService.deleteTemplate(template.id).subscribe({
      next: () => {
        this.snackBar.open(`Šablon "${template.name}" obrisan.`, 'OK', { duration: 3000 });
        this.loadTemplates();
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Greška pri brisanju.', 'OK', { duration: 4000 });
      }
    });
  }
}
