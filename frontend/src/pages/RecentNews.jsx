import { useState, useEffect } from 'react'

const SECTIONS = [
  { key: 'doctorofcredit', label: 'Doctor of Credit', type: 'blog' },
  { key: 'frequentmiler', label: 'Frequent Miler', type: 'blog' },
  { key: 'awardtravel', label: 'r/awardtravel', type: 'subreddit' },
  { key: 'churning', label: 'r/churning', type: 'subreddit' },
]

export default function RecentNews() {
  const [data, setData] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    async function fetchAll() {
      try {
        const [blogRes, subRes] = await Promise.all([
          fetch('/api/blog-summaries'),
          fetch('/api/subreddit-summaries'),
        ])
        if (!blogRes.ok) throw new Error(`Blog summaries: HTTP ${blogRes.status}`)
        if (!subRes.ok) throw new Error(`Subreddit summaries: HTTP ${subRes.status}`)
        const [blog, sub] = await Promise.all([blogRes.json(), subRes.json()])
        setData({ ...blog, ...sub })
      } catch (e) {
        setError(e.message || 'Failed to load news.')
      } finally {
        setLoading(false)
      }
    }
    fetchAll()
  }, [])

  if (loading) return <p className="empty">Loading news…</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div className="container">
      {SECTIONS.map(({ key, label }) => {
        const section = data[key]
        if (!section) return null
        return (
          <div key={key} className="news-section">
            <h2 className="news-section-title">
              {label}
              {section.stale && <span className="stale-badge">stale</span>}
            </h2>
            {section.updates && section.updates.length > 0
              ? <ul className="deals-list">
                  {section.updates.map((update, i) => (
                    <li key={i} className="deal-item">
                      {update.source
                        ? <a href={update.source} target="_blank" rel="noopener noreferrer">{update.text}</a>
                        : update.text}
                    </li>
                  ))}
                </ul>
              : <p className="empty">No updates.</p>
            }
          </div>
        )
      })}
    </div>
  )
}
