function Header() {
  return (
    <header className="flex items-center justify-between px-4 py-2 bg-gray-800 text-white border-b border-gray-700 shrink-0">
      <div className="flex items-center gap-3">
        <span className="text-blue-400 font-bold text-lg">PSP</span>
        <span className="font-semibold text-sm">Inteligentna Mapa Woj. Lubelskiego</span>
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm text-gray-400">Status: gotowy</span>
        <button
          className="p-2 rounded-full hover:bg-gray-700 transition-colors"
          title="Asystent głosowy"
          aria-label="Asystent głosowy"
        >
          🎤
        </button>
      </div>
    </header>
  )
}

export default Header
