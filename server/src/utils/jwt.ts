import jwt from "jsonwebtoken";
import { getEnv } from "../config";

export interface JwtPayload {
  sub: string;       // user id
  role: string;      // "admin" | "app_user"
  type: "access" | "refresh";
}

export function signAccessToken(userId: string, role: string): string {
  const config = getEnv();
  return jwt.sign({ sub: userId, role, type: "access" }, config.JWT_SECRET, {
    expiresIn: config.JWT_ACCESS_EXPIRES,
  });
}

export function signRefreshToken(userId: string, role: string): string {
  const config = getEnv();
  return jwt.sign({ sub: userId, role, type: "refresh" }, config.JWT_SECRET, {
    expiresIn: config.JWT_REFRESH_EXPIRES,
  });
}

export function verifyToken(token: string): JwtPayload {
  return jwt.verify(token, getEnv().JWT_SECRET) as JwtPayload;
}
