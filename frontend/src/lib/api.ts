import axios from "axios";

import { useAuthStore } from "@/store/authStore";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL;

export const api = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const { token } = useAuthStore.getState();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export function assertApiUrlConfigured(): void {
  if (!apiBaseUrl) {
    throw new Error("NEXT_PUBLIC_API_URL must be set for frontend API calls.");
  }
}
