import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { GisMapApp } from '@psp/shared'
import './index.css'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <GisMapApp apiBaseUrl={apiBaseUrl} />
  </StrictMode>,
)
