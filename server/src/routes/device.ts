import { FastifyInstance } from "fastify";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { authGuard } from "../../middleware/auth";

export async function deviceRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/v1/device/register", { preHandler: [authGuard] }, async (request) => {
    const body = z.object({
      deviceModel: z.string().max(100),
      androidVersion: z.string().max(10),
      appVersion: z.string().max(20),
    }).parse(request.body);

    const userId = request.user!.sub;
    const existing = await prisma.deviceInfo.findFirst({
      where: { appUserId: userId, deviceModel: body.deviceModel },
    });
    if (existing) {
      await prisma.deviceInfo.update({ where: { id: existing.id }, data: { lastSeenAt: new Date(), appVersion: body.appVersion } });
      return { success: true, id: existing.id };
    }
    const device = await prisma.deviceInfo.create({
      data: { appUserId: userId, ...body },
    });
    return { success: true, id: device.id };
  });
}
