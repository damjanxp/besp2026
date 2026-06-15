export interface TemplateResponse {
  id: number;
  name: string;
  caIssuerId: number;
  caIssuerCommonName: string;
  ownerEmail: string;
  cnRegex: string | null;
  sanRegex: string | null;
  maxTtlDays: number | null;
  defaultKeyUsage: string | null;
  defaultExtendedKeyUsage: string | null;
  createdAt: string;
}

export interface TemplateRequest {
  name: string;
  caIssuerId: number;
  cnRegex?: string;
  sanRegex?: string;
  maxTtlDays?: number;
  defaultKeyUsage?: string;
  defaultExtendedKeyUsage?: string;
}
