import { FastifyInstance } from "fastify";
import { z } from "zod";
import prisma from "../../utils/prisma";
import { decrypt } from "../../utils/crypto";
import { authGuard } from "../../middleware/auth";

export async function vlmRoutes(app: FastifyInstance): Promise<void> {
  // OpenAI-compatible chat proxy
  app.post("/api/v1/vlm/chat", { preHandler: [authGuard] }, async (request, reply) => {
    if (request.user!.role !== "app_user") return reply.status(403).send({ error: "App user access required" });

    const body = request.body as Record<string, unknown>;
    const providerId = (body.providerId as string) || "aliyun";
    const model = (body.model as string) || "qwen3-vl-plus";

    const user = await prisma.appUser.findUnique({ where: { id: request.user!.sub } });
    if (!user?.isActivated) return reply.status(403).send({ error: "Account not activated" });

    const provider = await prisma.vlmProviderConfig.findUnique({ where: { providerId } });
    if (!provider || !provider.isEnabled) return reply.status(404).send({ error: "Provider not available" });

    const apiKey = decrypt(provider.apiKey);
    const baseUrl = provider.baseUrl;

    // Build VLM request
    const vlmBody = {
      model: model || provider.defaultModel,
      messages: body.messages,
      max_tokens: body.max_tokens || 4096,
      temperature: body.temperature || 0,
    };

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 120000);
      const resp = await fetch(baseUrl + "/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer " + apiKey,
        },
        body: JSON.stringify(vlmBody),
        signal: controller.signal,
      });
      clearTimeout(timeout);

      // Record usage
      await prisma.usageRecord.create({
        data: {
          appUserId: request.user!.sub,
          providerId,
          model: model || provider.defaultModel,
          requestType: "chat",
          requestCount: 1,
        },
      });

      if (!resp.ok) {
        const errText = await resp.text().catch(() => "");
        return reply.status(resp.status).send({ error: "VLM provider error: " + errText.slice(0, 500) });
      }

      const data = await resp.json();
      // Update token usage if available
      const usage = (data as Record<string, unknown>).usage as Record<string, number> | undefined;
      if (usage?.total_tokens) {
        await prisma.usageRecord.updateMany({
          where: { appUserId: request.user!.sub, providerId, requestType: "chat" },
          data: { tokensUsed: usage.total_tokens },
        });
      }
      return data;
    } catch (e: unknown) {
      const err = e as Error & { cause?: Error };
      return reply.status(502).send({ error: "VLM request failed: " + (err.cause?.message || err.message) });
    }
  });

  // GUI-Owl agent proxy
  app.post("/api/v1/vlm/gui-owl", { preHandler: [authGuard] }, async (request, reply) => {
    if (request.user!.role !== "app_user") return reply.status(403).send({ error: "App user access required" });

    const body = request.body as Record<string, unknown>;
    const providerId = (body.providerId as string) || "gui_owl";

    const user = await prisma.appUser.findUnique({ where: { id: request.user!.sub } });
    if (!user?.isActivated) return reply.status(403).send({ error: "Account not activated" });

    const provider = await prisma.vlmProviderConfig.findUnique({ where: { providerId } });
    if (!provider || !provider.isEnabled) return reply.status(404).send({ error: "Provider not available" });

    const apiKey = decrypt(provider.apiKey);
    const baseUrl = provider.baseUrl;

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 120000);
      const resp = await fetch(baseUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer " + apiKey,
        },
        body: JSON.stringify(body.payload),
        signal: controller.signal,
      });
      clearTimeout(timeout);

      await prisma.usageRecord.create({
        data: {
          appUserId: request.user!.sub,
          providerId,
          model: provider.defaultModel,
          requestType: "gui_owl",
          requestCount: 1,
        },
      });

      if (!resp.ok) {
        const errText = await resp.text().catch(() => "");
        return reply.status(resp.status).send({ error: "GUI-Owl error: " + errText.slice(0, 500) });
      }
      const data = await resp.json();
      return data;
    } catch (e: unknown) {
      const err = e as Error & { cause?: Error };
      return reply.status(502).send({ error: "GUI-Owl request failed: " + (err.cause?.message || err.message) });
    }
  });
}
