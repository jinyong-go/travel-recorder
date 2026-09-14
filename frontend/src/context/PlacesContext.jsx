import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { MOCK_PLACES } from '../data/places.js'

const PlacesContext = createContext(null)

export function PlacesProvider({ children }) {
  const [places, setPlaces] = useState(MOCK_PLACES)

  const addPlace = useCallback((place) => {
    setPlaces((prev) => [place, ...prev])
  }, [])

  const value = useMemo(() => ({ places, addPlace }), [places, addPlace])

  return <PlacesContext.Provider value={value}>{children}</PlacesContext.Provider>
}

export function usePlaces() {
  const ctx = useContext(PlacesContext)
  if (!ctx) throw new Error('usePlaces must be used within a PlacesProvider')
  return ctx
}
