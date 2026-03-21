export interface RuntimeInfo {
  applicationUrl: string;
  jobRunrDashboardUrl: string;
  settingsUrl: string;
}

export interface RuntimeSettings {
  httpPort: number;
  openBrowserOnStartup: boolean;
  dataStorageRoot: string;
  logsDir: string;
  dataDir: string;
  jobrunrDataDir: string;
  configFilePath: string;
  externalConfigPresent: boolean;
  restartRequired: boolean;
}

export interface UpdateRuntimeSettingsRequest {
  httpPort: number;
  openBrowserOnStartup: boolean;
  dataStorageRoot: string;
}

export interface LocalFolderSelectionResponse {
  path: string | null;
}

export interface AlfrescoConnectionPayload {
  alfrescoBaseUrl: string;
  username: string;
  password: string;
  ticket: string;
}

export interface AlfrescoConnectionStatusResponse {
  userId: string;
  displayName: string;
}

export interface AlfrescoSiteInfo {
  id: string;
  title: string;
  description: string;
}

export interface RemoteNode {
  id: string;
  name: string;
  folder: boolean;
  file: boolean;
  sizeInBytes: number;
  modifiedAt: string | null;
}

export interface AlfrescoSiteBrowseResponse {
  site: AlfrescoSiteInfo;
  documentLibrary: RemoteNode;
  currentNode: RemoteNode;
  children: RemoteNode[];
}

export interface StartSyncRequest extends AlfrescoConnectionPayload {
  localRoot: string;
  remoteRootNodeId: string;
  dryRun: boolean;
  deleteRemoteMissing: boolean;
  forceNewVersion: boolean;
  forceVersionType: 'MINOR' | 'MAJOR';
  reportOutput: string;
}

export interface SyncFailure {
  path: string;
  operation: string;
  message: string;
}

export interface SyncItemResult {
  path: string;
  operation: string;
  outcome: string;
  sizeInBytes: number;
  remoteNodeId: string | null;
  message: string | null;
}

export interface SyncReport {
  localRoot: string;
  remoteRootNodeId: string;
  dryRun: boolean;
  deleteRemoteMissing: boolean;
  startedAt: string | null;
  completedAt: string | null;
  directoriesScanned: number;
  filesScanned: number;
  foldersCreated: number;
  filesUploaded: number;
  filesUpdated: number;
  filesSkipped: number;
  remoteNodesDeleted: number;
  uploadedBytes: number;
  updatedBytes: number;
  failures: SyncFailure[];
  items: SyncItemResult[];
  failedCount: number;
  durationMs: number;
}

export interface SyncJob {
  jobId: string;
  jobRunrId: string | null;
  localRoot: string;
  remoteRootNodeId: string;
  reportOutput: string;
  dryRun: boolean;
  deleteRemoteMissing: boolean;
  forceNewVersion: boolean;
  forceVersionType: 'MINOR' | 'MAJOR';
  createdAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  status: string;
  errorMessage: string | null;
  report: SyncReport | null;
}

export interface SyncReportHistoryEntry {
  jobId: string;
  status: string;
  localRoot: string;
  remoteRootNodeId: string;
  reportOutput: string;
  createdAt: string | null;
  completedAt: string | null;
}

export interface JobRunrSummary {
  total: number;
  awaiting: number;
  scheduled: number;
  enqueued: number;
  processing: number;
  failed: number;
  succeeded: number;
  deleted: number;
  allTimeSucceeded: number;
  recurringJobs: number;
  backgroundJobServers: number;
}

export interface SyncStateEntry {
  relativePath: string;
  remoteNodeId: string;
  sizeInBytes: number;
  sha256: string;
  remoteModifiedAt: string | null;
  lastTransferredAt: string | null;
}

export interface SyncStateView {
  remoteRootNodeId: string;
  entryCount: number;
  entries: SyncStateEntry[];
}

export interface LogFileInfo {
  fileName: string;
  sizeInBytes: number;
  modifiedAt: string | null;
}

export interface LogEntry {
  fileName: string | null;
  timestamp: string | null;
  level: string | null;
  category: string | null;
  thread: string | null;
  message: string | null;
  raw: string | null;
}

export interface LogView {
  logsDirectory: string | null;
  selectedFile: string | null;
  files: LogFileInfo[];
  entries: LogEntry[];
}
