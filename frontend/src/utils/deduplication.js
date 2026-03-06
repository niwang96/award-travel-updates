const STOP_WORDS = new Set(['the','a','an','is','in','on','to','and','or','of','for','with','from','by','at','it','its','that','this','as','are','was','be','has','have','had','will','which'])

function tokenize(text) {
  return new Set(
    text.toLowerCase().match(/[a-z]+/g)?.filter(w => !STOP_WORDS.has(w)) ?? []
  )
}

function jaccard(setA, setB) {
  const intersection = [...setA].filter(w => setB.has(w)).length
  const union = new Set([...setA, ...setB]).size
  return union === 0 ? 0 : intersection / union
}

const SIMILARITY_THRESHOLD = 0.4
const PRIORITY = ['doctorofcredit', 'frequentmiler', 'awardtravel', 'churning']

/**
 * Given the merged data object { [sourceKey]: SummaryResult },
 * removes updates from lower-priority sections that are too similar
 * to an update already kept from a higher-priority section.
 * Returns a new data object (does not mutate input).
 */
export function deduplicateData(data) {
  const seen = []  // { tokens: Set, sourceKey }
  const result = {}

  for (const key of PRIORITY) {
    if (!data[key]) continue
    const kept = []
    for (const update of (data[key].updates ?? [])) {
      const tokens = tokenize(update.text)
      const isDuplicate = seen.some(s => jaccard(tokens, s.tokens) >= SIMILARITY_THRESHOLD)
      if (!isDuplicate) {
        seen.push({ tokens, sourceKey: key })
        kept.push(update)
      }
    }
    result[key] = { ...data[key], updates: kept }
  }

  // Pass through any keys not in PRIORITY order unchanged
  for (const key of Object.keys(data)) {
    if (!(key in result)) result[key] = data[key]
  }

  return result
}
