import axios, { type AxiosInstance } from 'axios'

export const createApiClient = (baseUrl: string): AxiosInstance =>
  axios.create({ baseURL: baseUrl })
