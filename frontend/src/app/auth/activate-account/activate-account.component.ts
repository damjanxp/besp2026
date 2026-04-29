import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';
@Component({
  selector: 'app-activate-account',
  templateUrl: './activate-account.component.html',
  styleUrls: ['./activate-account.component.scss']
})
export class ActivateAccountComponent implements OnInit {
  isLoading = true;
  isSuccess = false;
  errorMessage = '';
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {}
  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      if (!token) {
        this.isLoading = false;
        this.errorMessage = 'Aktivacioni token nije pronadjen.';
        return;
      }
      this.authService.activateAccount(token).subscribe({
        next: () => {
          this.isLoading = false;
          this.isSuccess = true;
        },
        error: (err) => {
          this.isLoading = false;
          this.isSuccess = false;
          this.errorMessage = err.error?.message || 'Greska prilikom aktivacije naloga.';
        }
      });
    });
  }
  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
