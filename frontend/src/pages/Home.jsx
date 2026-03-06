import { Link } from 'react-router-dom'

export default function Home() {
  return (
    <div className="home">
      <h1 className="home-title">Award Travel Updates</h1>
      <p className="home-desc">Track award flight deals and frequent flyer news in one place.</p>
      <div className="home-actions">
        <Link to="/deals" className="cta-button">Flight Deals</Link>
        <Link to="/news" className="cta-button secondary">Recent News</Link>
      </div>
    </div>
  )
}
