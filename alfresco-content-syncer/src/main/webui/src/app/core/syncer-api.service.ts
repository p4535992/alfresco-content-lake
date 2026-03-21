import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AlfrescoConnectionPayload,
  AlfrescoConnectionStatusResponse,
  AlfrescoSiteBrowseResponse,
  AlfrescoSiteInfo,
  JobRunrSummary,
  LocalFolderSelectionResponse,
  LogView,
  RuntimeInfo,
  RuntimeSettings,
  StartSyncRequest,
  SyncJob,
  SyncReportHistoryEntry,
  SyncStateView,
  UpdateRuntimeSettingsRequest
} from './models';

@Injectable({ providedIn: 'root' })
export class SyncerApiService {
  constructor(private readonly http: HttpClient) {}

  async getRuntimeInfo(token: string): Promise<RuntimeInfo> {
    return firstValueFrom(this.http.get<RuntimeInfo>('/api/system/runtime', { headers: this.headers(token) }));
  }

  async getRuntimeSettings(token: string): Promise<RuntimeSettings> {
    return firstValueFrom(this.http.get<RuntimeSettings>('/api/system/settings', { headers: this.headers(token) }));
  }

  async saveRuntimeSettings(token: string, request: UpdateRuntimeSettingsRequest): Promise<RuntimeSettings> {
    return firstValueFrom(this.http.post<RuntimeSettings>('/api/system/settings', request, {
      headers: this.headers(token, true)
    }));
  }

  async selectLocalFolder(token: string): Promise<LocalFolderSelectionResponse> {
    return firstValueFrom(this.http.post<LocalFolderSelectionResponse>('/api/system/local-folder/select', null, {
      headers: this.headers(token)
    }));
  }

  async verifyConnection(token: string, request: AlfrescoConnectionPayload): Promise<AlfrescoConnectionStatusResponse> {
    return firstValueFrom(this.http.post<AlfrescoConnectionStatusResponse>('/api/alfresco/connection/verify', request, {
      headers: this.headers(token, true)
    }));
  }

  async listSites(token: string, request: AlfrescoConnectionPayload): Promise<AlfrescoSiteInfo[]> {
    return firstValueFrom(this.http.post<AlfrescoSiteInfo[]>('/api/alfresco/sites', request, {
      headers: this.headers(token, true)
    }));
  }

  async browseSite(
    token: string,
    request: AlfrescoConnectionPayload & { siteId: string; folderNodeId?: string }
  ): Promise<AlfrescoSiteBrowseResponse> {
    return firstValueFrom(this.http.post<AlfrescoSiteBrowseResponse>('/api/alfresco/sites/browse', request, {
      headers: this.headers(token, true)
    }));
  }

  async listJobs(token: string): Promise<SyncJob[]> {
    return firstValueFrom(this.http.get<SyncJob[]>('/api/sync/jobs', { headers: this.headers(token) }));
  }

  async getJob(token: string, jobId: string): Promise<SyncJob> {
    return firstValueFrom(this.http.get<SyncJob>(`/api/sync/jobs/${encodeURIComponent(jobId)}`, {
      headers: this.headers(token)
    }));
  }

  async startJob(token: string, request: StartSyncRequest): Promise<SyncJob> {
    return firstValueFrom(this.http.post<SyncJob>('/api/sync/jobs', request, {
      headers: this.headers(token, true)
    }));
  }

  async listReports(token: string): Promise<SyncReportHistoryEntry[]> {
    return firstValueFrom(this.http.get<SyncReportHistoryEntry[]>('/api/sync/reports', {
      headers: this.headers(token)
    }));
  }

  async getJobRunrSummary(token: string): Promise<JobRunrSummary> {
    return firstValueFrom(this.http.get<JobRunrSummary>('/api/sync/jobrunr/summary', {
      headers: this.headers(token)
    }));
  }

  async getTrackedState(token: string, remoteRootNodeId: string): Promise<SyncStateView> {
    return firstValueFrom(this.http.get<SyncStateView>(`/api/sync/state/${encodeURIComponent(remoteRootNodeId)}`, {
      headers: this.headers(token)
    }));
  }

  async clearTrackedState(token: string, remoteRootNodeId: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/sync/state/${encodeURIComponent(remoteRootNodeId)}`, {
      headers: this.headers(token)
    }));
  }

  async getLogs(token: string, fileName?: string, limit = 200): Promise<LogView> {
    let params = new HttpParams().set('limit', String(limit));
    if (fileName) {
      params = params.set('file', fileName);
    }
    return firstValueFrom(this.http.get<LogView>('/api/logs', {
      headers: this.headers(token),
      params
    }));
  }

  async downloadCsv(token: string, jobId: string): Promise<Blob> {
    return firstValueFrom(this.http.get(`/api/sync/jobs/${encodeURIComponent(jobId)}/report.csv`, {
      headers: this.headers(token),
      responseType: 'blob'
    }));
  }

  async downloadJson(token: string, jobId: string): Promise<Blob> {
    return firstValueFrom(this.http.get(`/api/sync/jobs/${encodeURIComponent(jobId)}/report.json`, {
      headers: this.headers(token),
      responseType: 'blob'
    }));
  }

  errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error === 'string' && error.error.trim()) {
        return error.error;
      }
      if (error.error && typeof error.error.message === 'string' && error.error.message.trim()) {
        return error.error.message;
      }
      return error.message || fallback;
    }
    if (error instanceof Error) {
      return error.message || fallback;
    }
    return fallback;
  }

  private headers(token: string, json = false): HttpHeaders {
    let headers = new HttpHeaders();
    if (json) {
      headers = headers.set('Content-Type', 'application/json');
    }
    if (token.trim()) {
      headers = headers.set('X-Syncer-Token', token.trim());
    }
    return headers;
  }
}
