import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Local calendar date as yyyy-mm-dd, the format the backend uses for LocalDate. */
export function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export type Sex = 'FEMALE' | 'MALE';
export type Goal = 'MAINTAIN' | 'LOSE' | 'GAIN';
export type Sport = 'BIKE' | 'RUN' | 'SWIM' | 'WALK' | 'STRENGTH' | 'DAY_OFF' | 'EVENT' | 'OTHER';
export type Intensity = 'REST' | 'RECOVERY' | 'ENDURANCE' | 'TEMPO' | 'THRESHOLD' | 'VO2MAX' | 'RACE';
export type MealType =
  | 'BREAKFAST'
  | 'SNACK'
  | 'LUNCH'
  | 'PRE_WORKOUT'
  | 'DURING_WORKOUT'
  | 'POST_WORKOUT'
  | 'DINNER';

export const INTENSITIES: Intensity[] = ['REST', 'RECOVERY', 'ENDURANCE', 'TEMPO', 'THRESHOLD', 'VO2MAX', 'RACE'];

export const MEAL_TYPES: { value: MealType; label: string }[] = [
  { value: 'BREAKFAST', label: 'Breakfast' },
  { value: 'SNACK', label: 'Snack' },
  { value: 'LUNCH', label: 'Lunch' },
  { value: 'PRE_WORKOUT', label: 'Pre-workout' },
  { value: 'DURING_WORKOUT', label: 'During workout' },
  { value: 'POST_WORKOUT', label: 'Post-workout' },
  { value: 'DINNER', label: 'Dinner' },
];

export interface Profile {
  id?: number;
  /** First day the values apply; the backend uses today when it's missing. */
  validFrom?: string;
  weightKg: number;
  heightCm: number;
  birthYear: number;
  sex: Sex;
  ftpWatts: number | null;
  goal: Goal;
  proteinGPerKg: number;
}

export interface Workout {
  id: number;
  date: string;
  title: string;
  description: string;
  sport: Sport;
  plannedMinutes: number | null;
  actualMinutes: number | null;
  actualDistanceKm: number | null;
  intensity: Intensity;
  intensityOverridden: boolean;
  /** Measured work from intervals.icu; 0 when the session was recorded as part of another one that day. */
  actualKj: number | null;
  /** Recorded in intervals.icu without a planned workout. */
  unplanned: boolean;
}

export interface WorkoutEnergy {
  workoutId: number | null;
  kcal: number;
  carbsPerHourDuring: number;
}

export interface DailyTargets {
  kcal: number;
  carbsG: number;
  proteinG: number;
  fatG: number;
  carbsGPerKg: number;
  workoutKcal: number;
  workouts: WorkoutEnergy[];
  raceDay: boolean;
  dayBeforeRace: boolean;
}

export interface Meal {
  id?: number;
  date: string;
  type: MealType;
  description: string;
  kcal: number;
  carbsG: number;
  proteinG: number;
  fatG: number;
}

export interface Intake {
  kcal: number;
  carbsG: number;
  proteinG: number;
  fatG: number;
}

export interface Day {
  date: string;
  workouts: Workout[];
  targets: DailyTargets;
  meals: Meal[];
  eaten: Intake;
}

export interface FavoriteProduct {
  id?: number;
  name: string;
  kcalPer100g: number;
  carbsPer100g: number;
  proteinPer100g: number;
  fatPer100g: number;
  notes: string;
}

export interface CalendarStatus {
  connected: boolean;
  /** APP = saved in the app, ENV = TP_ICAL_URL from .env */
  source: 'APP' | 'ENV' | null;
  maskedUrl: string | null;
  /** Number of calendar entries, only right after connecting. */
  events: number | null;
}

export interface IntervalsStatus {
  connected: boolean;
  source: 'APP' | 'ENV' | null;
  athleteName: string | null;
}

export interface SyncResult {
  created: number;
  updated: number;
  /** Null when intervals.icu isn't connected. */
  activities: number | null;
}

export interface ConversationSummary {
  id: number;
  title: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  text: string;
  status: 'DONE' | 'RUNNING' | 'FAILED';
  /** What the agent is doing right now, e.g. "Searching the web: …". */
  activity: string | null;
}

export interface Conversation {
  id: number;
  title: string;
  messages: ChatMessage[];
}

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  profile() {
    return this.http.get<Profile>('/api/profile');
  }

  saveProfile(profile: Profile) {
    return this.http.put<Profile>('/api/profile', profile);
  }

  profileHistory() {
    return this.http.get<Profile[]>('/api/profile/history');
  }

  deleteProfileVersion(id: number) {
    return this.http.delete<void>(`/api/profile/${id}`);
  }

  days(from: string, to: string) {
    return this.http.get<Day[]>('/api/days', { params: { from, to } });
  }

  calendar() {
    return this.http.get<CalendarStatus>('/api/calendar');
  }

  connectCalendar(icalUrl: string) {
    return this.http.put<CalendarStatus>('/api/calendar', { icalUrl });
  }

  disconnectCalendar() {
    return this.http.delete<CalendarStatus>('/api/calendar');
  }

  intervals() {
    return this.http.get<IntervalsStatus>('/api/intervals');
  }

  connectIntervals(apiKey: string) {
    return this.http.put<IntervalsStatus>('/api/intervals', { apiKey });
  }

  disconnectIntervals() {
    return this.http.delete<IntervalsStatus>('/api/intervals');
  }

  agentStatus() {
    return this.http.get<{ available: boolean; problem: string | null }>('/api/chat/status');
  }

  conversations() {
    return this.http.get<ConversationSummary[]>('/api/chat/conversations');
  }

  conversation(id: number) {
    return this.http.get<Conversation>(`/api/chat/conversations/${id}`);
  }

  newConversation() {
    return this.http.post<Conversation>('/api/chat/conversations', {});
  }

  deleteConversation(id: number) {
    return this.http.delete<void>(`/api/chat/conversations/${id}`);
  }

  sendMessage(id: number, text: string) {
    return this.http.post<Conversation>(`/api/chat/conversations/${id}/messages`, { text });
  }

  agentNotes() {
    return this.http.get<{ text: string }>('/api/chat/notes');
  }

  saveAgentNotes(text: string) {
    return this.http.put<{ text: string }>('/api/chat/notes', { text });
  }

  syncWorkouts() {
    return this.http.post<SyncResult>('/api/workouts/sync', {});
  }

  setIntensity(workoutId: number, intensity: Intensity) {
    return this.http.put<Workout>(`/api/workouts/${workoutId}/intensity`, { intensity });
  }

  addMeal(meal: Meal) {
    return this.http.post<Meal>('/api/meals', meal);
  }

  deleteMeal(id: number) {
    return this.http.delete<void>(`/api/meals/${id}`);
  }

  favorites() {
    return this.http.get<FavoriteProduct[]>('/api/favorites');
  }

  addFavorite(product: FavoriteProduct) {
    return this.http.post<FavoriteProduct>('/api/favorites', product);
  }

  updateFavorite(product: FavoriteProduct) {
    return this.http.put<FavoriteProduct>(`/api/favorites/${product.id}`, product);
  }

  deleteFavorite(id: number) {
    return this.http.delete<void>(`/api/favorites/${id}`);
  }
}
