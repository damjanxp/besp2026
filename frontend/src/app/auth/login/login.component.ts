import { Component, ViewChild, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
import { RecaptchaComponent } from 'ng-recaptcha';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../environments/environment';
@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  @ViewChild('captchaRef') captchaRef!: RecaptchaComponent;
  loginForm: FormGroup;
  isLoading = false;
  errorMessage = '';
  hidePassword = true;
  siteKey = environment.recaptchaSiteKey;
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
      captchaToken: ['', Validators.required]
    });
  }
  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const message = params['message'];
      if (message) {
        this.snackBar.open(message, 'OK', { duration: 5000 });
      }
    });
  }
  onSubmit(): void {
    if (this.loginForm.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';
    const { email, password, captchaToken } = this.loginForm.value;
    this.authService.login(email, password, captchaToken).subscribe({
      next: (response) => {
        this.authService.saveToken(response.token);
        this.router.navigate(['/certificates']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Greska prilikom prijave.';
        if (this.captchaRef) {
          this.captchaRef.reset();
        }
        this.loginForm.get('captchaToken')?.setValue('');
      }
    });
  }
}
