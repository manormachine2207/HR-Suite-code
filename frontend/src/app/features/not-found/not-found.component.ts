import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [TranslateModule, RouterLink],
  templateUrl: './not-found.component.html'
})
export class NotFoundComponent {}
