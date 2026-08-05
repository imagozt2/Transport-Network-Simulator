import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { LocalizeContentDirective } from './core/i18n/localize-content.directive';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  hostDirectives: [LocalizeContentDirective],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {}
