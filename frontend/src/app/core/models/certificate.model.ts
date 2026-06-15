export interface CreateRootCertificateRequest {
  commonName: string;
  organization: string;
  organizationalUnit?: string;
  country: string;
  state?: string;
  locality?: string;
  email?: string;
  validDays: number;
  keySize: number;
}

export interface IssueCertificateRequest {
  type: 'INTERMEDIATE' | 'END_ENTITY';
  issuerCertificateId: number;
  commonName: string;
  organization: string;
  organizationalUnit?: string;
  country: string;
  state?: string;
  locality?: string;
  email?: string;
  validDays: number;
  keySize: number;
  ownerEmail?: string;
}

export interface UserProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}
export interface CertificateResponse {
  id: number;
  serialNumber: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  commonName: string;
  organization: string;
  issuerCommonName: string | null;
  ownerEmail: string;
  validFrom: string;
  validTo: string;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  certificateData: string;
  createdAt: string;
  revocationReason: string | null;
  revokedAt: string | null;
}