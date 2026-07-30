import { Component, inject, OnInit } from '@angular/core';

import {
  TransportTitle,
  TransportTitlesResponse,
  TransportTitleType
} from '../../core/models/transport-title.model';
import { TransportTitlesService } from '../../core/services/transport-titles.service';

type TypeFilter = TransportTitleType | 'ALL';
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

@Component({
  selector: 'app-transport-titles',
  templateUrl: './transport-titles.html',
  styleUrl: './transport-titles.css'
})
export class TransportTitles implements OnInit {
  private readonly transportTitlesService = inject(TransportTitlesService);

  catalog: TransportTitlesResponse | null = null;
  loading = true;
  errorMessage = '';
  searchText = '';
  selectedType: TypeFilter = 'ALL';
  selectedStatus: StatusFilter = 'ALL';

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

  trackTitle(_: number, title: TransportTitle): number {
    return title.id;
  }
}
