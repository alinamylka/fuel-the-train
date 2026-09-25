import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Goal, Profile, isoDate } from '../api';

const GOAL_LABELS: Record<Goal, string> = {
  MAINTAIN: 'Maintain',
  LOSE: 'Lose weight',
  GAIN: 'Gain weight',
};

@Component({
  selector: 'app-profile',
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <h1>Profile</h1>
    @if (profile(); as p) {
      <form class="card grid" (ngSubmit)="save(p)">
        <label>
          Valid from
          <input type="date" name="validFrom" [(ngModel)]="p.validFrom" required />
          <span class="hint">Earlier days keep the previous values</span>
        </label>
        <label>Weight (kg) <input type="number" step="0.1" name="weightKg" [(ngModel)]="p.weightKg" required /></label>
        <label>Height (cm) <input type="number" name="heightCm" [(ngModel)]="p.heightCm" required /></label>
        <label>Year of birth <input type="number" name="birthYear" [(ngModel)]="p.birthYear" required /></label>
        <label>
          Sex
          <select name="sex" [(ngModel)]="p.sex">
            <option value="FEMALE">Female</option>
            <option value="MALE">Male</option>
          </select>
        </label>
        <label>
          FTP (W)
          <input type="number" name="ftpWatts" [(ngModel)]="p.ftpWatts" placeholder="unknown" />
          <span class="hint">Empty = estimated as 3 W/kg</span>
        </label>
        <label>
          Goal
          <select name="goal" [(ngModel)]="p.goal">
            <option value="MAINTAIN">Maintain weight</option>
            <option value="LOSE">Lose weight (no deficit on hard days)</option>
            <option value="GAIN">Gain weight</option>
          </select>
        </label>
        <label>
          Protein (g/kg)
          <input type="number" step="0.1" name="proteinGPerKg" [(ngModel)]="p.proteinGPerKg" required />
        </label>
        <div class="actions">
          <button class="primary" type="submit" [disabled]="saving()">Save</button>
          @if (message()) {
            <span [class.error]="failed()">{{ message() }}</span>
          }
        </div>
      </form>
    }

    <h2>History</h2>
    <p class="muted">Each day's targets use the profile that was valid on that day. Saving with the same date corrects that version.</p>
    <div class="list">
      @for (v of history(); track v.id) {
        <div class="card row">
          <div>
            <strong>from {{ v.validFrom | date: 'd MMM y' }}</strong>
            @if (v.id === currentId()) {
              <span class="badge">current</span>
            }
            <div class="muted small">
              {{ v.weightKg | number: '1.0-1' }} kg · FTP {{ v.ftpWatts ? v.ftpWatts + ' W' : 'estimated' }} ·
              {{ goalLabels[v.goal] }} · protein {{ v.proteinGPerKg | number: '1.1-1' }} g/kg
            </div>
          </div>
          <div>
            <button class="link" (click)="edit(v)">edit</button>
            <button class="link" (click)="remove(v)" [disabled]="history().length < 2">delete</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      max-width: 700px;
      margin-bottom: 24px;
    }
    .hint {
      font-size: 0.75rem;
    }
    .actions {
      grid-column: 1 / -1;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .list {
      display: grid;
      gap: 8px;
      max-width: 700px;
    }
    .row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 10px 16px;
    }
    .small {
      font-size: 0.85rem;
    }
    .badge {
      margin-left: 8px;
      font-size: 0.75rem;
      padding: 1px 8px;
      border-radius: 999px;
      background: var(--accent);
      color: var(--brand);
    }
  `,
})
export class ProfilePage {
  private readonly api = inject(Api);
  protected readonly goalLabels = GOAL_LABELS;
  protected readonly profile = signal<Profile | null>(null);
  protected readonly history = signal<Profile[]>([]);
  protected readonly saving = signal(false);
  protected readonly message = signal('');
  protected readonly failed = signal(false);

  /** History is newest first, so the first version that already started is the current one. */
  protected readonly currentId = computed(() => {
    const today = isoDate(new Date());
    const versions = this.history();
    return (versions.find((v) => v.validFrom! <= today) ?? versions.at(-1))?.id;
  });

  constructor() {
    this.load();
  }

  protected save(profile: Profile) {
    this.saving.set(true);
    const payload = { ...profile, id: undefined, ftpWatts: profile.ftpWatts || null };
    this.api.saveProfile(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.failed.set(false);
        this.message.set('Saved');
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.failed.set(true);
        this.message.set('Couldn\'t save, check the values');
      },
    });
  }

  protected edit(version: Profile) {
    this.profile.set({ ...version });
    this.message.set('');
  }

  protected remove(version: Profile) {
    this.api.deleteProfileVersion(version.id!).subscribe(() => this.load());
  }

  /** The form starts from the current values, dated today, so saving adds a new version. */
  private load() {
    this.api.profile().subscribe((p) => this.profile.set({ ...p, validFrom: isoDate(new Date()) }));
    this.api.profileHistory().subscribe((list) => this.history.set(list));
  }
}
