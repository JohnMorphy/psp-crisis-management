import { ReactElement } from "react"
import { GisMapApp } from "@psp/shared"

interface GisMapContainerProps {
  springBaseUrl: string
  initialZoom?: number
}

export function GisMap({ springBaseUrl, initialZoom }: GisMapContainerProps): ReactElement {
  return (
    <GisMapApp
      apiBaseUrl={springBaseUrl}
      initialZoom={initialZoom}
    />
  )
}
