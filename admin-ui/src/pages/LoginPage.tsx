import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { setTokens } from "../lib/api";

export default function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const resp = await fetch("/api/v1/admin/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const data = await resp.json();
      if (!resp.ok) { setError(data.error || "Login failed"); return; }
      setTokens(data.accessToken, data.refreshToken);
      navigate("/admin/dashboard");
    } catch {
      setError("Network error");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-taozi-bg">
      <div className="w-full max-w-sm rounded-[12px] bg-taozi-card p-8">
        <div className="mb-8 text-center">
          <span className="text-3xl">🍑</span>
          <h1 className="mt-2 text-xl font-bold text-taozi-textPrimary">小桃子 管理后台</h1>
        </div>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <input
              type="text"
              placeholder="用户名"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-4 py-2.5 text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none"
              autoFocus
            />
          </div>
          <div>
            <input
              type="password"
              placeholder="密码"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-[8px] border border-taozi-surface bg-taozi-input px-4 py-2.5 text-taozi-textPrimary placeholder:text-taozi-textHint focus:border-taozi-primary focus:outline-none"
            />
          </div>
          {error && <p className="text-sm text-taozi-error">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="rounded-[8px] bg-taozi-primary py-2.5 font-medium text-white hover:bg-taozi-primaryDark disabled:opacity-50 transition-colors"
          >
            {loading ? "登录中..." : "登录"}
          </button>
        </form>
      </div>
    </div>
  );
}
