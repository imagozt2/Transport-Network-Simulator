import { Component, HostListener, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import {
  CompensatoryDeliveryMethod,
  CompensatoryTicketIssuanceRequest,
  CompensatoryTicketIssuanceResponse,
  TransportTitle,
  TransportTitlesResponse,
  TransportTitleType
} from '../../core/models/transport-title.model';
import { TransportTitlesService } from '../../core/services/transport-titles.service';
import { DeviceOperation } from '../../core/models/device-operation.model';
import { NetworkMapStation } from '../../core/models/network-map.model';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import { NetworkMapService } from '../../core/services/network-map.service';
import { PassengerAccount } from '../../core/models/passenger-account.model';
import { PassengerAccountsService } from '../../core/services/passenger-accounts.service';
import { TemporalFormatService } from '../../core/services/temporal-format.service';
import { APPLICATION_ROUTES } from '../../core/navigation/application-routes';

type TypeFilter = TransportTitleType | 'ALL';
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';
type IssuanceProgress = 'FORM' | 'SUBMITTING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

@Component({
  selector: 'app-transport-titles',
  imports: [FormsModule],
  templateUrl: './transport-titles.html',
  styleUrls: ['./transport-titles.css', './transport-title-issuance-dialog.css']
})
export class TransportTitles implements OnInit {
  private readonly transportTitlesService = inject(TransportTitlesService);
  private readonly deviceOperationsService = inject(DeviceOperationsService);
  private readonly networkMapService = inject(NetworkMapService);
  private readonly passengerAccountsService = inject(PassengerAccountsService);
  private readonly temporalFormat = inject(TemporalFormatService);
  private readonly router = inject(Router);

  catalog: TransportTitlesResponse | null = null;
  loading = true;
  errorMessage = '';
  searchText = '';
  selectedType: TypeFilter = 'ALL';
  selectedStatus: StatusFilter = 'ALL';
  issuanceTitle: TransportTitle | null = null;
  ticketMachines: DeviceOperation[] = [];
  passengers: PassengerAccount[] = [];
  stations: NetworkMapStation[] = [];
  loadingIssuanceOptions = false;
  issuingTicket = false;
  issuanceError = '';
  issuanceOptionsWarning = '';
  issuanceConfirmation = '';
  issuanceProgress: IssuanceProgress = 'FORM';
  issuanceResult: CompensatoryTicketIssuanceResponse | null = null;
  selectedDeviceCode = '';
  selectedPassengerPublicId = '';
  selectedDeliveryMethod: CompensatoryDeliveryMethod = 'PHYSICAL_DEVICE';
  issuanceReason = '';
  originStationCode = '';
  destinationStationCode = '';
  selectedTrips: number | null = null;
  selectedDays: number | null = null;
  balanceAmount: number | null = null;

  readonly types: readonly TransportTitleType[] = [
    'SINGLE_TRIP',
    'MULTI_TRIP',
    'TIME_PASS',
    'SMART_BALANCE'
  ];

  ngOnInit(): void {
    this.loadTitles();
  }

  loadTitles(): void {
    this.loading = true;
    this.errorMessage = '';

    this.transportTitlesService.getTitles().subscribe({
      next: (catalog) => {
        this.catalog = catalog;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el catálogo de títulos de transporte.';
        this.loading = false;
      }
    });
  }

  setSearchText(value: string): void {
    this.searchText = value;
  }

  setTypeFilter(value: string): void {
    this.selectedType = value as TypeFilter;
  }

  setStatusFilter(value: string): void {
    this.selectedStatus = value as StatusFilter;
  }

  clearFilters(): void {
    this.searchText = '';
    this.selectedType = 'ALL';
    this.selectedStatus = 'ALL';
  }

  hasActiveFilters(): boolean {
    return this.searchText.trim().length > 0
      || this.selectedType !== 'ALL'
      || this.selectedStatus !== 'ALL';
  }

  filteredTitles(): TransportTitle[] {
    const search = this.searchText.trim().toLocaleLowerCase('es');

    return (this.catalog?.titles ?? []).filter((title) => {
      const matchesSearch = !search || [title.code, title.name, title.description ?? '']
        .some((value) => value.toLocaleLowerCase('es').includes(search));
      const matchesStatus = this.selectedStatus === 'ALL'
        || (this.selectedStatus === 'ACTIVE' && title.active)
        || (this.selectedStatus === 'INACTIVE' && !title.active);

      return matchesSearch
        && (this.selectedType === 'ALL' || title.type === this.selectedType)
        && matchesStatus;
    });
  }

  rechargeableTitleCount(): number {
    return this.catalog?.titles.filter((title) => title.rechargeable).length ?? 0;
  }

  typeLabel(type: TransportTitleType): string {
    const labels: Record<TransportTitleType, string> = {
      SINGLE_TRIP: 'Billete sencillo',
      MULTI_TRIP: 'Billete multiviaje',
      TIME_PASS: 'Abono temporal',
      SMART_BALANCE: 'Saldo inteligente'
    };
    return labels[type];
  }

  typeCode(type: TransportTitleType): string {
    const codes: Record<TransportTitleType, string> = {
      SINGLE_TRIP: 'BS',
      MULTI_TRIP: 'MV',
      TIME_PASS: 'AT',
      SMART_BALANCE: 'SI'
    };
    return codes[type];
  }

  priceLabel(title: TransportTitle): string {
    switch (title.type) {
      case 'SINGLE_TRIP':
        return `${this.money(title.basePrice)} + ${this.money(title.pricePerStation)} por estación`;
      case 'MULTI_TRIP':
        return `${this.money(title.pricePerTrip)} por viaje`;
      case 'TIME_PASS':
        return `${this.money(title.pricePerDay)} por día`;
      case 'SMART_BALANCE':
        return `${this.money(title.basePrice)} + ${this.money(title.pricePerStation)} por estación`;
    }
  }

  rangeLabel(title: TransportTitle): string {
    switch (title.type) {
      case 'SINGLE_TRIP':
        return 'Origen y destino obligatorios';
      case 'MULTI_TRIP':
        return `De ${title.minTrips} a ${title.maxTrips} viajes`;
      case 'TIME_PASS':
        return `De ${title.minDays} a ${title.maxDays} días`;
      case 'SMART_BALANCE':
        return `Recargas de ${this.money(title.minRechargeAmount)} a ${this.money(title.maxRechargeAmount)}`;
    }
  }

  money(value: number | null): string {
    if (value === null) {
      return '—';
    }
    return new Intl.NumberFormat('es-ES', {
      style: 'currency',
      currency: this.catalog?.currency ?? 'EUR'
    }).format(value);
  }

  openIssuanceDialog(title: TransportTitle): void {
    if (!title.active) {
      return;
    }
    this.issuanceTitle = title;
    this.resetIssuanceForm(title);
    if (this.ticketMachines.length === 0 || this.stations.length === 0) {
      this.loadIssuanceOptions();
    }
  }

  closeIssuanceDialog(): void {
    if (!this.issuingTicket) {
      this.issuanceTitle = null;
      this.issuanceError = '';
    }
  }

  @HostListener('document:keydown.escape')
  closeIssuanceDialogWithEscape(): void {
    this.closeIssuanceDialog();
  }

  submitCompensatoryIssuance(): void {
    const title = this.issuanceTitle;
    if (!title || !this.canSubmitIssuance()) {
      return;
    }
    this.issuingTicket = true;
    this.issuanceProgress = 'SUBMITTING';
    this.issuanceResult = null;
    this.issuanceError = '';
    this.transportTitlesService.issueCompensatoryTicket(title.id, this.issuanceRequest(title)).subscribe({
      next: (issuance) => {
        this.issuingTicket = false;
        this.issuanceResult = issuance;
        this.issuanceProgress = issuance.status === 'PROCESSING' ? 'PROCESSING' : 'COMPLETED';
        if (issuance.deliveryMethod === 'DIGITAL_WALLET') {
          this.issuanceConfirmation = `Billete ${issuance.ticketCode} entregado a ${issuance.passengerEmail}.`;
        } else if (issuance.simulated) {
          this.issuanceConfirmation = `Emisión simulada correctamente en ${issuance.deviceName}.`;
        } else {
          this.issuanceConfirmation = `Billete ${issuance.ticketCode} enviado a ${issuance.deviceName}.`;
        }
      },
      error: () => {
        this.issuingTicket = false;
        this.issuanceProgress = 'FAILED';
        this.issuanceError = 'No se ha podido completar la emisión compensatoria.';
      }
    });
  }

  canSubmitIssuance(): boolean {
    const title = this.issuanceTitle;
    const hasDestination = this.selectedDeliveryMethod === 'PHYSICAL_DEVICE'
      ? this.selectedMachine() !== null : this.selectedPassenger() !== null;
    if (!title || this.loadingIssuanceOptions || this.issuingTicket
      || !hasDestination || !this.issuanceReason.trim()) {
      return false;
    }
    switch (title.type) {
      case 'SINGLE_TRIP':
        return !!this.originStationCode && !!this.destinationStationCode
          && this.originStationCode !== this.destinationStationCode;
      case 'MULTI_TRIP':
        return this.inRange(this.selectedTrips, title.minTrips, title.maxTrips);
      case 'TIME_PASS':
        return this.inRange(this.selectedDays, title.minDays, title.maxDays);
      case 'SMART_BALANCE':
        return this.inRange(this.balanceAmount, title.minRechargeAmount, title.maxRechargeAmount);
    }
  }

  setOptionalNumber(field: 'trips' | 'days' | 'balance', value: string): void {
    const parsed = value === '' ? null : Number(value);
    const normalized = parsed !== null && Number.isFinite(parsed) ? parsed : null;
    if (field === 'trips') this.selectedTrips = normalized;
    if (field === 'days') this.selectedDays = normalized;
    if (field === 'balance') this.balanceAmount = normalized;
  }

  setDeliveryMethod(method: CompensatoryDeliveryMethod): void {
    this.selectedDeliveryMethod = method;
    this.selectedDeviceCode = '';
    this.selectedPassengerPublicId = '';
    this.issuanceError = '';
  }

  selectDevice(code: string): void {
    this.selectedDeviceCode = this.ticketMachines.some((machine) => machine.code === code)
      ? code : '';
    this.selectedPassengerPublicId = '';
  }

  selectPassenger(publicId: string): void {
    this.selectedPassengerPublicId = this.passengers.some((passenger) =>
      passenger.publicId === publicId && passenger.status === 'ACTIVE') ? publicId : '';
    this.selectedDeviceCode = '';
  }

  selectedMachine(): DeviceOperation | null {
    return this.ticketMachines.find((machine) => machine.code === this.selectedDeviceCode) ?? null;
  }

  selectedPassenger(): PassengerAccount | null {
    return this.passengers.find((passenger) =>
      passenger.publicId === this.selectedPassengerPublicId && passenger.status === 'ACTIVE') ?? null;
  }

  consultIssuanceLogs(): void {
    const deviceCode = this.issuanceResult?.deviceCode;
    this.closeIssuanceDialog();
    void this.router.navigate([APPLICATION_ROUTES.logs], {
      queryParams: {
        origin: 'ADMINISTRATION',
        ...(deviceCode ? { deviceCode } : {})
      }
    });
  }

  returnToIssuanceForm(): void {
    if (!this.issuingTicket) {
      this.issuanceProgress = 'FORM';
      this.issuanceError = '';
      this.issuanceResult = null;
    }
  }

  issuanceResultMessage(): string {
    const issuance = this.issuanceResult;
    if (!issuance) return '';
    if (issuance.status === 'PROCESSING') {
      return 'La orden se ha enviado a la máquina y está pendiente de confirmación.';
    }
    if (issuance.deliveryMethod === 'DIGITAL_WALLET') {
      return 'El billete digital ya está disponible en la cartera del pasajero.';
    }
    if (issuance.simulated) {
      return 'La emisión se ha simulado y ha quedado registrada sin generar un billete.';
    }
    return 'La emisión ha finalizado correctamente.';
  }

  issuanceQrSource(result: CompensatoryTicketIssuanceResponse): string | null {
    return result.qrPngBase64 ? `data:image/png;base64,${result.qrPngBase64}` : null;
  }

  issuanceProductType(type: TransportTitleType): string {
    return this.typeLabel(type);
  }

  issuanceDate(value: string): string {
    return this.temporalFormat.formatDateTime(value);
  }

  loadIssuanceOptions(): void {
    this.loadingIssuanceOptions = true;
    this.issuanceError = '';
    this.issuanceOptionsWarning = '';
    forkJoin({
      devices: this.deviceOperationsService.getOperations().pipe(catchError(() => of(null))),
      network: this.networkMapService.getNetworkMap().pipe(catchError(() => of(null))),
      passengers: this.passengerAccountsService.getAccounts(0, 100, {
        status: 'ACTIVE', sortBy: 'name', direction: 'ASC'
      }).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ devices, network, passengers }) => {
        this.ticketMachines = (devices?.devices ?? [])
          .filter((device) => device.type === 'TICKET_MACHINE' && device.status === 'ONLINE'
            && device.connectivity.state !== 'DISCONNECTED')
          .sort((first, second) =>
            first.station.name.localeCompare(second.station.name, 'es')
            || first.name.localeCompare(second.name, 'es')
            || first.code.localeCompare(second.code, 'es'));
        this.passengers = (passengers?.users ?? [])
          .filter((passenger) => passenger.status === 'ACTIVE')
          .sort((first, second) => `${first.firstName} ${first.lastName}`
            .localeCompare(`${second.firstName} ${second.lastName}`, 'es'));
        const byCode = new Map<string, NetworkMapStation>();
        (network?.lines ?? []).flatMap((line) => line.stations)
          .forEach((station) => byCode.set(station.code, station));
        this.stations = [...byCode.values()]
          .sort((first, second) => first.name.localeCompare(second.name, 'es'));
        const unavailable = [
          devices === null ? 'máquinas' : null,
          network === null ? 'estaciones' : null,
          passengers === null ? 'pasajeros' : null
        ].filter((value): value is string => value !== null);
        if (unavailable.length > 0) {
          this.issuanceOptionsWarning = `No se han podido cargar: ${unavailable.join(', ')}. Puedes usar las opciones disponibles o reintentar.`;
        }
        this.loadingIssuanceOptions = false;
      },
      error: () => {
        this.loadingIssuanceOptions = false;
        this.issuanceError = 'No se han podido cargar las máquinas y estaciones disponibles.';
      }
    });
  }

  private resetIssuanceForm(title: TransportTitle): void {
    this.issuanceError = '';
    this.issuanceOptionsWarning = '';
    this.issuanceProgress = 'FORM';
    this.issuanceResult = null;
    this.issuanceConfirmation = '';
    this.selectedDeviceCode = '';
    this.selectedPassengerPublicId = '';
    this.selectedDeliveryMethod = 'PHYSICAL_DEVICE';
    this.issuanceReason = '';
    this.originStationCode = '';
    this.destinationStationCode = '';
    this.selectedTrips = title.minTrips;
    this.selectedDays = title.minDays;
    this.balanceAmount = title.minRechargeAmount;
  }

  private issuanceRequest(title: TransportTitle): CompensatoryTicketIssuanceRequest {
    const request: CompensatoryTicketIssuanceRequest = {
      deliveryMethod: this.selectedDeliveryMethod,
      reason: this.issuanceReason.trim()
    };
    if (this.selectedDeliveryMethod === 'PHYSICAL_DEVICE') {
      request.deviceCode = this.selectedDeviceCode;
    } else {
      request.passengerPublicId = this.selectedPassengerPublicId;
    }
    if (title.type === 'SINGLE_TRIP') {
      request.originStationCode = this.originStationCode;
      request.destinationStationCode = this.destinationStationCode;
    } else if (title.type === 'MULTI_TRIP') {
      request.trips = this.selectedTrips!;
    } else if (title.type === 'TIME_PASS') {
      request.days = this.selectedDays!;
    } else {
      request.balanceAmount = this.balanceAmount!;
    }
    return request;
  }

  private inRange(value: number | null, minimum: number | null, maximum: number | null): boolean {
    return value !== null && (minimum === null || value >= minimum)
      && (maximum === null || value <= maximum);
  }

}
