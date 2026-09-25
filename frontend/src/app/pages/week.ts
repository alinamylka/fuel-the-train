import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Day, FavoriteProduct, INTENSITIES, Intensity, MEAL_TYPES, Meal, MealType, Workout, isoDate } from '../api';

const INTENSITY_LABELS: Record<Intensity, string> = {
  REST: 'Rest',
  RECOVERY: 'Recovery',
  ENDURANCE: 'Endurance',
  TEMPO: 'Tempo',
  THRESHOLD: 'Threshold / FTP',
  VO2MAX: 'VO2max',
  RACE: 'Race',
};

function mondayOf(date: Date): Date {
  const monday = new Date(date);
  monday.setHours(0, 0, 0, 0);
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
  return monday;
}

function formatMinutes(minutes: number | null): string {
  if (!minutes) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h ? `${h}h${m ? ' ' + String(m).padStart(2, '0') : ''}` : `${m} min`;
}

@Component({
  selector: 'app-day-card',
  imports: [DatePipe, FormsModule],
  template: `
    @let d = day();
    @let t = d.targets;
    <article class="card day" [class.today]="isToday()">
      <header>
        <div>
          <strong class="weekday">{{ d.date | date: 'EEEE' }}</strong>
          <span class="muted">{{ d.date | date: 'd MMM' }}</span>
        </div>
        @if (t.raceDay) {
          <span class="badge race">Race day</span>
        } @else if (t.dayBeforeRace) {
          <span class="badge load">Carb loading</span>
        }
      </header>

      <section class="workouts">
        @for (w of d.workouts; track w.id) {
          <div class="workout" [title]="w.description">
            <div class="workout-title">
              {{ w.title }}
              @if (w.unplanned) {
                <span class="tag">unplanned</span>
              }
            </div>
            <div class="workout-meta">
              @if (w.plannedMinutes || w.actualMinutes) {
                <span>{{ minutes(w) }}</span>
              }
              @if (w.actualKj === 0) {
                <span>recorded with the main session</span>
              } @else if (energyOf(w); as e) {
                @if (w.actualKj) {
                  <span class="measured" title="Measured by your power meter (intervals.icu)">{{ w.actualKj }} kJ</span>
                } @else if (e.kcal) {
                  <span title="Estimated from FTP, intensity and duration">~{{ e.kcal }} kcal</span>
                }
                @if (e.carbsPerHourDuring) {
                  <span class="fuel">{{ e.carbsPerHourDuring }} g carbs/h</span>
                }
              }
              <select
                class="intensity"
                [class.overridden]="w.intensityOverridden"
                [ngModel]="w.intensity"
                (ngModelChange)="intensityChange.emit({ workout: w, intensity: $event })"
                aria-label="Intensity"
              >
                @for (i of intensities; track i) {
                  <option [value]="i">{{ labels[i] }}</option>
                }
              </select>
            </div>
          </div>
        } @empty {
          <div class="muted">No workout</div>
        }
      </section>

      <section class="targets">
        <div class="kcal">
          <strong>{{ d.eaten.kcal }}</strong> / {{ t.kcal }} kcal
          <span class="muted">· {{ t.carbsGPerKg }} g/kg carbs</span>
        </div>
        @for (m of macros(); track m.label) {
          <div class="macro">
            <span class="macro-label">{{ m.label }}</span>
            <div class="bar" [attr.aria-label]="m.label + ' ' + m.eaten + ' of ' + m.target + ' g'">
              <div class="fill" [class.over]="m.eaten > m.target * 1.1" [style.width.%]="m.percent"></div>
            </div>
            <span class="macro-value">{{ m.eaten }}/{{ m.target }} g</span>
          </div>
        }
      </section>

      <section class="meals">
        @for (meal of d.meals; track meal.id) {
          <div class="meal">
            <span>
              <span class="muted">{{ mealLabel(meal.type) }}:</span> {{ meal.description }}
              <span class="muted small">({{ meal.kcal }} kcal, C {{ meal.carbsG }} g)</span>
            </span>
            <button class="link" (click)="deleteMeal.emit(meal)" aria-label="Delete meal">✕</button>
          </div>
        }

        @if (adding()) {
          <form class="meal-form" (ngSubmit)="submit()">
            <select name="type" [(ngModel)]="draft.type">
              @for (t of mealTypes; track t.value) {
                <option [value]="t.value">{{ t.label }}</option>
              }
            </select>
            @if (favorites().length) {
              <div class="from-favorite">
                <select name="favorite" [(ngModel)]="favoriteId" (ngModelChange)="applyFavorite()">
                  <option [ngValue]="null">— from favorites —</option>
                  @for (f of favorites(); track f.id) {
                    <option [ngValue]="f.id">{{ f.name }}</option>
                  }
                </select>
                <input type="number" name="grams" [(ngModel)]="grams" (ngModelChange)="applyFavorite()" aria-label="Grams" />
                <span class="muted">g</span>
              </div>
            }
            <input name="description" [(ngModel)]="draft.description" placeholder="What did you eat?" required />
            <div class="numbers">
              <label>kcal <input type="number" step="any" name="kcal" [(ngModel)]="draft.kcal" /></label>
              <label>C <input type="number" step="any" name="carbs" [(ngModel)]="draft.carbsG" /></label>
              <label>P <input type="number" step="any" name="protein" [(ngModel)]="draft.proteinG" /></label>
              <label>F <input type="number" step="any" name="fat" [(ngModel)]="draft.fatG" /></label>
            </div>
            <div class="form-actions">
              <button class="primary" type="submit" [disabled]="!draft.description.trim()">Add</button>
              <button type="button" (click)="adding.set(false)">Cancel</button>
            </div>
          </form>
        } @else {
          <button class="add" (click)="startAdding()">+ Add meal</button>
        }
      </section>
    </article>
  `,
  styles: `
    .day {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .day.today {
      border-color: var(--accent);
      box-shadow: 0 0 0 1px var(--accent);
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .weekday {
      text-transform: capitalize;
      margin-right: 6px;
    }
    .badge {
      font-size: 0.75rem;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 999px;
    }
    .badge.race {
      background: var(--brand);
      color: var(--on-brand);
    }
    .badge.load {
      background: var(--accent);
      color: #1b2626;
    }
    .workouts {
      display: grid;
      gap: 8px;
    }
    .workout-title {
      font-weight: 600;
    }
    .workout-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 10px;
      align-items: center;
      font-size: 0.85rem;
      color: var(--muted);
    }
    .measured {
      color: var(--ok);
      font-weight: 600;
    }
    .tag {
      font-size: 0.7rem;
      font-weight: 500;
      padding: 1px 6px;
      margin-left: 4px;
      border-radius: 999px;
      border: 1px solid var(--border);
      color: var(--muted);
      vertical-align: middle;
    }
    .fuel {
      color: var(--accent-strong);
      font-weight: 600;
    }
    .intensity {
      font-size: 0.8rem;
      padding: 2px 4px;
    }
    .intensity.overridden {
      border-color: var(--accent);
    }
    .targets {
      display: grid;
      gap: 4px;
      padding-top: 8px;
      border-top: 1px solid var(--border);
    }
    .macro {
      display: grid;
      grid-template-columns: 16px 1fr auto;
      gap: 8px;
      align-items: center;
      font-size: 0.85rem;
    }
    .macro-label {
      font-weight: 600;
    }
    .macro-value {
      color: var(--muted);
      font-variant-numeric: tabular-nums;
    }
    .bar {
      height: 8px;
      border-radius: 4px;
      background: var(--border);
      overflow: hidden;
    }
    .fill {
      height: 100%;
      background: var(--brand);
      border-radius: 4px;
    }
    @media (prefers-color-scheme: dark) {
      .fill {
        background: var(--ok);
      }
    }
    .fill.over {
      background: var(--accent);
    }
    .meals {
      display: grid;
      gap: 6px;
      padding-top: 8px;
      border-top: 1px solid var(--border);
      font-size: 0.9rem;
    }
    .meal {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .small {
      font-size: 0.8rem;
    }
    .add {
      justify-self: start;
    }
    .meal-form {
      display: grid;
      gap: 8px;
    }
    .from-favorite {
      display: grid;
      grid-template-columns: 1fr 70px auto;
      gap: 6px;
      align-items: center;
    }
    .numbers {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 6px;
    }
    .form-actions {
      display: flex;
      gap: 8px;
    }
  `,
})
export class DayCard {
  readonly day = input.required<Day>();
  readonly favorites = input<FavoriteProduct[]>([]);
  readonly intensityChange = output<{ workout: Workout; intensity: Intensity }>();
  readonly addMeal = output<Meal>();
  readonly deleteMeal = output<Meal>();

