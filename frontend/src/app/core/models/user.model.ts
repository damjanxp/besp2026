export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  organization?: string;
  role: 'ADMIN' | 'CA_USER' | 'END_ENTITY';
  isActive: boolean;
}
