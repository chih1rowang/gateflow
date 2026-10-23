// 管理口令：右上角输入一次，存 localStorage；所有面板请求经 adminFetch 附带 X-Admin-Token 请求头。
// 对应后端：gateway/workers 的 AdminTokenFilter 与 nginx 对 /conductor/ 的口令校验（2026-10 审查报告修复）。
const KEY = 'gf_admin_token'

export const getToken = () => localStorage.getItem(KEY) || ''
export const setToken = t => localStorage.setItem(KEY, t)

export async function adminFetch(url, opts = {}) {
  const r = await fetch(url, { ...opts, headers: { ...opts.headers, 'X-Admin-Token': getToken() } })
  if (r.status === 401) {
    throw new Error('管理员口令缺失或错误（右上角"口令"处填写）')
  }
  return r
}
