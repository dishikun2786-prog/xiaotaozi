import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "../lib/api";
import { Plus, Ban, RefreshCw } from "lucide-react";

interface CardKey {
  id: string;
  codePrefix: string;
  codeLast4: string;
  status: string;
  type: string;
  maxUses: number;
  currentUses: number;
  durationDays: number;
  batchId: string | null;
  notes: string;
  createdAt: string;
  usedAt: string | null;
}

interface GenerateResult {
  batchId: string;
  count: number;
  keys: string[];
}

export default function CardKeysPage() {
  const [items, setItems] = useState<CardKey[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [showGenerate, setShowGenerate] = useState(false);
  const [generated, setGenerated] = useState<GenerateResult | null>(null);
  const [statusFilter, setStatusFilter] = useState("");

  // Generate form
  const [count, setCount] = useState(10);
  const [type, setType] = useState<"single" | "multi">("single");
  const [maxUses, setMaxUses] = useState(1);
  const [durationDays, setDurationDays] = useState(365);
  const [notes, setNotes] = useState("");

  async function load(p: number = page) {
    const params = new URLSearchParams({ page: String(p), pageSize: "20" });
    if (statusFilter) params.set("status", statusFilter);
    const data = await apiGet<{ items: CardKey[]; total: number; page: number }>("/card-keys?" + params.toString());
    setItems(data.items);
    setTotal(data.total);
    setPage(data.page);
  }

  useEffect(() => { load(); }, [statusFilter]);

  async function handleGenerate() {
    const result = await apiPost<GenerateResult>("/card-keys/generate", { count, type, maxUses, durationDays, notes });
    setGenerated(result);
    setShowGenerate(false);
    load();
  }

  async function handleRevoke(id: string) {
    if (!confirm("确定吊销此卡密？")) return;
    await apiPut("/card-keys/" + id, { status: "revoked" });
    load();
  }

  const statusColors: Record<string, string> = {
    unused: "bg-taozi-success/15 text-taozi-success",
    used: "bg-taozi-textHint/15 text-taozi-textHint",
    revoked: "bg-taozi-error/15 text-taozi-error",
  };

  const statusLabels: Record<string, string> = {
    unused: "未使用",
    used: "已使用",
    revoked: "已吊销",
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold text-taozi-textPrimary">卡密管理</h2>
        <button onClick={() => { setGenerated(null); setShowGenerate(true); }} className="flex items-center gap-2 rounded-[8px] bg-taozi-primary px-4 py-2 text-sm font-medium text-white hover:bg-taozi-primaryDark transition-colors">
          <Plus size={16} /> 生成卡密
        </button>
      </div>

      {generated && (
        <div className="mb-6 rounded-[12px] bg-taozi-success/10 p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-taozi-success">卡密生成成功</p>
              <p className="text-sm text-taozi-textSecondary">批次: {generated.batchId} · 共 {generated.count} 个</p>
            </div>
            <button onClick={() => setGenerated(null)} className="text-sm text-taozi-textSecondary hover:text-taozi-textPrimary">关闭</button>
          </div>
          <div className="mt-3 max-h-48 overflow-auto rounded-[8px] bg-taozi-bg p-3 font-mono text-xs text-taozi-textPrimary leading-relaxed">
            {generated.keys.map((k, i) => <div key={i}>{k}</div>)}
          </div>
          <p className="mt-2 text-xs text-taozi-error">复制并妥善保管以上卡密，关闭后将无法再次查看明文。</p>
        </div>
      )}

      {/* Filters */}
      <div className="mb-4 flex gap-2">
        {["", "unused", "used", "revoked"].map((s) => (
          <button key={s} onClick={() => setStatusFilter(s)} className={"rounded-full px-3 py-1 text-xs " + (statusFilter === s ? "bg-taozi-primary text-white" : "bg-taozi-card text-taozi-textSecondary hover:bg-taozi-surface")}>
            {s ? statusLabels[s] : "全部"}
          </button>
        ))}
      </div>

      <div className="rounded-[12px] bg-taozi-card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-taozi-surface">
              <th className="px-4 py-3 text-left text-taozi-textSecondary">卡密</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">类型</th>
              <th className="px-4 py-3 text-center text-taozi-textSecondary">状态</th>
              <th className="px-4 py-3 text-center text-taozi-textSecondary">用量</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">有效期</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">批次</th>
              <th className="px-4 py-3 text-right text-taozi-textSecondary">操作</th>
            </tr>
          </thead>
          <tbody>
            {items.map((k) => (
              <tr key={k.id} className="border-t border-taozi-surface hover:bg-white/[0.03]">
                <td className="px-4 py-3 font-mono text-xs text-taozi-textPrimary">{k.codePrefix}****-{k.codeLast4}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{k.type === "single" ? "单次" : "多次"}</td>
                <td className="px-4 py-3 text-center">
                  <span className={"inline-block rounded-full px-2 py-0.5 text-xs " + (statusColors[k.status] || "")}>{statusLabels[k.status] || k.status}</span>
                </td>
                <td className="px-4 py-3 text-center text-taozi-textSecondary">{k.currentUses}/{k.maxUses}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{k.durationDays}天</td>
                <td className="px-4 py-3 text-taozi-textSecondary font-mono text-xs">{k.batchId || "-"}</td>
                <td className="px-4 py-3 text-right">
                  {k.status === "unused" && (
                    <button onClick={() => handleRevoke(k.id)} className="rounded p-1.5 text-taozi-textHint hover:bg-taozi-surface hover:text-taozi-error transition-colors" title="吊销">
                      <Ban size={16} />
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-12 text-center text-taozi-textHint">暂无卡密</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {total > 20 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button onClick={() => load(page - 1)} disabled={page <= 1} className="rounded-lg px-3 py-1.5 text-sm text-taozi-textSecondary hover:bg-taozi-card disabled:opacity-30">上一页</button>
          <span className="text-sm text-taozi-textSecondary">{page} / {Math.ceil(total / 20)}</span>
          <button onClick={() => load(page + 1)} disabled={page >= Math.ceil(total / 20)} className="rounded-lg px-3 py-1.5 text-sm text-taozi-textSecondary hover:bg-taozi-card disabled:opacity-30">下一页</button>
        </div>
      )}

      {/* Generate Modal */}
      {showGenerate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setShowGenerate(false)}>
          <div className="w-full max-w-sm rounded-[16px] bg-taozi-card p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-4 text-lg font-bold text-taozi-textPrimary">生成卡密 (XTZ- 前缀)</h3>
            <div className="flex flex-col gap-3">
              <div>
                <label className="mb-1 block text-xs text-taozi-textSecondary">数量</label>
                <input type="number" value={count} onChange={(e) => setCount(Number(e.target.value))} min={1} max={500} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary focus:border-taozi-primary focus:outline-none" />
              </div>
              <div>
                <label className="mb-1 block text-xs text-taozi-textSecondary">类型</label>
                <select value={type} onChange={(e) => setType(e.target.value as "single" | "multi")} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary focus:border-taozi-primary focus:outline-none">
                  <option value="single">单次使用</option>
                  <option value="multi">多次使用</option>
                </select>
              </div>
              {type === "multi" && (
                <div>
                  <label className="mb-1 block text-xs text-taozi-textSecondary">最大使用次数</label>
                  <input type="number" value={maxUses} onChange={(e) => setMaxUses(Number(e.target.value))} min={1} max={9999} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary focus:border-taozi-primary focus:outline-none" />
                </div>
              )}
              <div>
                <label className="mb-1 block text-xs text-taozi-textSecondary">有效天数</label>
                <input type="number" value={durationDays} onChange={(e) => setDurationDays(Number(e.target.value))} min={1} max={9999} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary focus:border-taozi-primary focus:outline-none" />
              </div>
              <div>
                <label className="mb-1 block text-xs text-taozi-textSecondary">备注</label>
                <input value={notes} onChange={(e) => setNotes(e.target.value)} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none" />
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-3">
              <button onClick={() => setShowGenerate(false)} className="rounded-[8px] px-4 py-2 text-sm text-taozi-textSecondary hover:bg-taozi-surface transition-colors">取消</button>
              <button onClick={handleGenerate} className="rounded-[8px] bg-taozi-primary px-4 py-2 text-sm font-medium text-white hover:bg-taozi-primaryDark transition-colors">生成</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
