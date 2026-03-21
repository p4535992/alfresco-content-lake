import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../core/i18n.service';
import { RuntimeSettings } from '../../core/models';
import { SyncerApiService } from '../../core/syncer-api.service';

type MessageKind = 'ok' | 'warn' | 'err' | '';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {
  authToken = localStorage.getItem('syncer.authToken') ?? '';
  settings: RuntimeSettings | null = null;
  form = {
    httpPort: 9093,
    dataStorageRoot: '',
    openBrowserOnStartup: false
  };
  fieldErrors: Record<string, string> = {};
  message = { text: '', kind: '' as MessageKind };

  constructor(
    private readonly api: SyncerApiService,
    private readonly i18n: I18nService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.loadSettings();
  }

  t(key: string): string {
    return this.i18n.t(key);
  }

  onTokenChanged(): void {
    localStorage.setItem('syncer.authToken', this.authToken);
  }

  async loadSettings(): Promise<void> {
    try {
      this.settings = await this.api.getRuntimeSettings(this.authToken);
      this.form.httpPort = this.settings.httpPort;
      this.form.dataStorageRoot = this.settings.dataStorageRoot;
      this.form.openBrowserOnStartup = this.settings.openBrowserOnStartup;
      this.message = { text: '', kind: '' };
    } catch (error: unknown) {
      this.message = { text: this.api.errorMessage(error, this.t('loadFailed')), kind: 'err' };
    }
  }

  async saveSettings(): Promise<void> {
    if (!this.validate()) {
      return;
    }

    this.message = { text: this.t('saveSettings'), kind: 'warn' };
    try {
      this.settings = await this.api.saveRuntimeSettings(this.authToken, {
        httpPort: this.form.httpPort,
        dataStorageRoot: this.form.dataStorageRoot.trim(),
        openBrowserOnStartup: this.form.openBrowserOnStartup
      });
      this.message = { text: this.t('settingsSaved'), kind: 'ok' };
    } catch (error: unknown) {
      this.message = { text: this.api.errorMessage(error, this.t('settingsSaveFailed')), kind: 'err' };
    }
  }

  async browseStorageRoot(): Promise<void> {
    try {
      const selection = await this.api.selectLocalFolder(this.authToken);
      if (selection.path) {
        this.form.dataStorageRoot = selection.path;
        delete this.fieldErrors['dataStorageRoot'];
      }
    } catch (error: unknown) {
      this.message = { text: this.api.errorMessage(error, this.t('folderSelectFailed')), kind: 'err' };
    }
  }

  fieldState(field: string, value: string | number): string {
    if (this.fieldErrors[field]) {
      return 'is-invalid';
    }
    return String(value ?? '').trim() ? 'is-valid' : '';
  }

  private validate(): boolean {
    let valid = true;
    if (!Number.isInteger(this.form.httpPort) || this.form.httpPort < 1 || this.form.httpPort > 65535) {
      this.fieldErrors['httpPort'] = this.t('invalidPort');
      valid = false;
    } else {
      delete this.fieldErrors['httpPort'];
    }

    if (!this.form.dataStorageRoot.trim()) {
      this.fieldErrors['dataStorageRoot'] = this.t('requiredStorageRoot');
      valid = false;
    } else {
      delete this.fieldErrors['dataStorageRoot'];
    }
    return valid;
  }
}
