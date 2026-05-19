import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CryptoService {

  /**
   * Import a public key from PEM (SPKI format) for RSA-OAEP encryption.
   */
  async importPublicKey(pem: string): Promise<CryptoKey> {
    const binaryDer = this.pemToBinary(pem, 'PUBLIC KEY');
    return window.crypto.subtle.importKey(
      'spki',
      binaryDer,
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt']
    );
  }

  /**
   * Import a private key from PEM (PKCS8 format) for RSA-OAEP decryption.
   */
  async importPrivateKey(pem: string): Promise<CryptoKey> {
    const binaryDer = this.pemToBinary(pem, 'PRIVATE KEY');
    return window.crypto.subtle.importKey(
      'pkcs8',
      binaryDer,
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['decrypt']
    );
  }

  /**
   * Encrypt plaintext password with RSA-OAEP using the provided public key.
   * Returns Base64-encoded ciphertext.
   */
  async encryptPassword(plaintext: string, publicKey: CryptoKey): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(plaintext);
    const encrypted = await window.crypto.subtle.encrypt(
      { name: 'RSA-OAEP' },
      publicKey,
      data
    );
    return this.arrayBufferToBase64(encrypted);
  }

  /**
   * Decrypt Base64-encoded ciphertext with the provided private key.
   * Returns plaintext string.
   */
  async decryptPassword(base64Ciphertext: string, privateKey: CryptoKey): Promise<string> {
    const ciphertextBytes = this.base64ToArrayBuffer(base64Ciphertext);
    const decrypted = await window.crypto.subtle.decrypt(
      { name: 'RSA-OAEP' },
      privateKey,
      ciphertextBytes
    );
    const decoder = new TextDecoder();
    return decoder.decode(decrypted);
  }

  /**
   * Export a CryptoKey public key as a SPKI PEM string (to store on server).
   */
  async exportPublicKeyAsPem(publicKey: CryptoKey): Promise<string> {
    const exported = await window.crypto.subtle.exportKey('spki', publicKey);
    const b64 = this.arrayBufferToBase64(exported);
    const lines = b64.match(/.{1,64}/g)?.join('\n') ?? b64;
    return `-----BEGIN PUBLIC KEY-----\n${lines}\n-----END PUBLIC KEY-----`;
  }

  /**
   * Export a CryptoKey private key as a PKCS8 PEM string.
   */
  async exportPrivateKeyAsPem(privateKey: CryptoKey): Promise<string> {
    const exported = await window.crypto.subtle.exportKey('pkcs8', privateKey);
    const b64 = this.arrayBufferToBase64(exported);
    const lines = b64.match(/.{1,64}/g)?.join('\n') ?? b64;
    return `-----BEGIN PRIVATE KEY-----\n${lines}\n-----END PRIVATE KEY-----`;
  }

  /**
   * Generate a new RSA-2048 key pair for use with RSA-OAEP encryption.
   */
  async generateKeyPair(): Promise<CryptoKeyPair> {
    return window.crypto.subtle.generateKey(
      {
        name: 'RSA-OAEP',
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: 'SHA-256'
      },
      true,
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Download a text file to the user's computer.
   */
  downloadFile(content: string, filename: string): void {
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  /**
   * Read file contents as text (for PEM key files).
   */
  readFileAsText(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsText(file);
    });
  }

  private pemToBinary(pem: string, type: string): ArrayBuffer {
    const header = `-----BEGIN ${type}-----`;
    const footer = `-----END ${type}-----`;
    const base64 = pem
      .replace(header, '')
      .replace(footer, '')
      .replace(/\s+/g, '');
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    bytes.forEach(b => binary += String.fromCharCode(b));
    return btoa(binary);
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }
}

