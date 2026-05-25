import { useEffect, useState } from "react";
import { apiGet } from "../lib/api";
import { Shield, UserCog } from "lucide-react";

interface AdminUser {
  id: string;
  username: string;
  displayName: string;
  role: string;
  createdAt: string;
}

export default function AdminUsersPage() {
  const [admins, setAdmins] = useState<AdminUser[]>([]);

  useEffect(() => {
    // For now just show current user info
    apiGet<AdminUser>("/auth/me")
      .then((me) => setAdmins([me]))
      .catch(console.error);
  }, []);

  return (
    <div>
      <h2 className="mb-6 text-xl font-bold text-taozi-textPrimary">管理员</h2>

      <div className="rounded-[12px] bg-taozi-card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-taozi-surface">
              <th className="px-4 py-3 text-left text-taozi-textSecondary">用户名</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">显示名</th>
              <th className="px-4 py-3 text-center text-taozi-textSecondary">角色</th>
              <th className="px-4 py-3 text-left text-taozi-textSecondary">创建时间</th>
            </tr>
          </thead>
          <tbody>
            {admins.map((a) => (
              <tr key={a.id} className="border-t border-taozi-surface hover:bg-white/[0.03]">
                <td className="px-4 py-3 text-taozi-textPrimary">{a.username}</td>
                <td className="px-4 py-3 text-taozi-textSecondary">{a.displayName}</td>
                <td className="px-4 py-3 text-center">
                  <span className={"inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs " + (a.role === "superadmin" ? "bg-taozi-primary/15 text-taozi-primary" : "bg-taozi-textHint/15 text-taozi-textSecondary")}>
                    {a.role === "superadmin" ? <Shield size={12} /> : <UserCog size={12} />}
                    {a.role === "superadmin" ? "超级管理员" : "管理员"}
                  </span>
                </td>
                <td className="px-4 py-3 text-taozi-textSecondary">{new Date(a.createdAt).toLocaleDateString("zh-CN")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
