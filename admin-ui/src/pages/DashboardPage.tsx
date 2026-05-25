import { useEffect, useState } from "react";
import { apiGet } from "../lib/api";
import { Users, Key, Zap, Activity } from "lucide-react";

interface Stats {
  totalUsers: number;
  activeUsers: number;
  totalCardKeys: number;
  usedCardKeys: number;
  totalRequests: number;
  providerUsage: Record<string, { requests: number; tokens: number }>;
}

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats | null>(null);

  useEffect(() => {
    apiGet<Stats>("/stats/overview").then(setStats).catch(console.error);
  }, []);

  if (!stats) {
    return <div className="text-taozi-textSecondary">Loading...</div>;
  }

  const cards = [
    { label: "总用户数", value: stats.totalUsers, icon: Users, color: "text-taozi-primary" },
    { label: "活跃用户", value: stats.activeUsers, icon: Activity, color: "text-taozi-success" },
    { label: "卡密总数", value: stats.totalCardKeys, icon: Key, color: "text-taozi-secondary" },
    { label: "API 调用", value: stats.totalRequests, icon: Zap, color: "text-taozi-warning" },
  ];

  return (
    <div>
      <h2 className="mb-6 text-xl font-bold text-taozi-textPrimary">概览</h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="rounded-[12px] bg-taozi-card p-5 hover:border-l-2 hover:border-l-taozi-primary transition-all">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-taozi-textSecondary">{label}</p>
                <p className="mt-1 text-2xl font-bold text-taozi-textPrimary">{value.toLocaleString()}</p>
              </div>
              <Icon className={color} size={24} />
            </div>
          </div>
        ))}
      </div>
      {Object.keys(stats.providerUsage).length > 0 && (
        <div className="mt-8">
          <h3 className="mb-4 text-lg font-semibold text-taozi-textPrimary">服务商用量 (近30天)</h3>
          <div className="overflow-hidden rounded-[12px] bg-taozi-card">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-taozi-surface">
                  <th className="px-4 py-3 text-left text-taozi-textSecondary">服务商</th>
                  <th className="px-4 py-3 text-right text-taozi-textSecondary">请求数</th>
                  <th className="px-4 py-3 text-right text-taozi-textSecondary">Token 用量</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(stats.providerUsage).map(([id, u]) => (
                  <tr key={id} className="border-t border-taozi-surface hover:bg-white/[0.03]">
                    <td className="px-4 py-3 text-taozi-textPrimary">{id}</td>
                    <td className="px-4 py-3 text-right text-taozi-textSecondary">{u.requests.toLocaleString()}</td>
                    <td className="px-4 py-3 text-right text-taozi-textSecondary">{u.tokens.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
