import { FastifyInstance } from "fastify";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { adminGuard } from "../../middleware/auth";

export async function adminUserRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/v1/admin/users", { preHandler: [adminGuard] }, async (request) => {
    const query = z.object({
      page: z.coerce.number().int().min(1).default(1),
      pageSize: z.coerce.number().int().min(1).max(100).default(20),
    }).parse(request.query);

    const [users, total] = await Promise.all([
      prisma.appUser.findMany({
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
        orderBy: { createdAt: "desc" },
        select: {
          id: true,
          username: true,
          email: true,
          isActivated: true,
          createdAt: true,
          lastLoginAt: true,
          _count: { select: { usageRecords: true } },
        },
      }),
      prisma.appUser.count(),
    ]);
    return { items: users, total, page: query.page, pageSize: query.pageSize };
  });

  app.get("/api/v1/admin/users/:id", { preHandler: [adminGuard] }, async (request, reply) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(request.params);
    const user = await prisma.appUser.findUnique({
      where: { id },
      include: { activations: true, sessions: true, deviceInfos: true, usageRecords: { take: 50, orderBy: { createdAt: "desc" } } },
    });
    if (!user) return reply.status(404).send({ error: "User not found" });
    return user;
  });
}
