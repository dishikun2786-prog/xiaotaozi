import { NavLink } from "react-router-dom";
import { LayoutDashboard, Server, Key, Users, Shield } from "lucide-react";

const navItems = [
  { to: "/admin/dashboard", icon: LayoutDashboard, label: "概览" },
  { to: "/admin/providers", icon: Server, label: "VLM 配置" },
  { to: "/admin/card-keys", icon: Key, label: "卡密管理" },
  { to: "/admin/users", icon: Users, label: "用户管理" },
  { to: "/admin/admins", icon: Shield, label: "管理员" },
];

export default function Sidebar() {
  return (
    <aside className="w-56 flex-shrink-0 border-r border-taozi-card bg-taozi-bg">
      <div className="flex h-16 items-center gap-2 border-b border-taozi-card px-5">
        <span className="text-xl">🍑</span>
        <span className="text-lg font-bold text-taozi-textPrimary">小桃子</span>
      </div>
      <nav className="mt-4 flex flex-col gap-1 px-3">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors " +
              (isActive
                ? "bg-[#D6774426] text-taozi-primary"
                : "text-taozi-textSecondary hover:bg-taozi-card hover:text-taozi-textPrimary")
            }
          >
            <Icon size={18} />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
