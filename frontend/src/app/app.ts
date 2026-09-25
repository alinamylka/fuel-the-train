import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <a routerLink="/" class="brand">
        <img src="logo.svg" alt="" width="32" height="32" />
        <span><span class="accent">Fuel</span> the Train</span>
      </a>
      <nav>
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Week</a>
        <a routerLink="/coach" routerLinkActive="active">Coach</a>
        <a routerLink="/favorites" routerLinkActive="active">Favorites</a>
        <a routerLink="/profile" routerLinkActive="active">Profile</a>
        <a routerLink="/settings" routerLinkActive="active">Settings</a>
      </nav>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      padding: 12px 16px;
      background: var(--brand);
      color: var(--on-brand);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      color: inherit;
      text-decoration: none;
      font-weight: 700;
      font-size: 1.15rem;
    }
    .brand img {
      border-radius: 8px;
    }
    .accent {
      color: var(--accent);
    }
    nav {
      display: flex;
      gap: 4px;
    }
    nav a {
      color: inherit;
      text-decoration: none;
      padding: 6px 12px;
      border-radius: 999px;
      opacity: 0.8;
    }
    nav a.active {
      background: rgb(255 255 255 / 0.14);
      opacity: 1;
    }
    main {
      max-width: 1100px;
      margin: 0 auto;
      padding: 16px;
    }
  `,
})
export class App {}
