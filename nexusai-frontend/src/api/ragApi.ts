import { apiClient } from './axiosInstance'
import type { Document } from '@/types/Api'

export const ragApi = {
  /** Upload a PDF/text document for async RAG ingestion */
  upload: (file: File): Promise<Document> => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      .post<Document>('/api/rag/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },

  /** List all documents */
  list: (): Promise<Document[]> =>
    apiClient.get<Document[]>('/api/rag/documents').then((r) => r.data),

  /** Get status of a single document */
  status: (id: string): Promise<Document> =>
    apiClient.get<Document>(`/api/rag/documents/${id}`).then((r) => r.data),

  /** Delete a document from the vector store */
  delete: (id: string): Promise<void> =>
    apiClient.delete(`/api/rag/documents/${id}`).then(() => undefined),
}

