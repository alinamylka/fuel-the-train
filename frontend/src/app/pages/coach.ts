import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { marked } from 'marked';
import { Api, ChatMessage, Conversation, ConversationSummary } from '../api';

const SUGGESTIONS = [
  'Plan my fuelling for the race weekend: what to take and when',
  'What should I eat before and during tomorrow’s workout?',
  'How much should I drink per hour in a race at 25 °C?',
];

@Component({
  selector: 'app-coach',
  imports: [FormsModule],
  template: `
    <div class="layout">
      <aside class="card side">
        <button class="primary" (click)="startNew()">+ New conversation</button>
        <nav class="list">
          @for (c of conversations(); track c.id) {
            <div class="item" [class.active]="c.id === current()?.id">
              <button class="link title" (click)="open(c.id)">{{ c.title }}</button>
              <button class="link" (click)="remove(c)" aria-label="Delete conversation">✕</button>
            </div>
          } @empty {
            <p class="muted small">No conversations yet.</p>
          }
        </nav>
        <details class="notes" [open]="notesOpen()" (toggle)="notesOpen.set($any($event.target).open)">
          <summary>What the coach knows about you</summary>
          <p class="muted small">
            Always sent to the coach with your plan and targets. Add products you use, what you tolerate in races,
            start times and anything else it should remember.
          </p>
          <textarea id="notes" name="notes" rows="10" [(ngModel)]="notes"></textarea>
          <div class="row">
            <button (click)="saveNotes()" [disabled]="savingNotes()">Save</button>
            @if (notesSaved()) {
              <span class="muted small">Saved</span>
            }
          </div>
        </details>
      </aside>

      <section class="card thread">
        @if (unavailable()) {
          <p class="error">
            The coach can't start: {{ unavailable() }}. It runs through Claude Code on this computer, so Claude Code must
            be installed and signed in.
          </p>
        }

        <div class="messages" #scroller>
          @if (!current()?.messages?.length) {
            <div class="empty">
              <h2>Ask your coach</h2>
              <p class="muted">
                The coach sees your plan, targets, meals and notes, and can look things up on the web. It answers in
                your language.
              </p>
              <div class="suggestions">
                @for (s of suggestions; track s) {
                  <button (click)="draft = s">{{ s }}</button>
                }
              </div>
            </div>
          }
          @for (m of current()?.messages ?? []; track m.id) {
            @if (m.role === 'USER') {
              <div class="user">{{ m.text }}</div>
            } @else {
              <div class="assistant" [class.failed]="m.status === 'FAILED'">
                @if (m.text) {
                  <div class="markdown" [innerHTML]="html(m)"></div>
                }
                @if (m.status === 'RUNNING') {
                  <div class="activity"><span class="pulse"></span>{{ m.activity || (m.text ? 'Writing…' : 'Thinking…') }}</div>
                }
              </div>
            }
          }
        </div>

        <form class="composer" (ngSubmit)="send()">
          <label class="sr-only" for="message">Message</label>
          <textarea
            id="message"
            name="message"
            rows="2"
            [(ngModel)]="draft"
            (keydown.enter)="onEnter($event)"
            placeholder="Ask about your plan, a race or a workout…"
          ></textarea>
          <button class="primary" type="submit" [disabled]="busy() || !draft.trim()">Send</button>
        </form>
        @if (error()) {
          <p class="error small">{{ error() }}</p>
        }
      </section>
    </div>
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: 260px minmax(0, 1fr);
      gap: 16px;
      align-items: start;
    }
    .side {
      display: grid;
      gap: 12px;
      position: sticky;
      top: 16px;
    }
    .list {
      display: grid;
      gap: 2px;
      max-height: 40vh;
      overflow-y: auto;
    }
    .item {
      display: flex;
      align-items: center;
      border-radius: 8px;
    }
    .item.active {
      background: var(--bg);
    }
    .item .title {
      flex: 1;
      min-width: 0;
      text-align: left;
      color: var(--text);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      padding: 6px 8px;
    }
    .notes summary {
      cursor: pointer;
      font-weight: 600;
    }
    .notes {
      display: grid;
      gap: 8px;
    }
    .notes textarea {
      width: 100%;
      font: inherit;
      font-size: 0.85rem;
      margin: 8px 0;
    }
    .row {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .thread {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-height: 70vh;
    }
    .messages {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 14px;
      overflow-y: auto;
      max-height: 68vh;
      padding-right: 4px;
    }
    .empty {
      margin: auto 0;
      display: grid;
      gap: 10px;
      max-width: 560px;
    }
    .empty h2,
    .empty p {
      margin: 0;
    }
    .suggestions {
      display: grid;
      gap: 6px;
    }
    .suggestions button {
      text-align: left;
    }
    .user {
      align-self: flex-end;
      max-width: 80%;
      background: var(--brand);
      color: var(--on-brand);
      padding: 8px 12px;
      border-radius: 12px 12px 2px 12px;
      white-space: pre-wrap;
    }
    .assistant {
      max-width: 100%;
      display: grid;
      gap: 6px;
    }
    .assistant.failed .markdown {
      color: var(--danger);
    }
    .activity {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.85rem;
      color: var(--muted);
      overflow-wrap: anywhere;
    }
    .pulse {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--accent);
      flex: none;
      animation: pulse 1.2s ease-in-out infinite;
    }
    @keyframes pulse {
      50% {
        opacity: 0.3;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .pulse {
        animation: none;
      }
    }
    .markdown {
      line-height: 1.5;
      overflow-wrap: anywhere;
    }
    .markdown :is(h1, h2, h3) {
      font-size: 1rem;
      margin: 12px 0 4px;
    }
    .markdown :is(p, ul, ol) {
      margin: 6px 0;
    }
    .markdown :is(table) {
      display: block;
      overflow-x: auto;
      border-collapse: collapse;
      font-size: 0.9rem;
      margin: 8px 0;
    }
    .markdown :is(th, td) {
      border: 1px solid var(--border);
      padding: 4px 8px;
      text-align: left;
      vertical-align: top;
    }
    .composer {
      display: flex;
      gap: 8px;
      align-items: flex-end;
    }
    .composer textarea {
      flex: 1;
      font: inherit;
      resize: vertical;
      min-height: 44px;
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
    @media (max-width: 760px) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
      }
      .side {
        position: static;
      }
      .list {
        max-height: 160px;
      }
      .messages {
        max-height: none;
      }
    }
  `,
})
export class CoachPage {
  private readonly api = inject(Api);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  protected readonly suggestions = SUGGESTIONS;
  protected readonly conversations = signal<ConversationSummary[]>([]);
  protected readonly current = signal<Conversation | null>(null);
  protected readonly error = signal('');
  protected readonly unavailable = signal<string | null>(null);
  protected readonly notesOpen = signal(false);
  protected readonly savingNotes = signal(false);
  protected readonly notesSaved = signal(false);
  protected readonly busy = computed(() => this.current()?.messages.some((m) => m.status === 'RUNNING') ?? false);
  protected draft = '';
  protected notes = '';
  private poll: ReturnType<typeof setTimeout> | undefined;
  private readonly rendered = new Map<string, string>();

