import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut, apiDelete } from "../lib/api";
import { Plus, Trash2, Zap, CheckCircle, XCircle } from "lucide-react";

interface Provider {
  id: string;
  name: string;
  providerId: string;
  apiKey: string;
  baseUrl: string;
  defaultModel: string;
  models: string[];
  isGuiAgent: boolean;
  isEnabled: boolean;
  priority: number;
}

export default function ProvidersPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Provider | null>(null);
  const [testing, setTesting] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<{ success: boolean; models?: string[]; error?: string } | null>(null);

  const [name, setName] = useState("");
  const [providerId, setProviderId] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [defaultModel, setDefaultModel] = useState("");
  const [isGuiAgent, setIsGuiAgent] = useState(false);

  async function load() {
    const data = await apiGet<Provider[]>("/providers");
    setProviders(data);
  }

  useEffect(() => { load(); }, []);

  function openNew() {
    setEditing(null); setName(""); setProviderId(""); setApiKey(""); setBaseUrl(""); setDefaultModel(""); setIsGuiAgent(false);
    setShowForm(true);
  }

  function openEdit(p: Provider) {
    setEditing(p); setName(p.name); setProviderId(p.providerId); setApiKey(""); setBaseUrl(p.baseUrl);
    setDefaultModel(p.defaultModel); setIsGuiAgent(p.isGuiAgent);
    setShowForm(true);
  }

  async function handleSave() {
    const body = { name, providerId, baseUrl, defaultModel, models: [], isGuiAgent };
    const bodyWithKey = { ...body, apiKey };

    if (editing) {
      // Only send apiKey if user entered a new one
      await apiPut("/providers/" + editing.id, apiKey ? bodyWithKey : body);
    } else {
      if (!apiKey) { alert("API Key is required"); return; }
      await apiPost("/providers", bodyWithKey);
    }
    setShowForm(false);
    load();
  }

  async function handleDelete(id: string) {
    if (!confirm("确定删除此提供商？")) return;
    await apiDelete("/providers/" + id);
    load();
  }

  async function handleTest(id: string) {
    setTesting(id);
    setTestResult(null);
    try {
      const result = await apiPost<{ success: boolean; models?: string[]; error?: string }>("/providers/" + id + "/test");
      setTestResult(result);
    } catch (e: unknown) {
      setTestResult({ success: false, error: (e as Error).message });
    }
    setTesting(null);
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold text-taozi-textPrimary">VLM 提供商配置</h2>
        <button onClick={openNew} className="flex items-center gap-2 rounded-[8px] bg-taozi-primary px-4 py-2 text-sm font-medium text-white hover:bg-taozi-primaryDark transition-colors">
          <Plus size={16} /> 添加提供商
        </button>
      </div>

      <div className="rounded-[12px] bg-taozi-card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-taozi-surface">
              <th className="px-4 py-3 text-left text-taozi-textSecondary">名称</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">ID</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">API Key</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">模型</th>
              <th className="px-4 py-3 text-center text-taozi-textSecondary">状态</th>
              <th className="px-4 py-3 text-right text-taozi-textSecondary">操作</th>
            </tr>
          </thead>
          <tbody>
            {providers.map((p) => (
              <tr key={p.id} className="border-t border-taozi-surface hover:bg-white/[0.03]">
                <td className="px-4 py-3 text-taozi-textPrimary">{p.name}</td>
                <td className="px-4 py-3 text-taozi-textSecondary font-mono text-xs">{p.providerId}</td>
                <td className="px-4 py-3 text-taozi-textSecondary font-mono text-xs">{p.apiKey}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{p.defaultModel}</td>
                <td className="px-4 py-3 text-center">
                  <span className={"inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs " + (p.isEnabled ? "bg-taozi-success/15 text-taozi-success" : "bg-taozi-error/15 text-taozi-error")}>
                    {p.isEnabled ? <CheckCircle size={12} /> : <XCircle size={12} />}
                    {p.isEnabled ? "启用" : "禁用"}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex items-center justify-end gap-2">
                    <button onClick={() => handleTest(p.id)} disabled={testing === p.id} className="rounded p-1.5 text-taozi-textHint hover:bg-taozi-surface hover:text-taozi-primary transition-colors" title="测试连接">
                      <Zap size={16} />
                    </button>
                    <button onClick={() => openEdit(p)} className="rounded p-1.5 text-taozi-textHint hover:bg-taozi-surface hover:text-taozi-primary transition-colors" title="编辑">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                    </button>
                    <button onClick={() => handleDelete(p.id)} className="rounded p-1.5 text-taozi-textHint hover:bg-taozi-surface hover:text-taozi-error transition-colors" title="删除">
                      <Trash2 size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {providers.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-12 text-center text-taozi-textHint">暂无提供商配置</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {testResult && (
        <div className={"mt-4 rounded-[12px] p-4 " + (testResult.success ? "bg-taozi-success/10" : "bg-taozi-error/10")}>
          <p className={"font-medium " + (testResult.success ? "text-taozi-success" : "text-taozi-error")}>
            {testResult.success ? "连接成功" : "连接失败"}
          </p>
          {testResult.models && testResult.models.length > 0 && (
            <p className="mt-1 text-sm text-taozi-textSecondary">可用模型: {testResult.models.join(", ")}</p>
          )}
          {testResult.error && <p className="mt-1 text-sm text-taozi-error">{testResult.error}</p>}
        </div>
      )}

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setShowForm(false)}>
          <div className="w-full max-w-md rounded-[16px] bg-taozi-card p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-4 text-lg font-bold text-taozi-textPrimary">{editing ? "编辑提供商" : "添加提供商"}</h3>
            <div className="flex flex-col gap-3">
              <input placeholder="名称" value={name} onChange={(e) => setName(e.target.value)} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none" />
              <input placeholder="Provider ID (如 aliyun)" value={providerId} onChange={(e) => setProviderId(e.target.value)} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none" />
              <input placeholder="API Key" type="password" value={apiKey} onChange={(e) => setApiKey(e.target.value)} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none" />
              <input placeholder="Base URL" value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none" />
              <input placeholder="默认模型" value={defaultModel} onChange={(e) => setDefaultModel(e.target.value)} className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-3 py-2 text-sm text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none" />
              <label className="flex items-center gap-2 text-sm text-taozi-textSecondary">
                <input type="checkbox" checked={isGuiAgent} onChange={(e) => setIsGuiAgent(e.target.checked)} className="rounded accent-taozi-primary" />
                GUI Agent 协议
              </label>
            </div>
            <div className="mt-4 flex justify-end gap-3">
              <button onClick={() => setShowForm(false)} className="rounded-[8px] px-4 py-2 text-sm text-taozi-textSecondary hover:bg-taozi-surface transition-colors">取消</button>
              <button onClick={handleSave} className="rounded-[8px] bg-taozi-primary px-4 py-2 text-sm font-medium text-white hover:bg-taozi-primaryDark transition-colors">保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
