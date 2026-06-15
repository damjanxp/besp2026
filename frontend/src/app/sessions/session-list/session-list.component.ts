import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserSession } from '../../core/models/session.model';
import { SessionService } from '../../shared/services/session.service';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-session-list',
  templateUrl: './session-list.component.html',
  styleUrls: ['./session-list.component.scss']
})
export class SessionListComponent implements OnInit {
  sessions: UserSession[] = [];
  displayedColumns = ['device', 'ipAddress', 'lastActivityAt', 'issuedAt', 'actions'];
  loading = false;

  constructor(
    private sessionService: SessionService,
    private snackBar: MatSnackBar,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading = true;
    this.sessionService.getMySessions().subscribe({
      next: (data) => {
        this.sessions = data;
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Greška pri učitavanju sesija', 'OK', { duration: 3000 });
        this.loading = false;
      }
    });
  }

  revoke(session: UserSession): void {
    const label = session.current ? 'trenutnu sesiju (bićete odjavljeni)' : `sesiju (${session.deviceLabel})`;
    if (!confirm(`Opozvati ${label}?`)) return;

    this.sessionService.revokeSession(session.jti).subscribe({
      next: () => {
        if (session.current) {
          // We just revoked our own token — log out locally and redirect
          this.authService.logout();
          this.router.navigate(['/login']);
          this.snackBar.open('Odjavljeni ste sa ovog uređaja', 'OK', { duration: 3000 });
        } else {
          this.snackBar.open('Sesija opozvana', 'OK', { duration: 2000 });
          this.loadSessions();
        }
      },
      error: () => this.snackBar.open('Greška pri opozivu sesije', 'OK', { duration: 3000 })
    });
  }
}
