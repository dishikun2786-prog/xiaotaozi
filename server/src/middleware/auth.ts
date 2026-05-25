import { FastifyRequest, FastifyReply } from "fastify";
import { verifyToken, JwtPayload } from "../utils/jwt";

declare module "fastify" {
  interface FastifyRequest {
    user?: JwtPayload;
  }
}

export async function authGuard(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const authHeader = request.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    reply.status(401).send({ error: "Missing or invalid Authorization header" });
    return;
  }
  const token = authHeader.slice(7);
  try {
    const payload = verifyToken(token);
    if (payload.type !== "access") {
      reply.status(401).send({ error: "Invalid token type" });
      return;
    }
    request.user = payload;
  } catch {
    reply.status(401).send({ error: "Invalid or expired token" });
  }
}

export async function adminGuard(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  await authGuard(request, reply);
  if (reply.sent) return;
  if (request.user?.role !== "admin" && request.user?.role !== "superadmin") {
    reply.status(403).send({ error: "Admin access required" });
  }
}
