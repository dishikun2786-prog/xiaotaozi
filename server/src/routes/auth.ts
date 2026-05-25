import { FastifyInstance } from "fastify";
import bcrypt from "bcrypt";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { signAccessToken, signRefreshToken, verifyToken } from "../../utils/jwt";
import { authGuard } from "../../middleware/auth";

const registerSchema = z.object({
  username: z.string().min(2).max(50).regex(/^[a-zA-Z0-9_\-.]+$/),
  password: z.string().min(6).max(100),
  email: z.string().email().max(255).optional(),
});

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});

export async function appAuthRoutes(app: FastifyInstance): Promise<void> {
  // Register
  app.post("/api/v1/auth/register", async (request, reply) => {
    const body = registerSchema.parse(request.body);
    const existing = await prisma.appUser.findUnique({ where: { username: body.username } });
    if (existing) {
      return reply.status(409).send({ error: "Username already taken" });
    }
    if (body.email) {
      const emailExists = await prisma.appUser.findUnique({ where: { email: body.email } });
      if (emailExists) return reply.status(409).send({ error: "Email already registered" });
    }
    const passwordHash = await bcrypt.hash(body.password, 12);
    const user = await prisma.appUser.create({
      data: { username: body.username, passwordHash, email: body.email },
    });
    const accessToken = signAccessToken(user.id, "app_user");
    const refreshToken = signRefreshToken(user.id, "app_user");
    await prisma.appUserSession.create({
      data: {
        appUserId: user.id,
        token: refreshToken,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });
    return reply.status(201).send({
      accessToken,
      refreshToken,
      user: { id: user.id, username: user.username, isActivated: user.isActivated },
    });
  });

  // Login
  app.post("/api/v1/auth/login", async (request, reply) => {
    const body = loginSchema.parse(request.body);
    const user = await prisma.appUser.findUnique({ where: { username: body.username } });
    if (!user) return reply.status(401).send({ error: "Invalid credentials" });
    const valid = await bcrypt.compare(body.password, user.passwordHash);
    if (!valid) return reply.status(401).send({ error: "Invalid credentials" });

    await prisma.appUser.update({ where: { id: user.id }, data: { lastLoginAt: new Date() } });
    const accessToken = signAccessToken(user.id, "app_user");
    const refreshToken = signRefreshToken(user.id, "app_user");
    await prisma.appUserSession.create({
      data: {
        appUserId: user.id,
        token: refreshToken,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });
    return {
      accessToken,
      refreshToken,
      user: { id: user.id, username: user.username, email: user.email, isActivated: user.isActivated },
    };
  });

  // Refresh
  app.post("/api/v1/auth/refresh", async (request, reply) => {
    const body = z.object({ refreshToken: z.string() }).parse(request.body);
    let payload;
    try { payload = verifyToken(body.refreshToken); } catch {
      return reply.status(401).send({ error: "Invalid refresh token" });
    }
    if (payload.type !== "refresh" || payload.role !== "app_user") {
      return reply.status(401).send({ error: "Invalid token type" });
    }
    const session = await prisma.appUserSession.findUnique({ where: { token: body.refreshToken } });
    if (!session || session.expiresAt < new Date()) {
      if (session) await prisma.appUserSession.delete({ where: { id: session.id } });
      return reply.status(401).send({ error: "Session expired" });
    }
    const user = await prisma.appUser.findUnique({ where: { id: payload.sub } });
    if (!user) return reply.status(401).send({ error: "User not found" });
    const newAccess = signAccessToken(user.id, "app_user");
    const newRefresh = signRefreshToken(user.id, "app_user");
    await prisma.appUserSession.delete({ where: { id: session.id } });
    await prisma.appUserSession.create({
      data: {
        appUserId: user.id,
        token: newRefresh,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });
    return { accessToken: newAccess, refreshToken: newRefresh };
  });

  // Get current user
  app.get("/api/v1/auth/me", { preHandler: [authGuard] }, async (request, reply) => {
    if (request.user!.role !== "app_user") return reply.status(403).send({ error: "App user access required" });
    const user = await prisma.appUser.findUnique({ where: { id: request.user!.sub } });
    if (!user) return reply.status(404).send({ error: "User not found" });
    return { id: user.id, username: user.username, email: user.email, isActivated: user.isActivated, createdAt: user.createdAt };
  });
}
