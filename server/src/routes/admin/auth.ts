import { FastifyInstance } from "fastify";
import bcrypt from "bcrypt";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { signAccessToken, signRefreshToken, verifyToken } from "../../utils/jwt";
import { adminGuard } from "../../middleware/auth";

const loginSchema = z.object({
  username: z.string().min(1).max(50),
  password: z.string().min(1).max(100),
});

export async function adminAuthRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/admin/auth/login", async (request, reply) => {
    const body = loginSchema.parse(request.body);
    const admin = await prisma.adminUser.findUnique({ where: { username: body.username } });
    if (!admin) {
      return reply.status(401).send({ error: "Invalid credentials" });
    }
    const valid = await bcrypt.compare(body.password, admin.passwordHash);
    if (!valid) {
      return reply.status(401).send({ error: "Invalid credentials" });
    }
    const accessToken = signAccessToken(admin.id, admin.role);
    const refreshToken = signRefreshToken(admin.id, admin.role);
    // Store session
    await prisma.adminSession.create({
      data: {
        adminUserId: admin.id,
        token: refreshToken,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });
    return {
      accessToken,
      refreshToken,
      user: { id: admin.id, username: admin.username, displayName: admin.displayName, role: admin.role },
    };
  });

  app.post("/api/v1/admin/auth/refresh", async (request, reply) => {
    const body = z.object({ refreshToken: z.string() }).parse(request.body);
    let payload;
    try {
      payload = verifyToken(body.refreshToken);
    } catch {
      return reply.status(401).send({ error: "Invalid refresh token" });
    }
    if (payload.type !== "refresh" || (payload.role !== "admin" && payload.role !== "superadmin")) {
      return reply.status(401).send({ error: "Invalid token type" });
    }
    const session = await prisma.adminSession.findUnique({ where: { token: body.refreshToken } });
    if (!session || session.expiresAt < new Date()) {
      if (session) await prisma.adminSession.delete({ where: { id: session.id } });
      return reply.status(401).send({ error: "Session expired" });
    }
    const admin = await prisma.adminUser.findUnique({ where: { id: payload.sub } });
    if (!admin) {
      return reply.status(401).send({ error: "User not found" });
    }
    // Rotate tokens
    const newAccessToken = signAccessToken(admin.id, admin.role);
    const newRefreshToken = signRefreshToken(admin.id, admin.role);
    await prisma.adminSession.delete({ where: { id: session.id } });
    await prisma.adminSession.create({
      data: {
        adminUserId: admin.id,
        token: newRefreshToken,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });
    return { accessToken: newAccessToken, refreshToken: newRefreshToken };
  });

  app.get("/api/v1/admin/auth/me", { preHandler: [adminGuard] }, async (request) => {
    const admin = await prisma.adminUser.findUnique({ where: { id: request.user!.sub } });
    return { id: admin!.id, username: admin!.username, displayName: admin!.displayName, role: admin!.role };
  });
}
