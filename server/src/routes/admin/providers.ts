import { FastifyInstance } from "fastify";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { encrypt, decrypt } from "../../utils/crypto";
import { adminGuard } from "../../middleware/auth";

const providerSchema = z.object({
  name: z.string().min(1).max(100),
  providerId: z.string().min(1).max(50),
  apiKey: z.string().min(1),
  baseUrl: z.string().min(1).max(500),
  defaultModel: z.string().min(1).max(100),
  models: z.array(z.string()).default([]),
  isGuiAgent: z.boolean().default(false),
  isEnabled: z.boolean().default(true),
  priority: z.number().int().default(0),
});

export async function adminProviderRoutes(app: FastifyInstance): Promise<void> {
  // List all providers
  app.get("/api/v1/admin/providers", { preHandler: [adminGuard] }, async () => {
    const providers = await prisma.vlmProviderConfig.findMany({ orderBy: { priority: "asc" } });
    return providers.map((p) => ({
      ...p,
      apiKey: "••••••••" + decrypt(p.apiKey).slice(-4),
      models: JSON.parse(p.models),
    }));
  });

  // Create provider
  app.post("/api/v1/admin/providers", { preHandler: [adminGuard] }, async (request, reply) => {
    const body = providerSchema.parse(request.body);
    const encryptedKey = encrypt(body.apiKey);
    const provider = await prisma.vlmProviderConfig.create({
      data: {
        ...body,
        apiKey: encryptedKey,
        models: JSON.stringify(body.models),
      },
    });
    return reply.status(201).send({ id: provider.id });
  });

  // Update provider
  app.put("/api/v1/admin/providers/:id", { preHandler: [adminGuard] }, async (request, reply) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(request.params);
    const body = providerSchema.partial().parse(request.body);
    const data: Record<string, unknown> = { ...body };
    if (body.apiKey) data.apiKey = encrypt(body.apiKey);
    if (body.models) data.models = JSON.stringify(body.models);
    await prisma.vlmProviderConfig.update({ where: { id }, data });
    return { success: true };
  });

  // Delete provider
  app.delete("/api/v1/admin/providers/:id", { preHandler: [adminGuard] }, async (request, reply) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(request.params);
    await prisma.vlmProviderConfig.delete({ where: { id } });
    return { success: true };
  });

  // Test connectivity
  app.post("/api/v1/admin/providers/:id/test", { preHandler: [adminGuard] }, async (request, reply) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(request.params);
    const provider = await prisma.vlmProviderConfig.findUnique({ where: { id } });
    if (!provider) return reply.status(404).send({ error: "Provider not found" });
    const apiKey = decrypt(provider.apiKey);
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10000);
      const resp = await fetch(provider.baseUrl + "/models", {
        headers: { Authorization: "Bearer " + apiKey },
        signal: controller.signal,
      });
      clearTimeout(timeout);
      if (resp.ok) {
        const data = await resp.json() as Record<string, unknown>;
        const models = data.data ? (data.data as Array<{ id?: string }>).map((m: { id?: string }) => m.id || "").filter(Boolean) : [];
        return { success: true, models };
      }
      return { success: false, error: "HTTP " + resp.status + ": " + (await resp.text()).slice(0, 200) };
    } catch (e: unknown) {
      const err = e as Error & { cause?: Error };
      return { success: false, error: err.cause?.message || err.message };
    }
  });
}
