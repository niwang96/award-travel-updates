import { useState } from 'react'
import { useFetch } from '../hooks/useFetch'
import { API } from '../api'

const COLUMNS = [
  { key: 'points',         label: 'Points Cost',     type: 'numeric' },
  { key: 'airline',        label: 'Airline',          type: 'string'  },
  { key: 'cabin',          label: 'Class',            type: 'string'  },
  { key: 'origin',         label: 'From',             type: 'string'  },
  { key: 'destination',    label: 'To',               type: 'string'  },
  { key: 'flightDate',     label: 'Date',             type: 'date'    },
  { key: 'dateFound',      label: 'Date Found',       type: 'numeric' },
  { key: 'bookingProgram', label: 'Booking Program',  type: 'string'  },
]

function parseFlightDate(str) {
  // Expects M/DD/YYYY
  const [m, d, y] = str.split('/')
  return new Date(+y, +m - 1, +d)
}

function sortDeals(deals, col, dir) {
  return [...deals].sort((a, b) => {
    const colDef = COLUMNS.find(c => c.key === col)
    let av = a[col], bv = b[col]
    let cmp
    if (colDef.type === 'numeric') {
      cmp = av - bv
    } else if (colDef.type === 'date') {
      cmp = parseFlightDate(av) - parseFlightDate(bv)
    } else {
      cmp = (av ?? '').localeCompare(bv ?? '')
    }
    return dir === 'asc' ? cmp : -cmp
  })
}

export default function FlightDeals() {
  const { data: deals, loading, error } = useFetch(API.emailDeals)
  const [sort, setSort] = useState({ col: 'points', dir: 'asc' })

  if (loading) return <p className="empty">Loading…</p>
  if (error) return <p className="error">{error}</p>
  if (deals.length === 0) return <p className="empty">No deals found.</p>

  function handleSort(key) {
    setSort(prev =>
      prev.col === key
        ? { col: key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { col: key, dir: 'asc' }
    )
  }

  const sorted = sortDeals(deals, sort.col, sort.dir)

  return (
    <table className="deals-table">
      <thead>
        <tr>
          {COLUMNS.map(({ key, label }) => (
            <th
              key={key}
              className={sort.col === key ? 'active' : ''}
              onClick={() => handleSort(key)}
            >
              {label}{sort.col === key ? (sort.dir === 'asc' ? ' ▲' : ' ▼') : ''}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {sorted.map((deal, i) => (
          <tr key={i}>
            <td>
              {deal.source
                ? <a href={deal.source} target="_blank" rel="noopener noreferrer">{deal.points.toLocaleString()}</a>
                : deal.points.toLocaleString()}
            </td>
            <td>{deal.airline}</td>
            <td>{deal.cabin}</td>
            <td>{deal.origin}</td>
            <td>{deal.destination}</td>
            <td>{deal.flightDate}</td>
            <td>{new Date(deal.dateFound * 1000).toLocaleDateString('en-US', { month: '2-digit', day: '2-digit', year: 'numeric' })}</td>
            <td>{deal.bookingProgram}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
