import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18nService, Locale } from './core/i18n.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  constructor(private readonly i18n: I18nService) {}

  get locale(): Locale {
    return this.i18n.locale;
  }

  set locale(locale: Locale) {
    this.i18n.setLocale(locale);
  }

  t(key: string): string {
    return this.i18n.t(key);
  }
}
