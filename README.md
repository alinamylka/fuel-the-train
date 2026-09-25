# Fuel the Train

Meal planning around training load. Workouts come from the TrainingPeaks calendar, daily energy and macro targets are calculated from them, and you log what you actually ate.

- `backend/` – Kotlin, Spring Boot 4, H2 file database (`backend/data/`)
- `frontend/` – Angular

## Setup

Create `.env` in the project root (it's git-ignored):

```
TP_ICAL_URL=webcal://www.trainingpeaks.com/ical/<your-id>.ics
```

You'll find the link in TrainingPeaks under Settings → Calendar sync (iCal).

## Run

```bash
# backend, http://localhost:8765
cd backend && ./gradlew bootRun

# frontend, http://localhost:4300 (proxies /api to the backend)
cd frontend && npm install && npm start
```

Tests: `cd backend && ./gradlew test`

## How targets are calculated

- **Workout energy (bike):** FTP × average intensity factor × duration. On the bike 1 kJ of work ≈ 1 kcal burned. Without FTP, 3 W/kg is assumed.
- **Intensity:** guessed from the coach's title and description (zones S1–S6, FTP, LT1, …). It can be corrected per workout in the app and survives re-sync.
- **Carbs:** 3 g/kg base plus the carbohydrate share of workout energy, 3–12 g/kg. At least 8 g/kg the day before a race and 7 g/kg on race day.
- **Protein:** from the profile (default 1.8 g/kg).
- **Fat:** the rest of the energy, never below 0.8 g/kg.
- **Energy:** Mifflin-St Jeor × 1.4 + workouts. With the weight loss goal: −300 kcal, but not on hard days (≥7 g/kg carbs) or around races.
- **During workout:** 40–90 g carbs/h for sessions over 75 min, depending on intensity.

## Next

- AI chat: meal plans and prep based on targets, favorites and history
- Activities from intervals.icu (actual kJ instead of estimates)
- Garmin Connect API once the developer account is sorted out