  protected readonly intensities = INTENSITIES;
  protected readonly labels = INTENSITY_LABELS;
  protected readonly mealTypes = MEAL_TYPES;
  protected readonly adding = signal(false);
  protected draft = this.emptyMeal();
  protected favoriteId: number | null = null;
  protected grams = 100;

  protected readonly isToday = computed(() => this.day().date === isoDate(new Date()));

  protected readonly macros = computed(() => {
    const { targets: t, eaten: e } = this.day();
    return [
      { label: 'C', eaten: e.carbsG, target: t.carbsG },
      { label: 'P', eaten: e.proteinG, target: t.proteinG },
      { label: 'F', eaten: e.fatG, target: t.fatG },
    ].map((m) => ({ ...m, percent: m.target ? Math.min(100, (m.eaten / m.target) * 100) : 0 }));
  });

  protected minutes(w: Workout) {
    return formatMinutes(w.actualMinutes ?? w.plannedMinutes);
  }

  protected energyOf(w: Workout) {
    return this.day().targets.workouts.find((e) => e.workoutId === w.id);
  }

  protected mealLabel(type: MealType) {
    return MEAL_TYPES.find((t) => t.value === type)?.label ?? type;
  }

  protected startAdding() {
    this.draft = this.emptyMeal();
    this.favoriteId = null;
    this.grams = 100;
    this.adding.set(true);
  }

