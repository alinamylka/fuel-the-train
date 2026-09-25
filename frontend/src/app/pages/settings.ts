import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Api, CalendarStatus, IntervalsStatus } from '../api';

@Component({
  selector: 'app-settings',
  imports: [FormsModule],
  template: `
    <h1>Settings</h1>

    <section class="card">
      <header>
        <h2>TrainingPeaks calendar</h2>
        @if (status(); as s) {
          <span class="state" [class.on]="s.connected">{{ s.connected ? 'Connected' : 'Not connected' }}</span>
        }
      </header>

      <p>
        Fuel the Train reads your planned workouts from your TrainingPeaks calendar. Once a workout is done, it also
        picks up the actual duration and distance. Targets for each day are calculated from these workouts.
      </p>

      @if (status(); as s) {
        @if (s.connected) {
          <div class="connected">
            <div>
              <div class="url">{{ s.maskedUrl }}</div>
              <div class="muted small">
                @if (s.source === 'ENV') {
                  Taken from TP_ICAL_URL in the .env file. Paste a link below to store it in the app instead.
                } @else if (s.events !== null) {
                  Found {{ s.events }} calendar entries. Go to Week and click “Sync” to import them.
                } @else {
                  Saved in the app.
                }
              </div>
            </div>
            @if (s.source === 'APP') {
              <button class="link" (click)="disconnect()">Disconnect</button>
            }
          </div>
        }
      }

      <h3>How to connect</h3>
      <ol class="steps">
        <li>
          Sign in to <a href="https://app.trainingpeaks.com" target="_blank" rel="noopener">TrainingPeaks</a> in a
          web browser. Calendar sync is not available in the mobile app.
        </li>
        <li>Open <strong>Settings → Account → Calendar</strong>.</li>
        <li>
          Copy the iCal link. It looks like
          <code>webcal://www.trainingpeaks.com/ical/ABC123….ics</code>.
        </li>
        <li>Paste it below and click <strong>Connect</strong>. The app downloads the calendar once to check the link.</li>
      </ol>

      <form class="row" (ngSubmit)="connect()">
        <label class="sr-only" for="icalUrl">TrainingPeaks iCal link</label>
        <input
          id="icalUrl"
          name="icalUrl"
          [(ngModel)]="icalUrl"
          placeholder="webcal://www.trainingpeaks.com/ical/…"
          autocomplete="off"
          required
        />
        <button class="primary" type="submit" [disabled]="saving() || !icalUrl.trim()">
          {{ saving() ? 'Checking…' : status()?.connected ? 'Replace link' : 'Connect' }}
        </button>
      </form>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }

      <details>
        <summary>Good to know</summary>
        <ul>
          <li>Calendar sync requires a <strong>TrainingPeaks Premium</strong> account.</li>
          <li>
            Anyone with the link can see your calendar, without logging in. Don't share it. The app stores it on your
            computer and never shows the full link again.
          </li>
          <li>
            Nothing syncs in the background. Click “Sync” on the Week page after your coach updates the
            plan or after a workout uploads.
          </li>
          <li>
            The calendar has no power, kJ or heart rate data, so workout energy is estimated from your FTP and the
            workout's intensity. You can correct the intensity for each workout on the Week page.
            Connect intervals.icu below to use measured kJ instead.
          </li>
          <li>Entries without a workout type, such as races and events, are treated as race days.</li>
        </ul>
      </details>
    </section>

    <section class="card">
      <header>
        <h2>intervals.icu</h2>
        @if (intervals(); as s) {
          <span class="state" [class.on]="s.connected">{{ s.connected ? 'Connected' : 'Not connected' }}</span>
        }
      </header>

      <p>
        intervals.icu provides what you actually did: the work from your power meter in kJ, and sessions that weren't
        in the plan. With it, workout energy is measured instead of estimated.
      </p>

      @if (intervals(); as s) {
        @if (s.connected) {
          <div class="connected">
            <div>
              <div>{{ s.athleteName ? 'Signed in as ' + s.athleteName : 'Connected' }}</div>
              <div class="muted small">
                @if (s.source === 'ENV') {
                  Using INTERVALS_API_KEY from the .env file. Paste a key below to store it in the app instead.
                } @else {
                  Click “Sync” on the Week page to import the last 90 days.
                }
              </div>
            </div>
            @if (s.source === 'APP') {
              <button class="link" (click)="disconnectIntervals()">Disconnect</button>
            }
          </div>
        }
      }

      <h3>How to connect</h3>
      <ol class="steps">
        <li>
          Sign in to <a href="https://intervals.icu" target="_blank" rel="noopener">intervals.icu</a>. If you don't
          have an account, create one for free.
        </li>
        <li>
          Under <strong>Settings → Connections</strong>, connect <strong>Garmin Connect</strong> (or Wahoo, Coros…)
          so new rides arrive automatically. To bring in older rides, click <strong>Import All Garmin Data</strong>.
        </li>
        <li>
          Still in Settings, scroll down to <strong>Developer Settings</strong> and generate an
          <strong>API key</strong>.
        </li>
        <li>Paste the key below and click <strong>Connect</strong>. The app checks the key with intervals.icu first.</li>
      </ol>

      <form class="row" (ngSubmit)="connectIntervals()">
        <label class="sr-only" for="apiKey">intervals.icu API key</label>
        <input
          id="apiKey"
          name="apiKey"
          type="password"
          [(ngModel)]="apiKey"
          placeholder="API key"
          autocomplete="off"
          required
        />
        <button class="primary" type="submit" [disabled]="savingIntervals() || !apiKey.trim()">
          {{ savingIntervals() ? 'Checking…' : intervals()?.connected ? 'Replace key' : 'Connect' }}
        </button>
      </form>
      @if (intervalsError()) {
        <p class="error">{{ intervalsError() }}</p>
      }

      <details>
        <summary>Good to know</summary>
        <ul>
          <li>The app only reads from intervals.icu. It never changes anything in your account.</li>
          <li>
            Activities are matched to the plan by day and sport. Several rides on one day, like warm-up, race and
            cool-down, count as one session.
          </li>
          <li>Activities without a planned workout are added to that day and marked “unplanned”.</li>
          <li>
            Connect your watch or bike computer directly. Activities that reach intervals.icu only through Strava may
            not be available to other apps.
          </li>
          <li>The key gives access to your intervals.icu data. If it leaks, generate a new one in Developer Settings.</li>
        </ul>
      </details>
    </section>
  `,
  styles: `
    section {
      max-width: 720px;
      display: grid;
      gap: 12px;
      margin-bottom: 16px;
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    h2,
    h3,
    p {
      margin: 0;
    }
    h3 {
      font-size: 1rem;
      margin-top: 4px;
    }
    .state {
      font-size: 0.8rem;
      font-weight: 600;
      padding: 2px 10px;
      border-radius: 999px;
      background: var(--border);
      color: var(--muted);
    }
    .state.on {
      background: var(--ok);
      color: var(--surface);
    }
    .connected {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: 8px;
    }
    .connected > div {
      min-width: 0;
    }
    .url {
      font-family: ui-monospace, Menlo, monospace;
      font-size: 0.85rem;
      overflow-wrap: anywhere;
    }
    .steps {
      margin: 0;
      padding-left: 22px;
      display: grid;
      gap: 6px;
    }
    code {
      font-size: 0.85em;
      overflow-wrap: anywhere;
    }
    .row {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .row input {
      flex: 1 1 280px;
      min-width: 0;
    }
    details ul {
      margin: 8px 0 0;
      padding-left: 22px;
      display: grid;
      gap: 6px;
    }
    summary {
      cursor: pointer;
      font-weight: 600;
    }
    .small {
      font-size: 0.85rem;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `,
})
export class SettingsPage {
  private readonly api = inject(Api);
  protected readonly status = signal<CalendarStatus | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected icalUrl = '';

