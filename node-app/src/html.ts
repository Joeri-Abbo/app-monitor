/**
 * Tiny HTML helpers shared by the pages. The app renders its two pages inline
 * rather than through a template engine — there is not enough markup here to
 * justify one.
 */

/** Kept in sync with the other apps' pages so they all look the same. */
export const STYLE = `<style>
    body { font-family: system-ui, sans-serif; max-width: 60rem; margin: 4rem auto; line-height: 1.6; }
    code { background: #f4f4f5; padding: .1rem .3rem; border-radius: .25rem; }
    a { color: #4f46e5; }
    h2 { margin-top: 2.5rem; border-bottom: 2px solid #e4e4e7; padding-bottom: .25rem; }
    table { border-collapse: collapse; margin-top: .5rem; width: 100%; }
    td { border: 1px solid #e4e4e7; padding: .3rem .6rem; vertical-align: top; }
    td:first-child { color: #52525b; width: 18rem; }
    th { border: 1px solid #e4e4e7; padding: .3rem .6rem; background: #f4f4f5; text-align: left; }
</style>`;

export function escape(value: unknown): string {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/** A two-cell table row with both values escaped. */
export function row(label: string, value: unknown): string {
  return `<tr><td>${escape(label)}</td><td>${escape(value)}</td></tr>`;
}

export function table(...rows: string[]): string {
  return `<table>${rows.join('')}</table>`;
}

export function mib(bytes: number): string {
  return `${(bytes / 1048576).toFixed(1)} MiB`;
}
