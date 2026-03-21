import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../core/i18n.service';
import {
  AlfrescoConnectionPayload,
  AlfrescoSiteBrowseResponse,
  AlfrescoSiteInfo,
  JobRunrSummary,
  LogEntry,
  LogView,
  RuntimeInfo,
  StartSyncRequest,
  SyncItemResult,
  SyncJob,
  SyncReportHistoryEntry,
  SyncStateEntry,
  SyncStateView
} from '../../core/models';
import { SyncerApiService } from '../../core/syncer-api.service';

type MessageKind = 'ok' | 'warn' | 'err' | '';
type JobFilter = 'all' | 'queued' | 'running' | 'failed' | 'completed';
type TransferTab = 'transferred' | 'inProgress' | 'error';
type ReportSortKey = 'completedAt' | 'jobId' | 'status' | 'localRoot' | 'remoteRootNodeId';

interface UiMessage {
  text: string;
  kind: MessageKind;
}

interface Breadcrumb {
  id: string;
  name: string;
}

interface TransferEntry {
  path: string;
  operation: string;
  statusKey: TransferTab;
  statusLabel: string;
  statusClass: 'success' | 'progress' | 'error';
  sizeInBytes: number;
  remoteNodeId: string;
  message: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly connection: AlfrescoConnectionPayload = {
    alfrescoBaseUrl: '',
    username: '',
    password: '',
    ticket: ''
  };

  readonly jobRequest = {
    localRoot: '',
    reportOutput: '',
    dryRun: true,
    deleteRemoteMissing: false,
    forceVersionType: 'MINOR' as 'MINOR' | 'MAJOR'
  };

  authToken = localStorage.getItem('syncer.authToken') ?? '';
  verifiedConnection = false;
  verifiedUser = '';
  connectionStatus: UiMessage = { text: '', kind: 'warn' };
  message: UiMessage = { text: '', kind: '' };
  fieldErrors: Record<string, string> = {};

  runtimeInfo: RuntimeInfo | null = null;
  sites: AlfrescoSiteInfo[] = [];
  selectedSiteId = '';
  browseState: AlfrescoSiteBrowseResponse | null = null;
  browseStack: Breadcrumb[] = [];
  selectedRemoteFolderId = '';
  selectedRemoteFolderPath = '';

  jobs: SyncJob[] = [];
  selectedJobId = '';
  selectedJob: SyncJob | null = null;
  activeJobFilter: JobFilter = 'all';
  jobSearchQuery = '';

  reports: SyncReportHistoryEntry[] = [];
  reportSearchQuery = '';
  reportStatusFilter = 'all';
  reportCompletedFrom = '';
  reportSortKey: ReportSortKey = 'completedAt';
  reportSortDirection: 'asc' | 'desc' = 'desc';

  jobRunrSummary: JobRunrSummary | null = null;
  activeTransferTab: TransferTab = 'transferred';
  trackedState: SyncStateView | null = null;
  logsView: LogView | null = null;
  selectedLogFileName = '';
  selectedLogRaw = '';
  autoRefreshLogs = true;

  submittingJob = false;
  loadingLogs = false;

  private jobPollHandle: number | null = null;
  private logsPollHandle: number | null = null;

  constructor(
    private readonly api: SyncerApiService,
    private readonly i18n: I18nService
  ) {
    this.setConnectionStatus(this.t('connectionNotVerified'), 'warn');
  }

  async ngOnInit(): Promise<void> {
    await Promise.allSettled([
      this.loadRuntimeInfo(),
      this.loadJobs(),
      this.loadLogs()
    ]);
    this.ensureLogsPolling();
  }

  ngOnDestroy(): void {
    this.stopJobPolling();
    this.stopLogsPolling();
  }

  t(key: string, params: Record<string, string | number> = {}): string {
    return this.i18n.t(key, params);
  }

  onTokenChanged(): void {
    localStorage.setItem('syncer.authToken', this.authToken);
  }

  onConnectionChanged(): void {
    if (this.verifiedConnection || this.selectedSiteId || this.browseState) {
      this.resetVerifiedConnection();
    }
  }

