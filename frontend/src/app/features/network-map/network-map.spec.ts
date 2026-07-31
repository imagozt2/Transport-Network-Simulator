import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NetworkJourneyResponse } from '../../core/models/network-journey.model';
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
        { id: 43, code: 'ST043', name: 'Cuatro Caminos', stationOrder: 2 },
      ],
    },
    {
      id: 2,
      code: 'L2',
      name: 'Línea 2',
      color: 'Verde',
      stations: [
        { id: 43, code: 'ST043', name: 'Cuatro Caminos', stationOrder: 1 },
        { id: 20, code: 'ST020', name: 'La Galería', stationOrder: 2 },
      ],
    },
  ],
};

const journeyResponse: NetworkJourneyResponse = {
  origin: { id: 45, code: 'ST045', name: 'Los Molinos' },
  destination: { id: 20, code: 'ST020', name: 'La Galería' },
  stationCount: 3,
  transferCount: 1,
  estimatedDurationSeconds: 430,
  stations: [
    { id: 45, code: 'ST045', name: 'Los Molinos' },
    { id: 43, code: 'ST043', name: 'Cuatro Caminos' },
    { id: 20, code: 'ST020', name: 'La Galería' },
  ],
  segments: [
    {
      lineId: 1,
      lineCode: 'L1',
      lineName: 'Línea 1',
      lineColor: 'Roja',
      origin: { id: 45, code: 'ST045', name: 'Los Molinos' },
      destination: { id: 43, code: 'ST043', name: 'Cuatro Caminos' },
      stopCount: 1,
      travelSeconds: 120,
      stations: [
        { id: 45, code: 'ST045', name: 'Los Molinos' },
        { id: 43, code: 'ST043', name: 'Cuatro Caminos' },
      ],
    },
    {
      lineId: 2,
      lineCode: 'L2',
      lineName: 'Línea 2',
      lineColor: 'Verde',
      origin: { id: 43, code: 'ST043', name: 'Cuatro Caminos' },
      destination: { id: 20, code: 'ST020', name: 'La Galería' },
      stopCount: 1,
      travelSeconds: 130,
      stations: [
        { id: 43, code: 'ST043', name: 'Cuatro Caminos' },
        { id: 20, code: 'ST020', name: 'La Galería' },
      ],
    },
  ],
};

