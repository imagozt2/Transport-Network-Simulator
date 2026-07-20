import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NetworkMapResponse } from '../../core/models/network-map.model';
import { NetworkMapService } from '../../core/services/network-map.service';
import { NetworkMap } from './network-map';

const response: NetworkMapResponse = {
  lines: [
    {
      id: 1,
      code: 'L1',
      name: 'Línea 1',
      color: 'Roja',
      stations: [
        { id: 45, code: 'ST045', name: 'Los Molinos', stationOrder: 1 },
        { id: 43, code: 'ST043', name: 'Cuatro Caminos', stationOrder: 2 }
      ]
    },
    {
      id: 2,
      code: 'L2',
      name: 'Línea 2',
      color: 'Verde',
      stations: [{ id: 43, code: 'ST043', name: 'Cuatro Caminos', stationOrder: 1 }]
    }
  ]
};

describe('NetworkMap', () => {
  it('should combine API stations with the visual map by station code', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('.metro-line')).toHaveLength(6);
    expect(compiled.querySelectorAll('.line-accordion')).toHaveLength(2);
    expect(compiled.textContent).toContain('Línea 1');
    expect(compiled.querySelectorAll('.station-transfer-ring')).toHaveLength(1);
    expect(compiled.querySelector('.station-node[role="button"]')).toBeNull();
    expect(compiled.querySelector('.station-node[tabindex]')).toBeNull();
  });

  it('should highlight and expand a line from the map or the side panel', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const firstAccordion = compiled.querySelector<HTMLButtonElement>('.line-accordion-header');

    firstAccordion?.click();
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.metro-line.highlighted-line')).toHaveLength(1);
    expect(compiled.querySelectorAll('.metro-line.dimmed-line')).toHaveLength(5);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('Los Molinos');

    const secondMapLine = compiled.querySelectorAll('.metro-line').item(1);
    secondMapLine.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(secondMapLine.classList.contains('highlighted-line')).toBe(true);
    expect(compiled.querySelectorAll('.line-accordion-body')).toHaveLength(1);
  });

  it('should highlight a line and all its stations on hover without adding a focus frame', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const mapLine = compiled.querySelector('.metro-line') as SVGElement;

    mapLine.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(mapLine.classList.contains('highlighted-line')).toBe(true);
    expect(compiled.querySelectorAll('.station-node.highlighted-station')).toHaveLength(2);
    expect(compiled.querySelector('.metro-line.dimmed-line')).toBeNull();
    expect(compiled.querySelector('.station-node.dimmed-station')).toBeNull();
    expect(mapLine.hasAttribute('tabindex')).toBe(false);
    expect(mapLine.hasAttribute('role')).toBe(false);

    mapLine.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();

    expect(compiled.querySelector('.highlighted-line')).toBeNull();
    expect(compiled.querySelector('.highlighted-station')).toBeNull();
  });

  it('should treat line labels like their tracks and stations', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const lineLabel = compiled.querySelector('.line-end-label') as SVGGElement;

    lineLabel.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(lineLabel.classList.contains('highlighted-line-label')).toBe(true);
    expect(compiled.querySelectorAll('.station-node.highlighted-station')).toHaveLength(2);
    expect(compiled.querySelector('.dimmed-line')).toBeNull();

    lineLabel.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.metro-line.dimmed-line')).toHaveLength(5);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('Los Molinos');
  });

  it('should select the station line without leaving the station pressed', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const station = compiled.querySelectorAll('.station-node').item(44);

    station.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(station.classList.contains('selected-station')).toBe(false);
    expect(compiled.querySelectorAll('.metro-line.highlighted-line')).toHaveLength(1);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('Los Molinos');
  });

  it('should show an error and retry action when the API fails', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => throwError(() => new Error('API error')) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
  });
});
