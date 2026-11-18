export const getToken = () => sessionStorage.getItem('token')
export const setToken = (t) => (t ? sessionStorage.setItem('token', t) : sessionStorage.removeItem('token'))

export async function api(path, method = 'GET', body) {
  const token = getToken()
  const res = await fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw Object.assign(new Error(data.error || 'Something went wrong'), { status: res.status })
  return data
}

export const money = (cents) =>
  (cents / 100).toLocaleString('en-US', { style: 'currency', currency: 'USD' })
export const groups = (n = '') => n.replace(/(\d{4})(?=\d)/g, '$1 ')
