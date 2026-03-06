import { useFetch } from '../hooks/useFetch'
import { API } from '../api'

export default function FlightDeals() {
  const { data: deals, loading, error } = useFetch(API.emailDeals)

  if (loading) return <p className="empty">Loading…</p>
  if (error) return <p className="error">{error}</p>

  return deals.length === 0
    ? <p className="empty">No deals found.</p>
    : <ul className="item-list">
        {deals.map((deal, i) => (
          <li key={i} className="item">
            {deal.source
              ? <a href={deal.source} target="_blank" rel="noopener noreferrer">{deal.text}</a>
              : deal.text}
          </li>
        ))}
      </ul>
}
