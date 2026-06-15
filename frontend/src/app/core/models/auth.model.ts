export interface LoginRequest {
  email: string;
  password: string;
  captchaToken: string;
}
export interface RegisterRequest {
  email: string;
  password: string;
  confirmPassword: string;
  firstName: string;
  lastName: string;
  organization?: string;
}
export interface ForgotPasswordRequest {
  email: string;
}
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}
export interface AuthResponse {
  token: string;
  email: string;
  role: string;
}