  protected applyFavorite() {
    const product = this.favorites().find((f) => f.id === this.favoriteId);
    if (!product) return;
    const factor = (this.grams || 0) / 100;
    const round = (value: number) => Math.round(value * factor * 10) / 10;
    this.draft = {
      ...this.draft,
      description: `${product.name} ${this.grams} g`,
      kcal: Math.round(product.kcalPer100g * factor),
      carbsG: round(product.carbsPer100g),
      proteinG: round(product.proteinPer100g),
      fatG: round(product.fatPer100g),
    };
  }

  protected submit() {
    this.addMeal.emit({ ...this.draft, date: this.day().date });
    this.adding.set(false);
  }

  private emptyMeal(): Meal {
    return { date: '', type: 'BREAKFAST', description: '', kcal: 0, carbsG: 0, proteinG: 0, fatG: 0 };
  }
}

@Component({
  selector: 'app-week',
  imports: [DatePipe, DayCard, RouterLink],
  template: `
    <div class="toolbar">
      <div class="nav">
        <button (click)="shiftWeek(-1)" aria-label="Previous week">←</button>
        <button (click)="goToday()">Today</button>
        <button (click)="shiftWeek(1)" aria-label="Next week">→</button>
        <h1>{{ monday() | date: 'd MMM' }} – {{ sunday() | date: 'd MMM y' }}</h1>
      </div>
      <div class="sync">
        @if (noCalendar()) {
          <span class="muted"><a routerLink="/settings">Connect TrainingPeaks or intervals.icu</a> first</span>
        } @else if (syncMessage()) {
          <span class="muted" [class.error]="syncFailed()">{{ syncMessage() }}</span>
        }
        <button class="primary" (click)="sync()" [disabled]="syncing()">
          {{ syncing() ? 'Syncing…' : 'Sync' }}
        </button>
      </div>
    </div>

    @if (summary(); as s) {
      <p class="muted summary">
        Week: {{ s.workoutKcal }} kcal in workouts · {{ s.avgKcal }} kcal/day on average ·
        eaten {{ s.eatenKcal }} of {{ s.targetKcal }} kcal
      </p>
    }

    @if (loadError()) {
      <p class="error">Can't reach the backend. Is it running on port 8765?</p>
    }

    <div class="days">
      @for (day of days(); track day.date) {
        <app-day-card
          [day]="day"
          [favorites]="favorites()"
          (intensityChange)="setIntensity($event.workout, $event.intensity)"
          (addMeal)="addMeal($event)"
          (deleteMeal)="deleteMeal($event)"
        />
      }
    </div>
  `,
  styles: `
    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 8px;
    }
    .nav {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .nav h1 {
      margin: 0 0 0 8px;
      font-size: 1.3rem;
    }
    .sync {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .summary {
      margin: 0 0 16px;
    }
    .days {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 12px;
    }
  `,
})
export class WeekPage {
  private readonly api = inject(Api);

