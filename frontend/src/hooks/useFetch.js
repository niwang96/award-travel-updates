import { useState, useEffect } from 'react'

export function useFetch(url) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch(url, { signal: controller.signal })
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(data => {
        setData(data)
        setLoading(false)
      })
      .catch(e => {
        if (e.name !== 'AbortError') {
          setError('Failed to load. Please try again.')
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [url])

  return { data, loading, error }
}