  protected readonly intervals = signal<IntervalsStatus | null>(null);
  protected readonly savingIntervals = signal(false);
  protected readonly intervalsError = signal('');
  protected apiKey = '';

  constructor() {
    this.api.calendar().subscribe((s) => this.status.set(s));
    this.api.intervals().subscribe((s) => this.intervals.set(s));
  }

  protected connectIntervals() {
    this.savingIntervals.set(true);
    this.intervalsError.set('');
    this.api.connectIntervals(this.apiKey).subscribe({
      next: (s) => {
        this.intervals.set(s);
        this.savingIntervals.set(false);
        this.apiKey = '';
      },
      error: (e: HttpErrorResponse) => {
        this.savingIntervals.set(false);
        const detail: string = e.error?.detail ?? '';
        this.intervalsError.set(
          detail === 'Invalid API key'
            ? 'intervals.icu rejected this key. Copy it again from Settings → Developer Settings.'
            : detail.includes('not reachable')
              ? "intervals.icu can't be reached right now. Try again in a moment."
              : "Couldn't save the key. Is the backend running?",
        );
      },
    });
  }

  protected disconnectIntervals() {
    this.api.disconnectIntervals().subscribe((s) => this.intervals.set(s));
  }

  protected connect() {
    this.saving.set(true);
    this.error.set('');
    this.api.connectCalendar(this.icalUrl).subscribe({
      next: (s) => {
        this.status.set(s);
        this.saving.set(false);
        this.icalUrl = '';
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.explain(e));
      },
    });
  }

  protected disconnect() {
    this.api.disconnectCalendar().subscribe((s) => this.status.set(s));
  }

  private explain(e: HttpErrorResponse): string {
    const detail: string = e.error?.detail ?? '';
    if (detail.startsWith('Not a TrainingPeaks')) {
      return 'This is not a TrainingPeaks calendar link. Copy the link from Settings → Account → Calendar.';
    }
    if (detail.startsWith('Calendar could not')) {
      return 'The calendar could not be downloaded. Check that you copied the whole link and that it is still active.';
    }
    if (detail.includes("doesn't return")) {
      return "This link doesn't return a calendar. Copy the iCal (.ics) link, not the page address.";
    }
    return "Couldn't save the calendar. Is the backend running?";
  }
}
