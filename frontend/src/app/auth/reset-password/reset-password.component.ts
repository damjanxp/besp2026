import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import * as zxcvbn from 'zxcvbn';

@Component({
  selector: 'app-reset-password',
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.scss']
})
export class ResetPasswordComponent implements OnInit {
  resetForm!: FormGroup;
  isLoading = false;
  errorMessage = '';
  hidePassword = true;
  hideConfirmPassword = true;
  passwordStrengthScore = 0;
  strengthLabels = ['Veoma slaba', 'Slaba', 'Prihvatljiva', 'Jaka', 'Veoma jaka'];
  token = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.resetForm = this.fb.group({
      newPassword: ['', [
        Validators.required,
        Validators.minLength(12),
        this.passwordComplexityValidator
      ]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });

    this.resetForm.get('newPassword')?.valueChanges.subscribe(value => {
      if (value) {
        const result = (zxcvbn as any).default ? (zxcvbn as any).default(value) : (zxcvbn as any)(value);
        this.passwordStrengthScore = result.score;
      } else {
        this.passwordStrengthScore = 0;
      }
    });

    this.route.queryParams.subscribe(params => {
      this.token = params['token'] || '';
      if (!this.token) {
        this.errorMessage = 'Link je istekao ili je vec iskoriscen';
      }
    });
  }

  get newPassword() { return this.resetForm.get('newPassword'); }
  get hasMinLength(): boolean { return (this.newPassword?.value?.length || 0) >= 12; }
  get hasUppercase(): boolean { return /[A-Z]/.test(this.newPassword?.value || ''); }
  get hasLowercaseAndDigit(): boolean { return /[a-z]/.test(this.newPassword?.value || '') && /\d/.test(this.newPassword?.value || ''); }
  get hasSpecialChar(): boolean { return /[!@#$%^&*()\-_+={}\[\]:;"'<>,.?\/~`|\\]/.test(this.newPassword?.value || ''); }
  get strengthColor(): string {
    if (this.passwordStrengthScore < 2) return 'warn';
    if (this.passwordStrengthScore < 4) return 'accent';
    return 'primary';
  }

  passwordComplexityValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (!value) return null;
    const hasUpper = /[A-Z]/.test(value);
    const hasLower = /[a-z]/.test(value);
    const hasDigit = /\d/.test(value);
    const hasSpecial = /[!@#$%^&*()\-_+={}\[\]:;"'<>,.?\/~`|\\]/.test(value);
    if (hasUpper && hasLower && hasDigit && hasSpecial) return null;
    return { complexity: true };
  }

  passwordMatchValidator(group: FormGroup): ValidationErrors | null {
    const password = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    if (password && confirm && password !== confirm) {
      return { passwordMismatch: true };
    }
    return null;
  }

  onSubmit(): void {
    if (this.resetForm.invalid || this.passwordStrengthScore < 3 || !this.token) return;

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.resetPassword({
      token: this.token,
      newPassword: this.resetForm.value.newPassword,
      confirmPassword: this.resetForm.value.confirmPassword
    }).subscribe({
      next: () => {
        this.router.navigate(['/login'], { queryParams: { message: 'Lozinka uspjesno promijenjena' } });
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Link je istekao ili je vec iskoriscen';
      }
    });
  }
}

