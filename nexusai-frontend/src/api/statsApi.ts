import { apiClient } from './axiosInstance'

export interface StatsOverview {
  usage: {
    conversations: number
    messages: number
    attachments: number
    exports: number
    ragDocuments: number
  }
  cache: {
    hitsExact: number
    hitsSemantic: number
    misses: number
    hitRate: number   // percentage
  }
  llm: {
    requests: number
    tokensPrompt: number
    tokensCompletion: number
  }
  queues: {
    documentIngest: number
    exportJob: number
    dlqDocument: number
    dlqEmbedding: number
  }
  daily: { day: string; count: number }[]
  generatedAt: string
}

export const statsApi = {
  overview: async (): Promise<StatsOverview> => {
    const r = await apiClient.get<StatsOverview>('/api/stats')
    return r.data
  },
}
