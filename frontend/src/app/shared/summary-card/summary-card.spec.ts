import { TestBed } from '@angular/core/testing';

import { SummaryCard } from './summary-card';

describe('SummaryCard', () => {
  it('should render a shared operational indicator', async () => {
    await TestBed.configureTestingModule({
      imports: [SummaryCard]
    }).compileComponents();

    const fixture = TestBed.createComponent(SummaryCard);
    fixture.componentRef.setInput('label', 'Trenes en servicio');
    fixture.componentRef.setInput('value', 14);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.summary-card') as HTMLElement;
    expect(card.querySelector('span')?.textContent?.trim()).toBe('Trenes en servicio');
    expect(card.querySelector('strong')?.textContent?.trim()).toBe('14');
  });
});
