import { FastifyInstance } from "fastify";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { generateCardKeys, hashCode } from "../../utils/cardkey";
import { adminGuard } from "../../middleware/auth";

const generateSchema = z.object({
  count: z.number().int().min(1).max(500),
  type: z.enum(["single", "multi"]).default("single"),
  maxUses: z.number().int().min(1).default(1),
  durationDays: z.number().int().min(1).default(365),
  expiresAt: z.string().datetime().optional(),
  notes: z.string().max(500).default(""),
  batchId: z.string().max(64).optional(),
});

export async function adminCardKeyRoutes(app: FastifyInstance): Promise<void> {
  // List card keys (paginated)
  app.get("/api/v1/admin/card-keys", { preHandler: [adminGuard] }, async (request) => {
    const query = z.object({
      page: z.coerce.number().int().min(1).default(1),
      pageSize: z.coerce.number().int().min(1).max(100).default(20),
      status: z.enum(["unused", "used", "revoked"]).optional(),
      batchId: z.string().optional(),
    }).parse(request.query);

    const where: Record<string, unknown> = {};
    if (query.status) where.status = query.status;
    if (query.batchId) where.batchId = query.batchId;

    const [items, total] = await Promise.all([
      prisma.cardKey.findMany({
        where,
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
        orderBy: { createdAt: "desc" },
      }),
      prisma.cardKey.count({ where }),
    ]);
    return {
      items: items.map((c) => ({ ...c })),
      total,
      page: query.page,
      pageSize: query.pageSize,
    };
  });

  // Generate card keys
  app.post("/api/v1/admin/card-keys/generate", { preHandler: [adminGuard] }, async (request, reply) => {
    const body = generateSchema.parse(request.body);
    const batchId = body.batchId || "batch_" + Date.now().toString(36);
    const keys = generateCardKeys(body.count);

    await prisma.cardKey.createMany({
      data: keys.map((k) => ({
        codeHash: k.codeHash,
        codePrefix: k.codePrefix,
        codeLast4: k.codeLast4,
        type: body.type,
        maxUses: body.maxUses,
        durationDays: body.durationDays,
        expiresAt: body.expiresAt ? new Date(body.expiresAt) : null,
        notes: body.notes,
        batchId,
        createdBy: request.user!.sub,
      })),
    });

    return reply.status(201).send({
      batchId,
      count: keys.length,
      keys: keys.map((k) => k.formattedCode),
    });
  });

  // Revoke card key
  app.put("/api/v1/admin/card-keys/:id", { preHandler: [adminGuard] }, async (request) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(request.params);
    const body = z.object({ status: z.enum(["revoked"]) }).parse(request.body);
    await prisma.cardKey.update({ where: { id }, data: { status: body.status } });
    return { success: true };
  });
}
