import cover from './assets/cover.png'
import Header from './Header'

function AppShell() {

  return (
    <>
		<Header />
		<div className="flex h-screen">
			<div>{/* mapa */}</div>
			<div>{/* panel */}</div>

			<div className="flex flex-col items-center gap-6 p-7 md:flex-row md:gap-8 rounded-2xl">
        <div>
        <img className="size-48 shadow-xl rounded-md" alt="" src={cover} />
        </div>
        <div className="flex flex-col items-center md:items-start">
            <span className="text-2xl font-medium">Class Warfare</span>
            <span className="font-medium text-sky-500">The Anti-Patterns</span>
            <div className="flex gap-2 font-medium text-gray-600 dark:text-gray-400">
              <span>No. 4</span>
              <span>·</span>
              <span>2025</span>
            </div>
          </div>
        </div>
		</div>
    
    </>
  )
}

export default AppShell
