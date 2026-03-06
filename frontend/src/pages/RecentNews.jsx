import { useState, useEffect } from 'react'
import { API } from '../api'

const TOPICS = [
  { key: 'credit_cards', label: 'Credit Cards' },
  { key: 'flights',      label: 'Flights' },
  { key: 'hotels',       label: 'Hotels' },
  { key: 'lounges',      label: 'Lounges' },
  { key: 'status',       label: 'Status & Elite' },
  { key: 'deals',        label: 'Deals' },
]

export default function RecentNews() {
  const [data, setData] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    async function fetchAll() {
      try {
        const res = await fetch(API.combinedSummaries, { signal })
        if (!res.ok) throw new Error(`Combined summaries: HTTP ${res.status}`)
        setData(await res.json())
        setLoading(false)
      } catch (e) {
        if (e.name !== 'AbortError') {
          setError(e.message || 'Failed to load news.')
          setLoading(false)
        }
      }
    }

    fetchAll()
    return () => controller.abort()
  }, [])

  if (loading) return <p className="empty">Loading news…</p>
  if (error) return <p className="error">{error}</p>

  // Flatten all updates from all sources, tagging each with whether its source is stale
  const allUpdates = Object.values(data).flatMap(section =>
    (section.updates ?? []).map(update => ({ ...update, stale: section.stale }))
  )

  // Group by topic
  const byTopic = Object.fromEntries(
    TOPICS.map(({ key }) => [key, allUpdates.filter(u => u.topic === key)])
  )

  const anyWithTopic = TOPICS.some(({ key }) => byTopic[key].length > 0)
  if (!anyWithTopic) return <p className="empty">No updates.</p>

  return (
    <>
      <h1>Recent Award Travel and Credit Card News from Blogs and Reddit</h1>
      {TOPICS.map(({ key, label }) => {
        const updates = byTopic[key]
        if (updates.length === 0) return null
        const stale = updates.some(u => u.stale)
        return (
          <div key={key} className="news-section">
            <h2 className="news-section-title">
              {label}
              {stale && <span className="stale-badge">stale</span>}
            </h2>
            <ul className="item-list">
              {updates.map((update, i) => (
                <li key={i} className="item">
                  {update.source
                    ? <a href={update.source} target="_blank" rel="noopener noreferrer">{update.text}</a>
                    : update.text}
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </>
  )
}
