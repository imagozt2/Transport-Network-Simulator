import { DOCUMENT } from '@angular/common';
import { afterNextRender, Directive, effect, ElementRef, inject, OnDestroy } from '@angular/core';

import { I18nService } from './i18n.service';
import { translateOperationalContent } from './translations/operational-content';

const LOCALIZED_ATTRIBUTES = ['aria-label', 'placeholder', 'title'] as const;

@Directive({
  selector: '[appLocalizeContent]',
  standalone: true
})
export class LocalizeContentDirective implements OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly document = inject(DOCUMENT);
  private readonly i18n = inject(I18nService);
  private readonly sourceTexts = new WeakMap<Text, string>();
  private readonly translatedTexts = new WeakMap<Text, string>();
  private readonly sourceAttributes = new WeakMap<Element, Map<string, string>>();
  private readonly translatedAttributes = new WeakMap<Element, Map<string, string>>();
  private observer?: MutationObserver;

  constructor() {
    effect(() => {
      const language = this.i18n.language();
      this.localizeTree(this.host, language);
    });

    afterNextRender(() => {
      this.observer = new MutationObserver(() => this.localizeTree(this.host, this.i18n.language()));
      this.observe();
      this.localizeTree(this.host, this.i18n.language());
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private localizeTree(root: HTMLElement, language: 'es' | 'en'): void {
    this.observer?.disconnect();

    const showText = this.document.defaultView?.NodeFilter.SHOW_TEXT ?? 4;
    const walker = this.document.createTreeWalker(root, showText);
    let node = walker.nextNode() as Text | null;
    while (node) {
      this.localizeTextNode(node, language);
      node = walker.nextNode() as Text | null;
    }

    this.localizeAttributes(root, language);
    this.observe();
  }

  private localizeTextNode(node: Text, language: 'es' | 'en'): void {
    const current = node.data;
    const previousTranslation = this.translatedTexts.get(node);
    if (!this.sourceTexts.has(node) || (current !== previousTranslation && current !== this.sourceTexts.get(node))) {
      this.sourceTexts.set(node, current);
    }

    const source = this.sourceTexts.get(node) ?? current;
    const leading = source.match(/^\s*/)?.[0] ?? '';
    const trailing = source.match(/\s*$/)?.[0] ?? '';
    const content = source.trim();
    const translated = content ? `${leading}${translateOperationalContent(content, language)}${trailing}` : source;
    this.translatedTexts.set(node, translated);
    if (current !== translated) node.data = translated;
  }

  private localizeAttributes(root: HTMLElement, language: 'es' | 'en'): void {
    const elements = [root, ...Array.from(root.querySelectorAll('*'))];
    for (const element of elements) {
      for (const attribute of LOCALIZED_ATTRIBUTES) {
        const current = element.getAttribute(attribute);
        if (current === null) continue;

        let sources = this.sourceAttributes.get(element);
        if (!sources) {
          sources = new Map<string, string>();
          this.sourceAttributes.set(element, sources);
        }
        const previousSource = sources.get(attribute);
        const previousTranslation = this.translatedAttributes.get(element)?.get(attribute);
        if (previousSource === undefined || (current !== previousSource && current !== previousTranslation)) {
          sources.set(attribute, current);
        }

        const translated = translateOperationalContent(sources.get(attribute) ?? current, language);
        let translations = this.translatedAttributes.get(element);
        if (!translations) {
          translations = new Map<string, string>();
          this.translatedAttributes.set(element, translations);
        }
        translations.set(attribute, translated);
        if (current !== translated) element.setAttribute(attribute, translated);
      }
    }
  }

  private observe(): void {
    this.observer?.observe(this.host, {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
      attributeFilter: [...LOCALIZED_ATTRIBUTES]
    });
  }
}