  protected readonly monday = signal(mondayOf(new Date()));
  protected readonly sunday = computed(() => {
    const sunday = new Date(this.monday());
    sunday.setDate(sunday.getDate() + 6);
    return sunday;
  });
  protected readonly days = signal<Day[]>([]);
  protected readonly favorites = signal<FavoriteProduct[]>([]);
  protected readonly loadError = signal(false);
  protected readonly syncing = signal(false);
  protected readonly syncMessage = signal('');
  protected readonly syncFailed = signal(false);
  protected readonly noCalendar = signal(false);

  protected readonly summary = computed(() => {
    const days = this.days();
    if (!days.length) return null;
    const targetKcal = days.reduce((sum, d) => sum + d.targets.kcal, 0);
    return {
      workoutKcal: days.reduce((sum, d) => sum + d.targets.workoutKcal, 0),
      targetKcal,
      avgKcal: Math.round(targetKcal / days.length),
      eatenKcal: days.reduce((sum, d) => sum + d.eaten.kcal, 0),
    };
  });

  constructor() {
    this.load();
    this.api.favorites().subscribe((list) => this.favorites.set(list));
  }

  protected shiftWeek(weeks: number) {
    const monday = new Date(this.monday());
    monday.setDate(monday.getDate() + weeks * 7);
    this.monday.set(monday);
    this.load();
  }

  protected goToday() {
    this.monday.set(mondayOf(new Date()));
    this.load();
  }

  protected sync() {
    this.syncing.set(true);
    this.api.syncWorkouts().subscribe({
      next: (result) => {
        this.syncing.set(false);
        this.noCalendar.set(false);
        this.syncFailed.set(false);
        this.syncMessage.set(
          `Plan: ${result.created} new, ${result.updated} updated` +
            (result.activities === null ? '' : ` · ${result.activities} activities`),
        );
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.syncing.set(false);
        this.syncFailed.set(true);
        this.noCalendar.set(error.status === 409);
        this.syncMessage.set('Sync failed. Check the connections in Settings.');
      },
    });
  }

  protected setIntensity(workout: Workout, intensity: Intensity) {
    this.api.setIntensity(workout.id, intensity).subscribe(() => this.load());
  }

  protected addMeal(meal: Meal) {
    this.api.addMeal(meal).subscribe(() => this.load());
  }

  protected deleteMeal(meal: Meal) {
    this.api.deleteMeal(meal.id!).subscribe(() => this.load());
  }

  private load() {
    this.api.days(isoDate(this.monday()), isoDate(this.sunday())).subscribe({
      next: (days) => {
        this.loadError.set(false);
        this.days.set(days);
      },
      error: () => this.loadError.set(true),
    });
  }
}
