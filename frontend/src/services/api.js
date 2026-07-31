export async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const value = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(value.message || `HTTP ${response.status}`);
  }
  return value;
}

export async function requestText(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.text();
}

export function sendJson(url, method, payload) {
  return requestJson(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}
