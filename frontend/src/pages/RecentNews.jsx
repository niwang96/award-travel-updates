import { useState, useEffect } from 'react'
import { API } from '../api'

const SECTIONS = [
  { key: 'doctorofcredit', label: 'Doctor of Credit' },
  { key: 'frequentmiler', label: 'Frequent Miler' },
  { key: 'awardtravel', label: 'r/awardtravel' },
  { key: 'churning', label: 'r/churning' },
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

  return (
    <>
      {SECTIONS.map(({ key, label }) => {
        const section = data[key]
        if (!section) return null
        return (
          <div key={key} className="news-section">
            <h2 className="news-section-title">
              {label}
              {section.stale && <span className="stale-badge">stale</span>}
            </h2>
            {section.updates?.length > 0
              ? <ul className="item-list">
                  {section.updates.map((update, i) => (
                    <li key={i} className="item">
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
    </>
  )
}
