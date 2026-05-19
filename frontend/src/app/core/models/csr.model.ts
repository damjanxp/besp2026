export interface CsrInfo {
  commonName: string;
  organization: string;
  organizationalUnit?: string;
  country: string;
  state?: string;
  locality?: string;
  email?: string;
  publicKeyAlgorithm: string;
  publicKeySize: number;
  signatureAlgorithm: string;
  isSignatureValid: boolean;
  rawPem?: string;
}

