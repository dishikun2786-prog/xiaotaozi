import { useNavigate } from "react-router-dom";
import { LogOut } from "lucide-react";
import { clearTokens } from "../../lib/api";

export default function Header() {
  const navigate = useNavigate();

  function handleLogout() {
    clearTokens();
    navigate("/admin/login");
  }

  return (
    <header className="flex h-16 items-center justify-between border-b border-taozi-card bg-taozi-bg px-6">
      <h1 className="text-sm font-medium text-taozi-textHint">小桃子 云端管理后台</h1>
      <button
        onClick={handleLogout}
        className="flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm text-taozi-textSecondary hover:bg-taozi-card hover:text-taozi-error transition-colors"
      >
        <LogOut size={16} />
        退出
      </button>
    </header>
  );
}
