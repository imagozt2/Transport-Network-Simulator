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
