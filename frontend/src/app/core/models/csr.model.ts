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

export interface CsrSignResponse {
  id: number;
  serialNumber: string;
  commonName: string;
  organization: string;
  type: string;
  validFrom: string;
  validTo: string;
  status: string;
  issuerCommonName: string;
  certificateData: string;
  message: string;
}
