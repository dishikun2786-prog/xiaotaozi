import { FastifyInstance } from "fastify";
import prisma from "../../utils/prisma";
import { authGuard } from "../../middleware/auth";

export async function mobileProviderRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/v1/providers", { preHandler: [authGuard] }, async () => {
    const providers = await prisma.vlmProviderConfig.findMany({
      where: { isEnabled: true },
      orderBy: { priority: "asc" },
      select: {
        id: true,
        name: true,
        providerId: true,
        baseUrl: true,
        defaultModel: true,
        models: true,
        isGuiAgent: true,
      },
    });
    return providers.map((p) => ({
      ...p,
      models: JSON.parse(p.models) as string[],
    }));
  });
}
