import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { marked } from 'marked';
import { chromium } from 'playwright';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const mdPath = path.join(root, 'docs/SMS-FORMATO-COMPATTO.md');
const htmlPath = path.join(root, 'docs/SMS-FORMATO-COMPATTO-print.html');
const pdfPath = path.join(root, 'docs/SMS-FORMATO-COMPATTO.pdf');

let md = fs.readFileSync(mdPath, 'utf8');
md = md.replace(/^---[\s\S]*?---\n/, '');

const body = marked.parse(md);
const html = `<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="utf-8"/>
  <title>geoHELP — Formato SMS compatto</title>
  <style>
    body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #111; margin: 0; }
    h1 { color: #b71c1c; font-size: 18pt; border-bottom: 2px solid #b71c1c; padding-bottom: 6px; margin-top: 0; }
    h2 { color: #333; font-size: 13pt; margin-top: 1.3em; page-break-after: avoid; }
    h3 { font-size: 11pt; page-break-after: avoid; }
    table { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 9.5pt; }
    th, td { border: 1px solid #bbb; padding: 5px 7px; text-align: left; vertical-align: top; }
    th { background: #f0f0f0; font-weight: 600; }
    pre { background: #f5f5f5; padding: 8px; font-size: 9pt; white-space: pre-wrap; }
    hr { border: none; border-top: 1px solid #ccc; margin: 16px 0; }
    @page { size: A4; margin: 16mm 12mm; }
  </style>
</head>
<body>${body}</body>
</html>`;

fs.writeFileSync(htmlPath, html, 'utf8');

const browser = await chromium.launch();
const page = await browser.newPage();
const fileUrl = 'file:///' + htmlPath.replace(/\\/g, '/');
await page.goto(fileUrl, { waitUntil: 'load' });
await page.pdf({
  path: pdfPath,
  format: 'A4',
  printBackground: true,
  margin: { top: '14mm', bottom: '14mm', left: '12mm', right: '12mm' },
});
await browser.close();
console.log('PDF creato:', pdfPath);
