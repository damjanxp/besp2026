export interface PasswordEntry {
  id: number;
  siteName: string;
  username: string;
  ownerId: number;
  ownerEmail: string;
  encryptedPassword: string;
  createdAt: string;
  updatedAt: string;
  // Runtime only — never sent to server
  decryptedPassword?: string;
}

export interface SavePasswordRequest {
  siteName: string;
  username: string;
  encryptedPassword: string; // Base64 RSA-OAEP encrypted
}

