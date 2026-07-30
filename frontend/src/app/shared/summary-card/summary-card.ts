import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-summary-card',
  template: `
    <article class="summary-card">
      <span>{{ label() }}</span>
      <strong>{{ value() }}</strong>
    </article>
  `,
  styleUrl: './summary-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SummaryCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
}
