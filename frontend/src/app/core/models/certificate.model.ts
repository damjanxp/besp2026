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
}