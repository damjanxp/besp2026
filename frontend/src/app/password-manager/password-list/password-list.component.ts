import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PasswordEntry } from '../../core/models/password-manager.model';
import { PasswordManagerService } from '../../shared/services/password-manager.service';
import { CryptoService } from '../../shared/services/crypto.service';

@Component({
  selector: 'app-password-list',
  templateUrl: './password-list.component.html',
  styleUrls: ['./password-list.component.scss']
})
export class PasswordListComponent implements OnInit {
  entries: PasswordEntry[] = [];
  displayedColumns = ['siteName', 'username', 'password', 'actions'];
  loading = false;

  // For decryption
  privateKey: CryptoKey | null = null;
  privateKeyLoaded = false;
  privateKeyLoading = false;

  // For adding new entry
  showAddForm = false;
  addForm: FormGroup;
  publicKey: CryptoKey | null = null;
  publicKeyLoaded = false;
  addLoading = false;

  // Public key registration (store key on server)
  serverPublicKeyStatus: 'unknown' | 'saved' | 'missing' = 'unknown';
  registeringPublicKey = false;
  generatingKeyPair = false;

  constructor(
    private passwordManagerService: PasswordManagerService,
    private cryptoService: CryptoService,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) {
    this.addForm = this.fb.group({
      siteName: ['', Validators.required],
      username: ['', Validators.required],
      password: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadEntries();
    this.checkServerPublicKey();
  }

  checkServerPublicKey(): void {
    this.passwordManagerService.getMyPublicKey().subscribe({
      next: (profile) => {
        this.serverPublicKeyStatus = profile.publicKeySpki ? 'saved' : 'missing';
      },
      error: () => { this.serverPublicKeyStatus = 'missing'; }
    });
  }

  /**
   * Upload a public key PEM file and register it on the server.
   * Called separately from the encryption flow so the key is stored once
   * and can be retrieved by other users for sharing.
   */
  async onRegisterPublicKeyFileSelected(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.registeringPublicKey = true;
    try {
      const pem = await this.cryptoService.readFileAsText(file);
      // Validate that the PEM can actually be imported before saving
      await this.cryptoService.importPublicKey(pem);
      this.passwordManagerService.saveMyPublicKey(pem.trim()).subscribe({
        next: () => {
          this.serverPublicKeyStatus = 'saved';
          this.snackBar.open('Javni ključ registrovan na serveru ✓', 'OK', { duration: 3000 });
          this.registeringPublicKey = false;
        },
        error: () => {
          this.snackBar.open('Greška pri čuvanju javnog ključa na serveru', 'OK', { duration: 3000 });
          this.registeringPublicKey = false;
        }
      });
    } catch {
      this.snackBar.open('Neispravan format javnog ključa (SPKI PEM)', 'OK', { duration: 4000 });
      this.registeringPublicKey = false;
    }
  }

  /**
   * Generate a new RSA-2048 key pair, auto-register public key on server,
   * and download both keys as PEM files for the user to save.
   */
  async generateAndDownloadKeyPair(): Promise<void> {
    this.generatingKeyPair = true;
    try {
      const keyPair = await this.cryptoService.generateKeyPair();
      const publicPem = await this.cryptoService.exportPublicKeyAsPem(keyPair.publicKey);
      const privatePem = await this.cryptoService.exportPrivateKeyAsPem(keyPair.privateKey);

      // Download both files
      this.cryptoService.downloadFile(publicPem, 'public_key.pem');
      this.cryptoService.downloadFile(privatePem, 'private_key.pem');

      // Auto-register public key on server
      this.passwordManagerService.saveMyPublicKey(publicPem.trim()).subscribe({
        next: () => {
          this.serverPublicKeyStatus = 'saved';
          // Also load it locally for immediate use in add form
          this.cryptoService.importPublicKey(publicPem).then(k => {
            this.publicKey = k;
            this.publicKeyLoaded = true;
          });
          this.snackBar.open('Ključ par generisan! Sačuvajte private_key.pem na sigurno mesto.', 'OK', { duration: 6000 });
        },
        error: () => {
          this.snackBar.open('Ključevi preuzeti, ali greška pri registraciji javnog ključa na serveru.', 'OK', { duration: 5000 });
        }
      });
    } catch {
      this.snackBar.open('Greška pri generisanju ključeva', 'OK', { duration: 3000 });
    } finally {
      this.generatingKeyPair = false;
    }
  }

  loadEntries(): void {
    this.loading = true;
    this.passwordManagerService.getMyPasswords().subscribe({
      next: (data) => {
        this.entries = data.map(e => ({ ...e, decryptedPassword: undefined }));
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Greška pri učitavanju lozinki', 'OK', { duration: 3000 });
        this.loading = false;
      }
    });
  }

  async onPrivateKeyFileSelected(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.privateKeyLoading = true;
    try {
      const pem = await this.cryptoService.readFileAsText(file);
      this.privateKey = await this.cryptoService.importPrivateKey(pem);
      this.privateKeyLoaded = true;
      this.snackBar.open('Privatni ključ uspešno učitan', 'OK', { duration: 2000 });
    } catch (e) {
      this.snackBar.open('Greška pri učitavanju privatnog ključa. Proverite format (PKCS8 PEM).', 'OK', { duration: 4000 });
    } finally {
      this.privateKeyLoading = false;
    }
  }

  async decryptEntry(entry: PasswordEntry): Promise<void> {
    if (!this.privateKey) {
      this.snackBar.open('Prvo učitajte privatni ključ', 'OK', { duration: 3000 });
      return;
    }
    try {
      entry.decryptedPassword = await this.cryptoService.decryptPassword(entry.encryptedPassword, this.privateKey);
    } catch {
      this.snackBar.open('Dekripcija nije uspela. Proverite da li je ispravan privatni ključ.', 'OK', { duration: 4000 });
    }
  }

  hidePassword(entry: PasswordEntry): void {
    entry.decryptedPassword = undefined;
  }

  async onPublicKeyFileSelected(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    try {
      const pem = await this.cryptoService.readFileAsText(file);
      this.publicKey = await this.cryptoService.importPublicKey(pem);
      this.publicKeyLoaded = true;
      this.snackBar.open('Javni ključ uspešno učitan', 'OK', { duration: 2000 });
    } catch {
      this.snackBar.open('Greška pri učitavanju javnog ključa. Proverite format (SPKI PEM).', 'OK', { duration: 4000 });
    }
  }

  async onAddSubmit(): Promise<void> {
    if (this.addForm.invalid) return;
    if (!this.publicKey) {
      this.snackBar.open('Prvo učitajte javni ključ za enkripciju', 'OK', { duration: 3000 });
      return;
    }
    this.addLoading = true;
    try {
      const { siteName, username, password } = this.addForm.value;
      const encryptedPassword = await this.cryptoService.encryptPassword(password, this.publicKey);
      this.passwordManagerService.savePassword({ siteName, username, encryptedPassword }).subscribe({
        next: () => {
          this.snackBar.open('Lozinka sačuvana', 'OK', { duration: 2000 });
          this.addForm.reset();
          this.showAddForm = false;
          this.publicKey = null;
          this.publicKeyLoaded = false;
          this.loadEntries();
          this.addLoading = false;
        },
        error: () => {
          this.snackBar.open('Greška pri čuvanju lozinke', 'OK', { duration: 3000 });
          this.addLoading = false;
        }
      });
    } catch {
      this.snackBar.open('Greška pri enkripciji lozinke', 'OK', { duration: 3000 });
      this.addLoading = false;
    }
  }

  deleteEntry(entry: PasswordEntry): void {
    if (!confirm(`Obrisati lozinku za "${entry.siteName}"?`)) return;
    this.passwordManagerService.deletePassword(entry.id).subscribe({
      next: () => {
        this.snackBar.open('Lozinka obrisana', 'OK', { duration: 2000 });
        this.loadEntries();
      },
      error: () => this.snackBar.open('Greška pri brisanju', 'OK', { duration: 3000 })
    });
  }
}

