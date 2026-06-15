export interface UserSession {
  jti: string;
  ipAddress: string;
  deviceLabel: string;
  issuedAt: string;
  lastActivityAt: string;
  current: boolean;
}
