import Fastify from "fastify";
import cors from "@fastify/cors";
import rateLimit from "@fastify/rate-limit";
import { loadEnv } from "./config";
import { adminAuthRoutes } from "./routes/admin/auth";
import { adminProviderRoutes } from "./routes/admin/providers";
import { adminCardKeyRoutes } from "./routes/admin/cardKeys";
import { adminUserRoutes } from "./routes/admin/users";
import { adminStatsRoutes } from "./routes/admin/stats";
import { appAuthRoutes } from "./routes/auth";
import { activationRoutes } from "./routes/activation";
import { vlmRoutes } from "./routes/vlm";
import { mobileProviderRoutes } from "./routes/providers";
import { deviceRoutes } from "./routes/device";

async function main(): Promise<void> {
  loadEnv();

  const app = Fastify({
    logger: process.env.NODE_ENV !== "test",
    bodyLimit: 10 * 1024 * 1024, // 10MB for VLM screenshots
  });

  await app.register(cors, { origin: true, credentials: true });

  await app.register(rateLimit, {
    max: 100,
    timeWindow: "1 minute",
    keyGenerator: (request) => request.ip,
  });

  // Admin routes
  await app.register(adminAuthRoutes);
  await app.register(adminProviderRoutes);
  await app.register(adminCardKeyRoutes);
  await app.register(adminUserRoutes);
  await app.register(adminStatsRoutes);

  // App routes
  await app.register(appAuthRoutes);
  await app.register(activationRoutes);
  await app.register(vlmRoutes);
  await app.register(mobileProviderRoutes);
  await app.register(deviceRoutes);

  // Health check
  app.get("/api/health", async () => ({ status: "ok", time: new Date().toISOString() }));

  const port = parseInt(process.env.PORT || "3000", 10);
  await app.listen({ port, host: "0.0.0.0" });
  app.log.info({ port }, "小桃子 (XiaoTaozi) server started");
}

main().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});
