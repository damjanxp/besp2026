import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {
  forgotForm: FormGroup;
  isLoading = false;
  isSubmitted = false;
  message = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService
  ) {
    this.forgotForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });
  }

  onSubmit(): void {
    if (this.forgotForm.invalid) return;

    this.isLoading = true;
    this.isSubmitted = false;

    this.authService.forgotPassword(this.forgotForm.value).subscribe({
      next: () => this.showGenericMessage(),
      error: () => this.showGenericMessage()
    });
  }

  private showGenericMessage(): void {
    this.isLoading = false;
    this.isSubmitted = true;
    this.message = 'Ako nalog postoji, poslan je email za resetovanje lozinke';
  }
}

