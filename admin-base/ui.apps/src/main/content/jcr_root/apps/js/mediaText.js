const mediaTextKeys = [
  'alt',
  'altText',
  'title',
  'headline',
  'description',
  'dc:title',
  'dc:description',
  'dam:title',
  'dam:altText',
  'dam:description',
  'by-line_title',
  'caption_abstract',
  'image_description',
  'jcr:title',
  'jcr:description',
]
const mediaTextCache = new Map()

function isPlaceholderMediaName(name = '') {
  const fileName = String(name || '').trim()
  if (!fileName) return true

  const normalized = fileName.replace(/[-_]+/g, ' ').toLowerCase()
  if (/^\d+$/.test(normalized)) return true
  if (/^[0-9a-f]{32}$/i.test(fileName)) return true
  if (/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(fileName)) return true
  if (/^(image|media|img|dcim)\s*\d+$/.test(normalized)) return true
  if (/^(image|media|img|dcim)\s*[- ]\s*\d+$/.test(normalized)) return true
  if (/^whatsapp image \d{4}(?:[- ]\d{2}){2} at \d{2}\.\d{2}\.\d{2}$/.test(normalized)) return true
  if (/^\d+(?:_\d+){2,}(?:_[a-z])?$/i.test(fileName)) return true
  return false
}

function isUsableMediaText(text = '') {
  const value = String(text || '').trim()
  return !!value && !isPlaceholderMediaName(value)
}

export function isImagePath(path = '') {
  return /\.(jpg|jpeg|png|gif|svg|webp|avif)$/i.test(String(path || ''))
}

export function normalizeAssetPath(path = '') {
  const value = String(path || '').trim()
  if (!value) return ''
  const cleaned = value.split('#')[0].split('?')[0]
  const repoPath = cleaned.match(/\/(?:content|assets)\/.*$/)
  return repoPath ? repoPath[0] : cleaned
}

export function fallbackMediaText(path = '') {
  const normalizedPath = normalizeAssetPath(path)
  const name = normalizedPath.split('/').pop() || ''
  const fileName = name.replace(/\.[^.]+$/, '').trim()
  if (isPlaceholderMediaName(fileName)) return ''
  return fileName.replace(/[-_]+/g, ' ').trim()
}

export function findMediaText(node) {
  if (!node || typeof node !== 'object') return ''
  for (const key of mediaTextKeys) {
    const value = node[key]
    if (typeof value === 'string' && isUsableMediaText(value)) {
      return value.trim()
    }
  }
  for (const value of Object.values(node)) {
    const text = findMediaText(value)
    if (text) return text
  }
  return ''
}

export function findNodeFromPath(nodes, path) {
  if (!nodes || !path) return null
  if (Array.isArray(nodes)) {
    for (const node of nodes) {
      const found = findNodeFromPath(node, path)
      if (found) return found
    }
    return null
  }
  if (typeof nodes !== 'object') return null
  if (nodes.path === path) return nodes
  if (nodes.children) return findNodeFromPath(nodes.children, path)
  for (const value of Object.values(nodes)) {
    if (value && typeof value === 'object') {
      const found = findNodeFromPath(value, path)
      if (found) return found
    }
  }
  return null
}

export async function resolveMediaText(path = '', nodes = null, cache = mediaTextCache) {
  const normalizedPath = normalizeAssetPath(path)
  if (!normalizedPath) return ''

  const cached = cache && cache.get(normalizedPath)
  if (cached) return cached

  try {
    const response = await fetch(`${normalizedPath}.1.json`)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    const text = findMediaText(data)
    if (text) {
      if (cache) cache.set(normalizedPath, text)
      return text
    }
  } catch (e) {
    // Fall through to the browser tree and filename fallback below.
  }

  const node = findNodeFromPath(nodes, normalizedPath)
  if (node) {
    const text = findMediaText(node)
    if (text) {
      if (cache) cache.set(normalizedPath, text)
      return text
    }
  }

  return fallbackMediaText(normalizedPath)
}
