import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './App.css'
import Nav from './components/Nav'
import Home from './pages/Home'
import FlightDeals from './pages/FlightDeals'
import RecentNews from './pages/RecentNews'

function App() {
  return (
    <BrowserRouter>
      <Nav />
      <div className="page-content">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/deals" element={<FlightDeals />} />
          <Route path="/news" element={<RecentNews />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
