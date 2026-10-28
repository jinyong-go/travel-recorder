import { useCallback } from 'react'
import { fetchTrip } from '../api/trips.js'
import useResource from './useResource.js'

/** 여행 하나를 읽는다 */
export default function useTrip(tripId) {
  const load = useCallback(() => fetchTrip(tripId), [tripId])
  const { data: trip, status, setData: setTrip } = useResource(load, Boolean(tripId))
  return { trip, status, setTrip }
}
