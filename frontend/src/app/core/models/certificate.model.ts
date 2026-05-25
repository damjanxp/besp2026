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

export interface Certificate {
  id: number;
  serialNumber: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  commonName: string;
  validFrom: string;
  validTo: string;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  issuerCommonName?: string | null;
}

export interface CertificateDetails {
  id: number;
  serialNumber: string;
  serialNumberFull?: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  commonName: string;
  organization: string | null;
  organizationalUnit?: string | null;
  country?: string | null;
  state?: string | null;
  locality?: string | null;
  emailAddress?: string | null;
  validFrom: string;
  validTo: string;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  issuerCommonName?: string | null;
  keyAlgorithm?: string | null;
  basicConstraints?: boolean;
  keyUsage?: string[];
}