  async verifyConnection(): Promise<void> {
    if (!this.validateConnectionFields()) {
      return;
    }

    this.setConnectionStatus(this.t('verifyingConnection'), 'warn');
    try {
      const status = await this.api.verifyConnection(this.authToken, this.connectionPayload());
      this.verifiedConnection = true;
      this.verifiedUser = status.displayName ? `${status.displayName} (${status.userId})` : status.userId;
      this.setConnectionStatus(this.t('connectionVerified', { user: status.userId }), 'ok');
      await this.reloadSites();
    } catch (error: unknown) {
      this.verifiedConnection = false;
      this.verifiedUser = '';
      this.setConnectionStatus(this.api.errorMessage(error, this.t('requestFailed')), 'err');
    }
  }

  async reloadSites(): Promise<void> {
    if (!this.validateConnectionFields()) {
      return;
    }

    try {
      const sites = await this.api.listSites(this.authToken, this.connectionPayload());
      this.sites = [...sites].sort((left, right) => this.siteLabel(left).localeCompare(this.siteLabel(right)));
      this.selectedSiteId = '';
      this.clearFieldError('site');
      this.setMessage(this.t('sitesReloaded'), 'ok');
    } catch (error: unknown) {
      this.setConnectionStatus(this.api.errorMessage(error, this.t('requestFailed')), 'err');
    }
  }

