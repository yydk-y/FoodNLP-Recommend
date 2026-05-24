const getImagePrefix = () => {
  try {
    return import.meta.env?.DEV ? '/api' : ''
  } catch (_) {
    return ''
  }
}

export const resolveImageUrl = (url) => {
  if (!url) return ''

  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) {
    return url
  }

  if (url.startsWith('/api/')) {
    return url
  }

  const prefix = getImagePrefix()
  if (url.startsWith('/')) {
    return `${prefix}${url}`
  }

  return `${prefix}/${url}`
}
