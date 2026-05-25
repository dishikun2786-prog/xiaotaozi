import { FastifyInstance } from "fastify";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { hashCode } from "../../utils/cardkey";
import { authGuard } from "../../middleware/auth";

const activateSchema = z.object({
  cardCode: z.string().regex(/^XTZ-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/),
});

export async function activationRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/activation", { preHandler: [authGuard] }, async (request, reply) => {
    if (request.user!.role !== "app_user") return reply.status(403).send({ error: "App user access required" });

    const body = activateSchema.parse(request.body);
    // Extract plain code from formatted card key
    const plainCode = body.cardCode.replace("XTZ-", "").replace(/-/g, "");
    const codeHash = hashCode(plainCode);

    const cardKey = await prisma.cardKey.findUnique({ where: { codeHash } });
    if (!cardKey) return reply.status(404).send({ error: "Invalid card key" });

    if (cardKey.status === "used" && cardKey.type === "single") {
      return reply.status(400).send({ error: "Card key already used" });
    }
    if (cardKey.status === "revoked") {
      return reply.status(400).send({ error: "Card key has been revoked" });
    }
    if (cardKey.expiresAt && cardKey.expiresAt < new Date()) {
      return reply.status(400).send({ error: "Card key has expired" });
    }
    if (cardKey.currentUses >= cardKey.maxUses) {
      return reply.status(400).send({ error: "Card key usage limit reached" });
    }

    const userId = request.user!.sub;

    // Check if user already activated
    const existingActivation = await prisma.userActivation.findFirst({
      where: { appUserId: userId, isActive: true, expiresAt: { gt: new Date() } },
    });
    if (existingActivation) {
      return reply.status(400).send({ error: "Account already activated" });
    }

    // Activate
    const activationExpires = new Date(Date.now() + cardKey.durationDays * 24 * 60 * 60 * 1000);
    await prisma.$transaction([
      prisma.userActivation.create({
        data: {
          appUserId: userId,
          cardKeyId: cardKey.id,
          expiresAt: activationExpires,
        },
      }),
      prisma.cardKey.update({
        where: { id: cardKey.id },
        data: {
          currentUses: cardKey.currentUses + 1,
          status: cardKey.currentUses + 1 >= cardKey.maxUses ? "used" : "unused",
          usedAt: new Date(),
        },
      }),
      prisma.appUser.update({
        where: { id: userId },
        data: { isActivated: true },
      }),
    ]);

    return { success: true, expiresAt: activationExpires.toISOString() };
  });
}