  async loadSiteRoot(): Promise<void> {
    if (!this.selectedSiteId) {
      this.setFieldError('site', this.t('siteRequired'));
      return;
    }

    this.setMessage(this.t('loadingDocumentLibrary'), 'warn');
    try {
      const data = await this.api.browseSite(this.authToken, {
        ...this.connectionPayload(),
        siteId: this.selectedSiteId
      });
      this.browseState = data;
      this.browseStack = [{ id: data.documentLibrary.id, name: data.documentLibrary.name }];
      this.selectedRemoteFolderId = data.currentNode.id;
      this.selectedRemoteFolderPath = this.currentBrowsePath();
      this.clearFieldError('site');
      await this.loadTrackedState();
      this.setMessage(this.t('documentLibraryLoaded'), 'ok');
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('requestFailed')), 'err');
    }
  }

  async browseToChild(childId: string, childName: string): Promise<void> {
    this.browseStack.push({ id: childId, name: childName });
    await this.browseCurrentFolder();
  }

  async browseBack(): Promise<void> {
    if (this.browseStack.length <= 1) {
      return;
    }
    this.browseStack.pop();
    await this.browseCurrentFolder();
  }

  async useCurrentFolder(): Promise<void> {
    if (!this.browseState) {
      this.setMessage(this.t('browsePlaceholder'), 'warn');
      return;
    }

    this.selectedRemoteFolderId = this.browseState.currentNode.id;
    this.selectedRemoteFolderPath = this.currentBrowsePath();
    await this.loadTrackedState();
    this.setMessage(this.t('remoteTargetSelected'), 'ok');
  }

  async selectLocalFolder(): Promise<void> {
    try {
      const selection = await this.api.selectLocalFolder(this.authToken);
      if (selection.path) {
        this.jobRequest.localRoot = selection.path;
        this.clearFieldError('localRoot');
      }
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('folderSelectFailed')), 'err');
    }
  }

  async submitJob(forceNewVersion: boolean): Promise<void> {
    if (!this.validateJobFields()) {
      return;
    }

    this.submittingJob = true;
    this.setMessage(this.t('submittingJob'), 'warn');
    try {
      const job = await this.api.startJob(this.authToken, this.jobPayload(forceNewVersion));
      this.selectedJobId = job.jobId;
      await this.loadJobs();
      await this.loadJob(job.jobId);
      this.setMessage(this.t(forceNewVersion ? 'forceVersionAccepted' : 'jobAccepted', { jobId: job.jobId }), 'ok');
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('requestFailed')), 'err');
    } finally {
      this.submittingJob = false;
    }
  }

  async selectJob(jobId: string): Promise<void> {
    this.selectedJobId = jobId;
    await this.loadJob(jobId);
  }

  async refreshReports(): Promise<void> {
    try {
      this.reports = await this.api.listReports(this.authToken);
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('reportsRefreshFailed')), 'err');
    }
  }

  async refreshTrackedState(): Promise<void> {
    try {
      await this.loadTrackedState();
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('trackedRefreshFailed')), 'err');
    }
  }

  async clearTrackedState(): Promise<void> {
    const remoteRootNodeId = this.activeTrackedRootId();
    if (!remoteRootNodeId) {
      this.setMessage(this.t('trackedSelectTarget'), 'warn');
      return;
    }
    if (!window.confirm(this.t('trackedClearConfirm'))) {
      return;
    }

    try {
      await this.api.clearTrackedState(this.authToken, remoteRootNodeId);
      await this.loadTrackedState();
      this.setMessage(this.t('trackedCleared'), 'ok');
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('trackedClearFailed')), 'err');
    }
  }

  async loadLogs(fileName?: string): Promise<void> {
    this.loadingLogs = true;
    try {
      const selectedFile = (fileName ?? this.selectedLogFileName) || '';
      const logs = await this.api.getLogs(this.authToken, selectedFile || undefined);
      this.logsView = logs;
      this.selectedLogFileName = logs.selectedFile ?? fileName ?? this.selectedLogFileName;
      const entries = this.displayLogEntries;
      if (!entries.length) {
        this.selectedLogRaw = this.t('logsNoSelected');
      } else if (!this.selectedLogRaw || this.selectedLogRaw === this.t('logsNoSelected')) {
        const latest = entries[0];
        this.selectedLogRaw = latest.raw || latest.message || this.t('logsNoSelected');
      }
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('logsRefreshFailed')), 'err');
    } finally {
      this.loadingLogs = false;
    }
  }

  async onLogFileChanged(): Promise<void> {
    this.selectedLogRaw = this.t('logsNoSelected');
    await this.loadLogs(this.selectedLogFileName);
  }

  toggleLogsPolling(): void {
    this.ensureLogsPolling();
  }

  openDashboard(): void {
    if (!this.runtimeInfo?.jobRunrDashboardUrl) {
      this.setMessage(this.t('requestFailed'), 'warn');
      return;
    }
    window.open(this.runtimeInfo.jobRunrDashboardUrl, '_blank', 'noopener');
  }

  async downloadCsv(jobId: string): Promise<void> {
    try {
      this.setMessage(this.t('csvGenerating'), 'warn');
      const blob = await this.api.downloadCsv(this.authToken, jobId);
      this.downloadBlob(blob, `${jobId}-report.csv`);
      this.setMessage(this.t('csvDownloaded'), 'ok');
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('csvFailed')), 'err');
    }
  }

  async downloadJson(jobId: string): Promise<void> {
    try {
      this.setMessage(this.t('jsonGenerating'), 'warn');
      const blob = await this.api.downloadJson(this.authToken, jobId);
      this.downloadBlob(blob, `${jobId}-report.json`);
      this.setMessage(this.t('jsonDownloaded'), 'ok');
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('jsonFailed')), 'err');
    }
  }

  exportReportsCsv(): void {
    if (!this.visibleReports.length) {
      this.setMessage(this.t('reportsNone'), 'warn');
      return;
    }
    const rows = [
      ['completedAt', 'jobId', 'status', 'localRoot', 'remoteRootNodeId', 'reportOutput'],
      ...this.visibleReports.map((report) => [
        report.completedAt || report.createdAt || '',
        report.jobId || '',
        report.status || '',
        report.localRoot || '',
        report.remoteRootNodeId || '',
        report.reportOutput || ''
      ])
    ];
    const csv = rows
      .map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(','))
      .join('\r\n');
    this.downloadBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'alfresco-content-syncer-report-history.csv');
  }

  sortReports(key: ReportSortKey): void {
    if (this.reportSortKey === key) {
      this.reportSortDirection = this.reportSortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.reportSortKey = key;
      this.reportSortDirection = key === 'completedAt' ? 'desc' : 'asc';
    }
  }

  selectTransferTab(tab: TransferTab): void {
    this.activeTransferTab = tab;
  }

  transferCount(tab: TransferTab): number {
    return this.transferEntries.filter((entry) => entry.statusKey === tab).length;
  }

  selectRawLog(entry: LogEntry): void {
    this.selectedLogRaw = entry.raw || entry.message || this.t('logsNoSelected');
  }

  reportText(job: SyncJob | null): string {
    if (!job) {
      return this.t('reportNotSelected');
    }
    const report = job.report;
    const items = report?.items?.slice(-60).reverse() ?? [];
    const failures = report?.failures ?? [];
    const lines = [
      `Job: ${job.jobId}`,
      `Status: ${job.status}`,
      `Local root: ${job.localRoot || '-'}`,
      `Remote root: ${job.remoteRootNodeId || '-'}`,
      `Report output: ${job.reportOutput || '-'}`,
      `Started: ${this.formatDate(job.startedAt || report?.startedAt)}`,
      `Completed: ${this.formatDate(job.completedAt || report?.completedAt)}`,
      `Duration: ${report?.durationMs != null ? `${report.durationMs} ms` : '-'}`,
      '',
      'Summary',
      `Directories scanned: ${report?.directoriesScanned ?? 0}`,
      `Files scanned: ${report?.filesScanned ?? 0}`,
      `Folders created: ${report?.foldersCreated ?? 0}`,
      `Files uploaded: ${report?.filesUploaded ?? 0} (${this.formatBytes(report?.uploadedBytes ?? -1)})`,
      `Files updated: ${report?.filesUpdated ?? 0} (${this.formatBytes(report?.updatedBytes ?? -1)})`,
      `Files skipped: ${report?.filesSkipped ?? 0}`,
      `Remote nodes deleted: ${report?.remoteNodesDeleted ?? 0}`,
      `Failures: ${report?.failedCount ?? failures.length}`,
      '',
      'Recent activity'
    ];
    if (!items.length) {
      lines.push('No file-level activity yet.');
    } else {
      items.forEach((item) => {
        const size = item.sizeInBytes >= 0 ? ` | ${this.formatBytes(item.sizeInBytes)}` : '';
        const remoteId = item.remoteNodeId ? ` | ${item.remoteNodeId}` : '';
        const message = item.message ? ` | ${item.message}` : '';
        lines.push(`[${item.outcome}] ${item.operation} | ${item.path}${size}${remoteId}${message}`);
      });
    }
    if (failures.length) {
      lines.push('', 'Failures');
      failures.slice(-20).reverse().forEach((failure) => {
        lines.push(`- ${failure.operation} | ${failure.path} | ${failure.message}`);
      });
    } else if (job.errorMessage) {
      lines.push('', `Error: ${job.errorMessage}`);
    }
    return lines.join('\n');
  }

  get selectedTargetLabel(): string {
    if (!this.selectedRemoteFolderId) {
      return this.t('noFolderSelected');
    }
    return `${this.selectedRemoteFolderPath} (${this.selectedRemoteFolderId})`;
  }

  get browserHeading(): string {
    if (!this.browseState) {
      return this.t('browsePlaceholder');
    }
    return `${this.siteLabel(this.browseState.site)} / ${this.currentBrowsePath()}`;
  }

  get browserFolders() {
    return this.browseState?.children ?? [];
  }

  get filteredJobs(): SyncJob[] {
    const normalizedQuery = this.jobSearchQuery.trim().toLowerCase();
    return this.jobs.filter((job) => {
      if (this.activeJobFilter !== 'all' && this.jobFilterKey(job) !== this.activeJobFilter) {
        return false;
      }
      if (!normalizedQuery) {
        return true;
      }
      const haystack = [job.jobId, job.localRoot, job.remoteRootNodeId, job.status, job.reportOutput].join(' ').toLowerCase();
      return haystack.includes(normalizedQuery);
    });
  }

  get jobFilterCards(): Array<{ key: JobFilter; label: string; count: number }> {
    const counts: Record<JobFilter, number> = { all: this.jobs.length, queued: 0, running: 0, failed: 0, completed: 0 };
    this.jobs.forEach((job) => {
      counts[this.jobFilterKey(job)] += 1;
    });
    return [
      { key: 'all', label: this.t('all'), count: counts.all },
      { key: 'queued', label: this.t('queued'), count: counts.queued },
      { key: 'running', label: this.t('running'), count: counts.running },
      { key: 'failed', label: this.t('failed'), count: counts.failed },
      { key: 'completed', label: this.t('succeeded'), count: counts.completed }
    ];
  }

  get visibleReports(): SyncReportHistoryEntry[] {
    const normalizedQuery = this.reportSearchQuery.trim().toLowerCase();
    return [...this.reports]
      .filter((report) => {
        if (this.reportStatusFilter !== 'all' && report.status?.toUpperCase() !== this.reportStatusFilter) {
          return false;
        }
        if (this.reportCompletedFrom) {
          const completedAt = report.completedAt || report.createdAt;
          if (!completedAt || new Date(completedAt).toISOString().slice(0, 10) < this.reportCompletedFrom) {
            return false;
          }
        }
        if (!normalizedQuery) {
          return true;
        }
        const haystack = [report.jobId, report.status, report.localRoot, report.remoteRootNodeId, report.reportOutput]
          .join(' ')
          .toLowerCase();
        return haystack.includes(normalizedQuery);
      })
      .sort((left, right) => {
        const leftValue = this.reportSortKey === 'completedAt' ? (left.completedAt || left.createdAt || '') : String(left[this.reportSortKey] || '');
        const rightValue = this.reportSortKey === 'completedAt' ? (right.completedAt || right.createdAt || '') : String(right[this.reportSortKey] || '');
        const result = String(leftValue).localeCompare(String(rightValue));
        return this.reportSortDirection === 'asc' ? result : result * -1;
      });
  }

  get transferEntries(): TransferEntry[] {
    const reportItems = this.selectedJob?.report?.items ?? [];
    const latestByPath = new Map<string, SyncItemResult>();
    reportItems.forEach((item) => {
      if (!item.path || !['upload-file', 'update-file', 'skip-file', 'sync-file'].includes(item.operation)) {
        return;
      }
      latestByPath.set(item.path, item);
    });
    return Array.from(latestByPath.values()).map((item) => this.transferEntry(item)).sort((left, right) => left.path.localeCompare(right.path));
  }

  get visibleTransferEntries(): TransferEntry[] {
    return this.transferEntries.filter((entry) => entry.statusKey === this.activeTransferTab);
  }

  get trackedEntries(): SyncStateEntry[] {
    return this.trackedState?.entries ?? [];
  }

  get displayLogEntries(): LogEntry[] {
    return [...(this.logsView?.entries ?? [])].reverse();
  }

  fieldState(field: string, currentValue: string): string {
    if (this.fieldErrors[field]) {
      return 'is-invalid';
    }
    return currentValue.trim() ? 'is-valid' : '';
  }

  reportSortLabel(key: ReportSortKey, label: string): string {
    const marker = this.reportSortKey === key ? (this.reportSortDirection === 'asc' ? ' ↑' : ' ↓') : '';
    return `${label}${marker}`;
  }

  formatDate(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleString(this.i18n.locale) : '-';
  }

  formatBytes(value: number | null | undefined): string {
    if (value == null || value < 0) {
      return '-';
    }
    if (value < 1024) {
      return `${value} B`;
    }
    if (value < 1024 * 1024) {
      return `${(value / 1024).toFixed(1)} KB`;
    }
    if (value < 1024 * 1024 * 1024) {
      return `${(value / (1024 * 1024)).toFixed(1)} MB`;
    }
    return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  }

  siteLabel(site: AlfrescoSiteInfo | null | undefined): string {
    return site ? (site.title || site.id) : '';
  }

  private async loadRuntimeInfo(): Promise<void> {
    try {
      this.runtimeInfo = await this.api.getRuntimeInfo(this.authToken);
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('loadFailed')), 'err');
    }
  }

  private async loadJobs(): Promise<void> {
    try {
      const [jobs, reports, summary] = await Promise.all([
        this.api.listJobs(this.authToken),
        this.api.listReports(this.authToken),
        this.api.getJobRunrSummary(this.authToken)
      ]);
      this.jobs = jobs;
      this.reports = reports;
      this.jobRunrSummary = summary;
      if (!this.selectedJobId && jobs.length > 0) {
        this.selectedJobId = jobs[0].jobId;
      }
      if (this.selectedJobId) {
        const selected = jobs.find((job) => job.jobId === this.selectedJobId);
        if (selected) {
          this.selectedJob = selected;
          this.startJobPollingIfNeeded(selected);
        }
      } else {
        await this.loadTrackedState();
      }
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('loadFailed')), 'err');
    }
  }

  private async loadJob(jobId: string): Promise<void> {
    try {
      const job = await this.api.getJob(this.authToken, jobId);
      this.selectedJob = job;
      this.selectedJobId = job.jobId;
      await this.loadTrackedState();
      this.startJobPollingIfNeeded(job);
    } catch (error: unknown) {
      this.setMessage(this.api.errorMessage(error, this.t('requestFailed')), 'err');
    }
  }

  private async loadTrackedState(): Promise<void> {
    const remoteRootNodeId = this.activeTrackedRootId();
    if (!remoteRootNodeId) {
      this.trackedState = null;
      return;
    }
    this.trackedState = await this.api.getTrackedState(this.authToken, remoteRootNodeId);
  }

  private connectionPayload(): AlfrescoConnectionPayload {
    return {
      alfrescoBaseUrl: this.connection.alfrescoBaseUrl.trim(),
      username: this.connection.username,
      password: this.connection.password,
      ticket: this.connection.ticket.trim()
    };
  }

  private jobPayload(forceNewVersion: boolean): StartSyncRequest {
    return {
      ...this.connectionPayload(),
      localRoot: this.jobRequest.localRoot.trim(),
      remoteRootNodeId: this.selectedRemoteFolderId,
      dryRun: this.jobRequest.dryRun,
      deleteRemoteMissing: this.jobRequest.deleteRemoteMissing,
      forceNewVersion,
      forceVersionType: this.jobRequest.forceVersionType,
      reportOutput: this.jobRequest.reportOutput.trim()
    };
  }

  private validateConnectionFields(): boolean {
    let valid = true;
    const normalizedBaseUrl = this.connection.alfrescoBaseUrl.trim().replace(/\/$/, '');
    if (!normalizedBaseUrl) {
      this.setFieldError('alfrescoBaseUrl', this.t('baseUrlRequired'));
      valid = false;
    } else if (!normalizedBaseUrl.toLowerCase().endsWith('/alfresco')) {
      this.setFieldError('alfrescoBaseUrl', this.t('baseUrlInvalid'));
      valid = false;
    } else {
      this.clearFieldError('alfrescoBaseUrl');
    }
    const hasBasic = this.connection.username.trim() && this.connection.password;
    const hasTicket = this.connection.ticket.trim();
    if (!hasBasic && !hasTicket) {
      this.setFieldError('username', this.t('credentialsRequired'));
      this.setFieldError('password', this.t('credentialsRequired'));
      this.setFieldError('ticket', this.t('credentialsRequired'));
      valid = false;
    } else {
      this.clearFieldError('username');
      this.clearFieldError('password');
      this.clearFieldError('ticket');
    }
    return valid;
  }

  private validateJobFields(): boolean {
    let valid = this.validateConnectionFields();
    if (!this.jobRequest.localRoot.trim()) {
      this.setFieldError('localRoot', this.t('localRootRequired'));
      valid = false;
    } else {
      this.clearFieldError('localRoot');
    }
    const reportOutput = this.jobRequest.reportOutput.trim();
    if (reportOutput && !/\.(csv|json)$/i.test(reportOutput)) {
      this.setFieldError('reportOutput', this.t('reportOutputInvalid'));
      valid = false;
    } else {
      this.clearFieldError('reportOutput');
    }
    if (!this.verifiedConnection) {
      this.setConnectionStatus(this.t('verifyBeforeStart'), 'err');
      valid = false;
    }
    if (!this.selectedSiteId) {
      this.setFieldError('site', this.t('siteRequired'));
      valid = false;
    } else {
      this.clearFieldError('site');
    }
    if (!this.selectedRemoteFolderId) {
      this.setMessage(this.t('selectRemoteTarget'), 'err');
      valid = false;
    }
    return valid;
  }

  private resetVerifiedConnection(): void {
    this.verifiedConnection = false;
    this.verifiedUser = '';
    this.sites = [];
    this.selectedSiteId = '';
    this.browseState = null;
    this.browseStack = [];
    this.selectedRemoteFolderId = '';
    this.selectedRemoteFolderPath = '';
    this.trackedState = null;
    this.setConnectionStatus(this.t('connectionNotVerified'), 'warn');
  }

  private async browseCurrentFolder(): Promise<void> {
    const current = this.browseStack[this.browseStack.length - 1];
    if (!current || !this.selectedSiteId) {
      return;
    }
    this.browseState = await this.api.browseSite(this.authToken, {
      ...this.connectionPayload(),
      siteId: this.selectedSiteId,
      folderNodeId: current.id
    });
  }

  private currentBrowsePath(): string {
    return this.browseStack.map((entry) => entry.name).join(' / ') || '-';
  }

  private activeTrackedRootId(): string {
    return this.selectedRemoteFolderId || this.selectedJob?.remoteRootNodeId || '';
  }

  private setMessage(text: string, kind: MessageKind): void {
    this.message = { text, kind };
  }

  private setConnectionStatus(text: string, kind: MessageKind): void {
    this.connectionStatus = { text, kind };
  }

  private setFieldError(field: string, message: string): void {
    this.fieldErrors[field] = message;
  }

  private clearFieldError(field: string): void {
    delete this.fieldErrors[field];
  }

  private jobFilterKey(job: SyncJob): JobFilter {
    switch ((job.status || '').toUpperCase()) {
      case 'QUEUED':
        return 'queued';
      case 'RUNNING':
        return 'running';
      case 'FAILED':
        return 'failed';
      case 'COMPLETED':
        return 'completed';
      default:
        return 'all';
    }
  }

  private transferEntry(item: SyncItemResult): TransferEntry {
    const outcome = (item.outcome || '').toUpperCase();
    if (outcome === 'FAILED') {
      return { path: item.path, operation: item.operation || '-', statusKey: 'error', statusLabel: this.t('error'), statusClass: 'error', sizeInBytes: item.sizeInBytes, remoteNodeId: item.remoteNodeId || '-', message: item.message || '-' };
    }
    if (outcome === 'IN_PROGRESS') {
      return { path: item.path, operation: item.operation || '-', statusKey: 'inProgress', statusLabel: this.t('inProgress'), statusClass: 'progress', sizeInBytes: item.sizeInBytes, remoteNodeId: item.remoteNodeId || '-', message: item.message || '-' };
    }
    return { path: item.path, operation: item.operation || '-', statusKey: 'transferred', statusLabel: this.t('transferred'), statusClass: 'success', sizeInBytes: item.sizeInBytes, remoteNodeId: item.remoteNodeId || '-', message: item.message || '-' };
  }

  private startJobPollingIfNeeded(job: SyncJob): void {
    const active = ['RUNNING', 'QUEUED'].includes((job.status || '').toUpperCase());
    if (!active) {
      this.stopJobPolling();
      return;
    }
    if (this.jobPollHandle != null) {
      return;
    }
    this.jobPollHandle = window.setInterval(async () => {
      if (!this.selectedJobId) {
        this.stopJobPolling();
        return;
      }
      await this.loadJobs();
      await this.loadJob(this.selectedJobId);
      if (!['RUNNING', 'QUEUED'].includes((this.selectedJob?.status || '').toUpperCase())) {
        this.stopJobPolling();
      }
    }, 2000);
  }

  private stopJobPolling(): void {
    if (this.jobPollHandle != null) {
      window.clearInterval(this.jobPollHandle);
      this.jobPollHandle = null;
    }
  }

  private ensureLogsPolling(): void {
    this.stopLogsPolling();
    if (!this.autoRefreshLogs) {
      return;
    }
    this.logsPollHandle = window.setInterval(async () => {
      await this.loadLogs();
    }, 4000);
  }

  private stopLogsPolling(): void {
    if (this.logsPollHandle != null) {
      window.clearInterval(this.logsPollHandle);
      this.logsPollHandle = null;
    }
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }
}
