import { useState } from 'react'
import './App.css'

function App() {
  const [deals, setDeals] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function fetchDeals() {
    setLoading(true)
    setError(null)
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

  return (
    <div className="container">
      <button className="cta-button" onClick={fetchDeals} disabled={loading}>
        {loading ? 'Loading…' : 'GIVE ME AWARD FLIGHT DEALS'}
      </button>

      {error && <p className="error">{error}</p>}

      {deals !== null && (
        deals.length === 0
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
      )}
    </div>
  )
}

export default App
