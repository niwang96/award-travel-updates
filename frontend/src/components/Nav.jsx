import { NavLink } from 'react-router-dom'

export default function Nav() {
  return (
    <nav className="nav">
      <NavLink to="/" end className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>Home</NavLink>
      <NavLink to="/deals" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>Flight Deals</NavLink>
      <NavLink to="/news" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>Recent News</NavLink>
    </nav>
  )
}
