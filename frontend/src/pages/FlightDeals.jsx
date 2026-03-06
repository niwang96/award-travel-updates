import { useState, useEffect } from 'react'

export default function FlightDeals() {
  const [deals, setDeals] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    async function fetchDeals() {
      try {
        const res = await fetch('/api/email-deals')
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.json()
        setDeals(data)
      } catch (e) {
        setError('Failed to load deals. Please try again.')
      } finally {
        setLoading(false)
      }
    }
    fetchDeals()
  }, [])

  if (loading) return <p className="empty">Loading…</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div className="container">
      {deals.length === 0
        ? <p className="empty">No deals found.</p>
        : <ul className="deals-list">
            {deals.map((deal, i) => (
              <li key={i} className="deal-item">
                {deal.source
                  ? <a href={deal.source} target="_blank" rel="noopener noreferrer">{deal.text}</a>
                  : deal.text}
              </li>
            ))}
          </ul>
      }
    </div>
  )
}