  constructor() {
    this.api.agentStatus().subscribe((s) => this.unavailable.set(s.available ? null : s.problem));
    this.api.agentNotes().subscribe((n) => {
      this.notes = n.text;
      this.notesOpen.set(!n.text.trim());
    });
    this.api.conversations().subscribe((list) => {
      this.conversations.set(list);
      if (list.length) this.open(list[0].id);
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.poll));
  }

  protected html(m: ChatMessage): string {
    const key = `${m.id}:${m.text.length}:${m.status}`;
    let html = this.rendered.get(key);
    if (html === undefined) {
      html = marked.parse(m.text, { async: false, gfm: true, breaks: true });
      this.rendered.set(key, html);
    }
    return html;
  }

  protected open(id: number) {
    clearTimeout(this.poll);
    this.api.conversation(id).subscribe((c) => this.show(c));
  }

  protected startNew() {
    this.api.newConversation().subscribe((c) => {
      this.show(c);
      this.refreshList();
    });
  }

  protected remove(c: ConversationSummary) {
    this.api.deleteConversation(c.id).subscribe(() => {
      if (this.current()?.id === c.id) this.current.set(null);
      this.refreshList();
    });
  }

  protected onEnter(event: Event) {
    const key = event as KeyboardEvent;
    if (key.shiftKey || key.isComposing) return;
    event.preventDefault();
    this.send();
  }

  protected send() {
    const text = this.draft.trim();
    if (!text || this.busy()) return;
    this.error.set('');
    const target = this.current();
    const post = (id: number) =>
      this.api.sendMessage(id, text).subscribe({
        next: (c) => {
          this.draft = '';
          this.show(c);
          this.refreshList();
        },
        error: (e: HttpErrorResponse) =>
          this.error.set(e.status === 409 ? 'The coach is still answering.' : 'Couldn’t send the message.'),
      });
    if (target) {
      post(target.id);
    } else {
      this.api.newConversation().subscribe((c) => post(c.id));
    }
  }

  protected saveNotes() {
    this.savingNotes.set(true);
    this.api.saveAgentNotes(this.notes).subscribe({
      next: () => {
        this.savingNotes.set(false);
        this.notesSaved.set(true);
        setTimeout(() => this.notesSaved.set(false), 2000);
      },
      error: () => this.savingNotes.set(false),
    });
  }

  private show(c: Conversation) {
    const before = this.current()?.messages.map((m) => m.text.length).join();
    this.current.set(c);
    if (before !== c.messages.map((m) => m.text.length).join()) this.scrollToEnd();
    clearTimeout(this.poll);
    if (c.messages.some((m) => m.status === 'RUNNING')) {
      this.poll = setTimeout(() => this.api.conversation(c.id).subscribe((next) => this.show(next)), 1500);
    }
  }

  private refreshList() {
    this.api.conversations().subscribe((list) => this.conversations.set(list));
  }

  private scrollToEnd() {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
