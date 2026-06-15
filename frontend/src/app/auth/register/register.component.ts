import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../shared/services/auth.service';
import * as zxcvbn from 'zxcvbn';
@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent implements OnInit {
  registerForm!: FormGroup;
  isLoading = false;
  errorMessage = '';
  hidePassword = true;
  hideConfirmPassword = true;
  passwordStrengthScore = 0;
  strengthLabels = ['Veoma slaba', 'Slaba', 'Prihvatljiva', 'Jaka', 'Veoma jaka'];
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}
  ngOnInit(): void {
    this.registerForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      organization: ['', [Validators.required]],
      password: ['', [
        Validators.required,
        Validators.minLength(12),
        this.passwordComplexityValidator
      ]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });
    this.registerForm.get('password')?.valueChanges.subscribe(value => {
      if (value) {
        const result = (zxcvbn as any).default ? (zxcvbn as any).default(value) : (zxcvbn as any)(value);
        this.passwordStrengthScore = result.score;
      } else {
        this.passwordStrengthScore = 0;
      }
    });
  }
  get password() { return this.registerForm.get('password'); }
  get hasMinLength(): boolean { return (this.password?.value?.length || 0) >= 12; }
  get hasUppercase(): boolean { return /[A-Z]/.test(this.password?.value || ''); }
  get hasLowercaseAndDigit(): boolean { return /[a-z]/.test(this.password?.value || '') && /\d/.test(this.password?.value || ''); }
  get hasSpecialChar(): boolean { return /[!@#$%^&*()\-_+={}[\]:;"'<>,.?\/~`|\\]/.test(this.password?.value || ''); }
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
    const hasSpecial = /[!@#$%^&*()\-_+={}[\]:;"'<>,.?\/~`|\\]/.test(value);
    if (hasUpper && hasLower && hasDigit && hasSpecial) return null;
    return { complexity: true };
  }
  passwordMatchValidator(group: FormGroup): ValidationErrors | null {
    const password = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    if (password && confirm && password !== confirm) {
      return { passwordMismatch: true };
    }
    return null;
  }
  onSubmit(): void {
    if (this.registerForm.invalid || this.passwordStrengthScore < 3) return;
    this.isLoading = true;
    this.errorMessage = '';
    this.authService.register(this.registerForm.value).subscribe({
      next: () => {
        this.snackBar.open('Registracija uspjesna! Provjerite email.', 'OK', { duration: 5000 });
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Greska prilikom registracije.';
      }
    });
  }
}