describe('NetworkMap', () => {
  it('should combine API stations with the visual map by station code', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
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

  it('should organize the side panel into independent line and journey sections', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const sectionButtons = compiled.querySelectorAll<HTMLButtonElement>('.side-panel-header');

    expect(sectionButtons).toHaveLength(2);
    expect(sectionButtons.item(0).textContent).toContain('Líneas');
    expect(sectionButtons.item(1).textContent).toContain('Calculadora de trayectos');
    expect(compiled.querySelector('#network-lines-content')).not.toBeNull();
    expect(compiled.querySelector('#journey-planner-content')).toBeNull();

    sectionButtons.item(0).click();
    sectionButtons.item(1).click();
    fixture.detectChanges();

    expect(compiled.querySelector('#network-lines-content')).toBeNull();
    expect(compiled.querySelector('#journey-planner-content')?.textContent).toContain(
      'estación de origen',
    );
    expect(sectionButtons.item(0).getAttribute('aria-expanded')).toBe('false');
    expect(sectionButtons.item(1).getAttribute('aria-expanded')).toBe('true');
  });

  it('should calculate, display and clear a journey', async () => {
    const calculateJourney = vi.fn(() => of(journeyResponse));
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [
        {
          provide: NetworkMapService,
          useValue: { getNetworkMap: () => of(response), calculateJourney },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelectorAll<HTMLButtonElement>('.side-panel-header').item(1).click();
    fixture.detectChanges();
    const selects = compiled.querySelectorAll<HTMLSelectElement>('.journey-fields select');

    selects.item(0).value = 'ST045';
    selects.item(0).dispatchEvent(new Event('change'));
    selects.item(1).value = 'ST020';
    selects.item(1).dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(calculateJourney).toHaveBeenCalledWith('ST045', 'ST020');
    expect(compiled.querySelector('.journey-summary')?.textContent).toContain('~8 min');
    expect(compiled.querySelector('.journey-summary')?.textContent).toContain('1 transbordo');
    expect(compiled.querySelectorAll('.journey-segment')).toHaveLength(2);
    expect(compiled.querySelectorAll('.journey-station')).toHaveLength(4);
    expect(compiled.querySelectorAll('.first-journey-station')).toHaveLength(2);
    expect(compiled.querySelectorAll('.last-journey-station')).toHaveLength(2);
    expect(compiled.querySelectorAll('.transfer-station-row')).toHaveLength(2);
    expect(compiled.querySelectorAll('.journey-station strong').item(1).textContent).toBe(
      'Cuatro Caminos',
    );
    expect(compiled.querySelectorAll('.journey-station strong').item(2).textContent).toBe(
      'Cuatro Caminos',
    );
    expect(compiled.querySelector('.journey-thermometer')?.textContent).toContain('La Galería');
    expect(compiled.querySelectorAll('.journey-map-line')).toHaveLength(2);
    expect(compiled.querySelectorAll('.journey-map-line').item(0).getAttribute('points')).toBe(
      '350,50 350,150',
    );
    expect(compiled.querySelectorAll('.journey-map-line').item(1).getAttribute('points')).toBe(
      '350,150 550,550',
    );
    expect(compiled.querySelectorAll('.metro-line.dimmed-line')).toHaveLength(6);
    expect(compiled.querySelectorAll('.station-node.highlighted-station')).toHaveLength(3);
    expect(compiled.querySelectorAll('.station-node.dimmed-station').length).toBeGreaterThan(0);

    compiled.querySelector<HTMLButtonElement>('.clear-journey-button')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.journey).toBeNull();
    expect(fixture.componentInstance.originStationCode).toBe('');
    expect(compiled.querySelector('.journey-result')).toBeNull();
    expect(compiled.querySelector('.journey-map-line')).toBeNull();
    expect(compiled.querySelector('.metro-line.dimmed-line')).toBeNull();
  });

  it('should expose unique journey station options sorted by name', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();

    expect(fixture.componentInstance.journeyStationOptions.map((station) => station.code)).toEqual([
      'ST043',
      'ST020',
      'ST045',
    ]);
  });

  it('should wait for two different stations before requesting a journey', async () => {
    const calculateJourney = vi.fn(() => of(journeyResponse));
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [
        {
          provide: NetworkMapService,
          useValue: { getNetworkMap: () => of(response), calculateJourney },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.setOriginStation('ST045');
    expect(calculateJourney).not.toHaveBeenCalled();

    component.setDestinationStation('ST045');
    expect(calculateJourney).not.toHaveBeenCalled();
    expect(component.journeyErrorMessage).toContain('diferentes');

    component.setDestinationStation('ST020');
    expect(calculateJourney).toHaveBeenCalledOnce();
    expect(calculateJourney).toHaveBeenCalledWith('ST045', 'ST020');
    expect(component.journey).toEqual(journeyResponse);
  });

  it('should show a local error without altering the map when journey calculation fails', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [
        {
          provide: NetworkMapService,
          useValue: {
            getNetworkMap: () => of(response),
            calculateJourney: () => throwError(() => new Error('Journey API error')),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelectorAll<HTMLButtonElement>('.side-panel-header').item(1).click();
    fixture.detectChanges();

    const selects = compiled.querySelectorAll<HTMLSelectElement>('.journey-fields select');
    selects.item(0).value = 'ST045';
    selects.item(0).dispatchEvent(new Event('change'));
    selects.item(1).value = 'ST020';
    selects.item(1).dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(compiled.querySelector('.journey-error')?.textContent).toContain(
      'No se ha podido calcular',
    );
    expect(compiled.querySelector('.journey-map-line')).toBeNull();
    expect(compiled.querySelector('.metro-line.dimmed-line')).toBeNull();
  });

  it('should label a journey segment with the line terminal in its travel direction', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.lines = [
      {
        id: 6,
        code: 'L6',
        name: 'Línea 6',
        color: 'Naranja',
        stations: [
          { id: 1, code: 'ST001', name: 'El Espigón', stationOrder: 1 },
          { id: 40, code: 'ST040', name: 'Parque de la Cultura', stationOrder: 2 },
          { id: 49, code: 'ST049', name: 'Las Salinas', stationOrder: 3 },
        ],
      },
    ];
    const outwardSegment = {
      ...journeyResponse.segments[0],
      lineCode: 'L6',
      destination: { id: 40, code: 'ST040', name: 'Parque de la Cultura' },
      stations: [
        { id: 1, code: 'ST001', name: 'El Espigón' },
        { id: 40, code: 'ST040', name: 'Parque de la Cultura' },
      ],
    };

    expect(component.getJourneySegmentDirection(outwardSegment)).toBe('Las Salinas');
    expect(
      component.getJourneySegmentDirection({
        ...outwardSegment,
        origin: outwardSegment.destination,
        destination: outwardSegment.stations[0],
        stations: [...outwardSegment.stations].reverse(),
      }),
    ).toBe('El Espigón');
  });

  it('should highlight and expand a line from the map or the side panel', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
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
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
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
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
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

  it('should toggle the line from one of its stations without selecting the station itself', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const station = compiled.querySelector<SVGGElement>('[data-station-code="ST045"]');

    station?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(station?.classList.contains('selected-station')).toBe(false);
    expect(compiled.querySelectorAll('.metro-line.highlighted-line')).toHaveLength(1);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('Los Molinos');

    station?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(compiled.querySelector('.metro-line.highlighted-line')).toBeNull();
    expect(compiled.querySelector('.metro-line.dimmed-line')).toBeNull();
    expect(compiled.querySelector('.line-accordion-body')).toBeNull();
  });

  it('should replace the selected line when clicking a station from another line', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const firstLineStation = compiled.querySelector<SVGGElement>('[data-station-code="ST045"]');
    const secondLineStation = compiled.querySelector<SVGGElement>('[data-station-code="ST020"]');

    firstLineStation?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
    secondLineStation?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.metro-line.highlighted-line')).toHaveLength(1);
    expect(
      compiled.querySelectorAll('.metro-line').item(1).classList.contains('highlighted-line'),
    ).toBe(true);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('La Galería');
  });

  it('should select the hovered line when clicking a transfer station shared by several lines', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const secondMapLine = compiled.querySelectorAll<SVGPolylineElement>('.metro-line').item(1);
    const transferStation = compiled.querySelector<SVGGElement>('[data-station-code="ST043"]');

    secondMapLine.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    secondMapLine.dispatchEvent(new MouseEvent('mouseleave'));
    transferStation?.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(secondMapLine.classList.contains('highlighted-line')).toBe(true);

    transferStation?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(secondMapLine.classList.contains('highlighted-line')).toBe(true);
    expect(compiled.querySelectorAll('.metro-line.dimmed-line')).toHaveLength(5);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('La Galería');
  });

  it('should keep a station selection after its click bubbles through the map', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const station = compiled.querySelector<SVGGElement>('[data-station-code="ST045"]');

    station?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.metro-line.highlighted-line')).toHaveLength(1);
    expect(compiled.querySelectorAll('.metro-line.dimmed-line')).toHaveLength(5);
    expect(compiled.querySelector('.line-accordion-body')?.textContent).toContain('Los Molinos');
  });

  it('should clear the selected line when clicking the empty map background', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const mapLine = compiled.querySelectorAll<SVGPolylineElement>('.metro-line').item(0);
    const mapBackground = compiled.querySelector<SVGSVGElement>('.network-svg');

    mapLine.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
    expect(mapLine.classList.contains('highlighted-line')).toBe(true);

    mapBackground?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(compiled.querySelector('.metro-line.highlighted-line')).toBeNull();
    expect(compiled.querySelector('.metro-line.dimmed-line')).toBeNull();
    expect(compiled.querySelector('.station-node.dimmed-station')).toBeNull();
    expect(compiled.querySelector('.line-accordion-body')).toBeNull();
  });

  it('should not clear the selection when the map click comes from a line element', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [{ provide: NetworkMapService, useValue: { getNetworkMap: () => of(response) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const mapLine = compiled.querySelectorAll<SVGPolylineElement>('.metro-line').item(0);

    mapLine.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(mapLine.classList.contains('highlighted-line')).toBe(true);
    expect(compiled.querySelectorAll('.metro-line.dimmed-line')).toHaveLength(5);
  });

  it('should show an error and retry action when the API fails', async () => {
    await TestBed.configureTestingModule({
      imports: [NetworkMap],
      providers: [
        {
          provide: NetworkMapService,
          useValue: { getNetworkMap: () => throwError(() => new Error('API error')) },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(NetworkMap);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain(
      'No se ha podido cargar',
    );
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
  });
});
