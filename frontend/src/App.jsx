import { useCallback, useEffect, useState } from 'react'
import { api, getToken, setToken, money, groups } from './api.js'

const LABELS = { deposit: 'Deposit', transfer_in: 'Received', transfer_out: 'Sent' }
const sign = (k) => (k === 'transfer_out' ? -1 : 1)

function BankCard({ me }) {
  return (
    <div className="card">
      <div className="card-top"><span className="brand">Lumen<b>Bank</b></span><span className="chip" /></div>
      <div className="card-balance"><small>Available balance</small><h2>{money(me.balance)}</h2></div>
      <div className="card-number">{groups(me.number)}</div>
      <div className="card-bottom"><span>Member since {me.createdAt.slice(0, 10)}</span><span className="visa">VISA</span></div>
    </div>
  )
}

function Chart({ history }) {
  const chrono = [...history].reverse()
  let run = 0
  const pts = [0, ...chrono.map((t) => (run += sign(t.kind) * t.amount))]
  if (pts.length < 2) return <p className="muted">Make a deposit to see your balance chart.</p>
  const W = 600, H = 160, max = Math.max(...pts, 1)
  const xy = pts.map((v, i) => [(i / (pts.length - 1)) * W, H - 10 - (v / max) * (H - 30)])
  const line = xy.map((p) => p.join(',')).join(' ')
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="chart" preserveAspectRatio="none">
      <defs>
        <linearGradient id="g" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="#7c5cff" stopOpacity=".5" /><stop offset="100%" stopColor="#7c5cff" stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={`0,${H} ${line} ${W},${H}`} fill="url(#g)" />
      <polyline points={line} fill="none" stroke="#a78bfa" strokeWidth="3" strokeLinejoin="round" />
    </svg>
  )
}

function Auth({ onLogin, toast }) {
  const [number, setNumber] = useState('')
  const [pin, setPin] = useState('')
  const [fresh, setFresh] = useState(null)

  const login = async (e, n = number, p = pin) => {
    e?.preventDefault()
    try { const { token } = await api('/login', 'POST', { number: n.replace(/\s/g, ''), pin: p }); setToken(token); onLogin() }
    catch (err) { toast(err.message, true) }
  }
  const register = async () => {
    try { setFresh(await api('/register', 'POST')) } catch (err) { toast(err.message, true) }
  }

  return (
    <div className="auth glass">
      <h1>Lumen<b>Bank</b></h1>
      <p className="muted">Modern banking demo · Java + React</p>
      {fresh ? (
        <div className="fresh">
          <p>Your card is ready. Save the PIN — it is shown only once.</p>
          <div className="card-number">{groups(fresh.number)}</div>
          <div className="pin">PIN <b>{fresh.pin}</b></div>
          <button onClick={() => login(null, fresh.number, fresh.pin)}>Enter my account</button>
        </div>
      ) : (
        <form onSubmit={login}>
          <input placeholder="Card number" inputMode="numeric" value={number} onChange={(e) => setNumber(e.target.value)} />
          <input placeholder="PIN" type="password" maxLength={4} value={pin} onChange={(e) => setPin(e.target.value)} />
          <button type="submit">Log in</button>
          <button type="button" className="ghost" onClick={register}>Open a new account</button>
        </form>
      )}
    </div>
  )
}

function Dashboard({ onLogout, toast }) {
  const [me, setMe] = useState(null)
  const [amount, setAmount] = useState('')
  const [to, setTo] = useState('')
  const [tAmount, setTAmount] = useState('')

  const load = useCallback(async () => {
    try { setMe(await api('/me')) } catch (e) { if (e.status === 401) onLogout(); else toast(e.message, true) }
  }, [onLogout, toast])
  useEffect(() => { load() }, [load])

  const run = (fn, ok) => async (e) => {
    e.preventDefault()
    try { await fn(); toast(ok); await load() } catch (err) { toast(err.message, true) }
  }
  const deposit = run(async () => { await api('/deposit', 'POST', { amount }); setAmount('') }, 'Income was added!')
  const transfer = run(async () => {
    await api('/transfer', 'POST', { to: to.replace(/\s/g, ''), amount: tAmount }); setTo(''); setTAmount('')
  }, 'Transfer successful!')
  const close = async () => {
    if (!confirm('Close this account? This cannot be undone.')) return
    try { await api('/account', 'DELETE'); onLogout() } catch (err) { toast(err.message, true) }
  }
  if (!me) return <p className="muted center">Loading…</p>

  return (
    <div className="grid">
      <header>
        <h1>Lumen<b>Bank</b></h1>
        <div><button className="ghost" onClick={onLogout}>Log out</button><button className="danger" onClick={close}>Close account</button></div>
      </header>
      <BankCard me={me} />
      <section className="glass">
        <h3>Balance over time</h3>
        <Chart history={me.history} />
      </section>
      <section className="glass">
        <h3>Add income</h3>
        <form onSubmit={deposit} className="row">
          <input placeholder="Amount, e.g. 250.00" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <button>Deposit</button>
        </form>
        <h3>Transfer</h3>
        <form onSubmit={transfer}>
          <input placeholder="Recipient card number" inputMode="numeric" value={to} onChange={(e) => setTo(e.target.value)} />
          <div className="row">
            <input placeholder="Amount" inputMode="decimal" value={tAmount} onChange={(e) => setTAmount(e.target.value)} />
            <button>Send</button>
          </div>
        </form>
      </section>
      <section className="glass wide">
        <h3>Recent activity</h3>
        {me.history.length === 0 && <p className="muted">No operations yet.</p>}
        <ul className="tx">
          {me.history.map((t, i) => (
            <li key={i}>
              <span className={'dot ' + (sign(t.kind) > 0 ? 'in' : 'out')}>{sign(t.kind) > 0 ? '↓' : '↑'}</span>
              <div><b>{LABELS[t.kind]}</b><small>{t.counterparty ? groups(t.counterparty) : 'Cash'} · {t.at}</small></div>
              <strong className={sign(t.kind) > 0 ? 'in' : 'out'}>{sign(t.kind) > 0 ? '+' : '−'}{money(t.amount)}</strong>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

export default function App() {
  const [authed, setAuthed] = useState(!!getToken())
  const [msg, setMsg] = useState(null)
  const toast = useCallback((text, error = false) => {
    setMsg({ text, error }); setTimeout(() => setMsg(null), 3200)
  }, [])
  const logout = useCallback(() => { setToken(null); setAuthed(false) }, [])

  return (
    <main>
      {authed ? <Dashboard onLogout={logout} toast={toast} /> : <Auth onLogin={() => setAuthed(true)} toast={toast} />}
      {msg && <div className={'toast ' + (msg.error ? 'err' : '')}>{msg.text}</div>}
    </main>
  )
}
