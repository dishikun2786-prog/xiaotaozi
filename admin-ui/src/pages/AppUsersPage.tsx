import { useEffect, useState } from "react";
import { apiGet } from "../lib/api";
import { CheckCircle, XCircle } from "lucide-react";

interface AppUser {
  id: string;
  username: string;
  email: string | null;
  isActivated: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  _count: { usageRecords: number };
}

export default function AppUsersPage() {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);

  async function load(p: number) {
    const data = await apiGet<{ items: AppUser[]; total: number; page: number }>("/users?page=" + p + "&pageSize=20");
    setUsers(data.items);
    setTotal(data.total);
    setPage(data.page);
  }

  useEffect(() => { load(1); }, []);

  return (
    <div>
      <h2 className="mb-6 text-xl font-bold text-taozi-textPrimary">用户管理</h2>

      <div className="rounded-[12px] bg-taozi-card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-taozi-surface">
              <th className="px-4 py-3 text-left text-taozi-textSecondary">用户名</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">邮箱</th>
              <th className="px-4 py-3 text-center text-taozi-textSecondary">激活状态</th>
              <th className="px-4 py-3 text-center text-taozi-textSecondary">API 调用</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">注册时间</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">最后登录</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-t border-taozi-surface hover:bg-white/[0.03]">
                <td className="px-4 py-3 text-taozi-textPrimary">{u.username}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{u.email || "-"}</td>
                <td className="px-4 py-3 text-center">
                  <span className={"inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs " + (u.isActivated ? "bg-taozi-success/15 text-taozi-success" : "bg-taozi-error/15 text-taozi-error")}>
                    {u.isActivated ? <CheckCircle size={12} /> : <XCircle size={12} />}
                    {u.isActivated ? "已激活" : "未激活"}
                  </span>
                </td>
                <td className="px-4 py-3 text-center text-taozi-textSecondary">{u._count.usageRecords}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{new Date(u.createdAt).toLocaleDateString("zh-CN")}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString("zh-CN") : "-"}</td>
              </tr>
            ))}
            {users.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-12 text-center text-taozi-textHint">暂无用户</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {total > 20 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button onClick={() => load(page - 1)} disabled={page <= 1} className="rounded-lg px-3 py-1.5 text-sm text-taozi-textSecondary hover:bg-taozi-card disabled:opacity-30">上一页</button>
          <span className="text-sm text-taozi-textSecondary">{page} / {Math.ceil(total / 20)}</span>
          <button onClick={() => load(page + 1)} disabled={page >= Math.ceil(total / 20)} className="rounded-lg px-3 py-1.5 text-sm text-taozi-textSecondary hover:bg-taozi-card disabled:opacity-30">下一页</button>
        </div>
      )}
    </div>
  );
}
