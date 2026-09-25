import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, FavoriteProduct } from '../api';

const emptyProduct = (): FavoriteProduct => ({
  name: '',
  kcalPer100g: 0,
  carbsPer100g: 0,
  proteinPer100g: 0,
  fatPer100g: 0,
  notes: '',
});

@Component({
  selector: 'app-favorites',
  imports: [FormsModule],
  template: `
    <h1>Favorite foods</h1>
    <p class="muted">Values per 100 g. The meal planner will prefer these foods.</p>

    <form class="card form" (ngSubmit)="save()">
      <label class="name">Name <input name="name" [(ngModel)]="draft.name" required /></label>
      <label>kcal <input type="number" step="any" name="kcal" [(ngModel)]="draft.kcalPer100g" /></label>
      <label>Carbs (g) <input type="number" step="any" name="carbs" [(ngModel)]="draft.carbsPer100g" /></label>
      <label>Protein (g) <input type="number" step="any" name="protein" [(ngModel)]="draft.proteinPer100g" /></label>
      <label>Fat (g) <input type="number" step="any" name="fat" [(ngModel)]="draft.fatPer100g" /></label>
      <label class="name">Note <input name="notes" [(ngModel)]="draft.notes" placeholder="e.g. breakfast before a long ride" /></label>
      <div class="actions">
        <button class="primary" type="submit" [disabled]="!draft.name.trim()">
          {{ draft.id ? 'Save changes' : 'Add' }}
        </button>
        @if (draft.id) {
          <button type="button" (click)="reset()">Cancel</button>
        }
      </div>
    </form>

    <div class="list">
      @for (p of products(); track p.id) {
        <div class="card row">
          <div>
            <strong>{{ p.name }}</strong>
            <div class="muted small">
              {{ p.kcalPer100g }} kcal · C {{ p.carbsPer100g }} g · P {{ p.proteinPer100g }} g · F {{ p.fatPer100g }} g
              @if (p.notes) {
                · {{ p.notes }}
              }
            </div>
          </div>
          <div>
            <button class="link" (click)="edit(p)">edit</button>
            <button class="link" (click)="remove(p)">delete</button>
          </div>
        </div>
      } @empty {
        <p class="muted">No favorite foods yet.</p>
      }
    </div>
  `,
  styles: `
    .form {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .name {
      grid-column: 1 / -1;
    }
    .actions {
      grid-column: 1 / -1;
      display: flex;
      gap: 8px;
    }
    .list {
      display: grid;
      gap: 8px;
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
    @media (max-width: 600px) {
      .form {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `,
})
export class FavoritesPage {
  private readonly api = inject(Api);
  protected readonly products = signal<FavoriteProduct[]>([]);
  protected draft = emptyProduct();

  constructor() {
    this.load();
  }

  protected save() {
    const request = this.draft.id ? this.api.updateFavorite(this.draft) : this.api.addFavorite(this.draft);
    request.subscribe(() => {
      this.reset();
      this.load();
    });
  }

  protected edit(product: FavoriteProduct) {
    this.draft = { ...product };
  }

  protected reset() {
    this.draft = emptyProduct();
  }

  protected remove(product: FavoriteProduct) {
    this.api.deleteFavorite(product.id!).subscribe(() => this.load());
  }

  private load() {
    this.api.favorites().subscribe((list) => this.products.set(list));
  }
}
