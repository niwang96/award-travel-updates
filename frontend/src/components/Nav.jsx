import { NavLink } from 'react-router-dom'

const navLinkClass = ({ isActive }) => `nav-link${isActive ? ' active' : ''}`

export default function Nav() {
  return (
    <nav className="nav">
      <NavLink to="/" end className={navLinkClass}>Home</NavLink>
      <NavLink to="/deals" className={navLinkClass}>Flight Deals</NavLink>
      <NavLink to="/news" className={navLinkClass}>Recent News</NavLink>
    </nav>
  )
}
