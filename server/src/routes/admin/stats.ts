import { FastifyInstance } from "fastify";
import prisma from "../../utils/prisma";
import { adminGuard } from "../../middleware/auth";

export async function adminStatsRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/v1/admin/stats/overview", { preHandler: [adminGuard] }, async () => {
    const [totalUsers, activeUsers, totalCardKeys, usedCardKeys, totalRequests] = await Promise.all([
      prisma.appUser.count(),
      prisma.appUser.count({ where: { isActivated: true } }),
      prisma.cardKey.count(),
      prisma.cardKey.count({ where: { status: "used" } }),
      prisma.usageRecord.count(),
    ]);

    // Usage by provider (last 30 days)
    const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const recentUsage = await prisma.usageRecord.findMany({
      where: { createdAt: { gte: thirtyDaysAgo } },
      select: { providerId: true, requestCount: true, tokensUsed: true, createdAt: true },
    });

    const providerUsage: Record<string, { requests: number; tokens: number }> = {};
    for (const u of recentUsage) {
      if (!providerUsage[u.providerId]) providerUsage[u.providerId] = { requests: 0, tokens: 0 };
      providerUsage[u.providerId].requests += u.requestCount;
      providerUsage[u.providerId].tokens += u.tokensUsed;
    }

    return {
      totalUsers,
      activeUsers,
      totalCardKeys,
      usedCardKeys,
      totalRequests,
      providerUsage,
    };
  });
}
