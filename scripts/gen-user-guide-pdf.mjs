import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { marked } from 'marked';
import { chromium } from 'playwright';
import sharp from 'sharp';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const mdPath = path.join(root, 'docs/geohelp/guida-utente.md');
const htmlPath = path.join(root, 'docs/geohelp/guida-utente-print.html');
const pdfPath = path.join(root, 'docs/geohelp/guida-utente.pdf');
const pdfAliasPath = path.join(root, 'docs/geohelp/geoHELP_guida-utente.pdf');
const assetsDir = path.join(root, 'docs/geohelp/assets');
const headerLogoSizePx = 512;
const headerLogoAnsmiMm = '13mm';
const headerLogoUcrsMm = '14mm';
/** Fascia header (solo loghi) + distacco prima del testo su ogni pagina. */
const headerBandMm = 16;
const gapAfterHeaderMm = 6;
const pageTopMarginMm = `${headerBandMm + gapAfterHeaderMm}mm`;

function toDataUrl(filePath) {
  const ext = path.extname(filePath).slice(1).toLowerCase();
  const mime = ext === 'png' ? 'image/png' : 'image/jpeg';
  return `data:${mime};base64,${fs.readFileSync(filePath).toString('base64')}`;
}

/** Ritaglio circolare (toglie lo sfondo nero quadrato) + canvas quadrato uguale per entrambi i loghi. */
async function prepareHeaderLogo(inputPath, outputPath) {
  const size = headerLogoSizePx;
  const squared = await sharp(inputPath)
    .ensureAlpha()
    .resize(size, size, {
      fit: 'contain',
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    })
    .png()
    .toBuffer();

  const circleMask = await sharp(Buffer.from(
    `<svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">
      <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 2}" fill="white"/>
    </svg>`,
  ))
    .resize(size, size, { fit: 'fill' })
    .png()
    .toBuffer();

  await sharp(squared)
    .composite([{ input: circleMask, blend: 'dest-in' }])
    .png()
    .toFile(outputPath);
}

async function buildHeaderLogos() {
  const ansmiPrepared = path.join(assetsDir, 'logo_ansmi_nv_header.png');
  const ucrsPrepared = path.join(assetsDir, 'logo_ucrs_cinofili_header.png');

  await prepareHeaderLogo(path.join(assetsDir, 'logo_ansmi_nv.png'), ansmiPrepared);
  await prepareHeaderLogo(path.join(assetsDir, 'logo_ucrs_cinofili.png'), ucrsPrepared);

  return { ansmiPrepared, ucrsPrepared };
}

const { ansmiPrepared, ucrsPrepared } = await buildHeaderLogos();
const logoAnsmi = toDataUrl(ansmiPrepared);
const logoUcrs = toDataUrl(ucrsPrepared);

const headerTemplate = `
<div style="width:100%; height:${headerBandMm}mm; margin:0; padding:0 10mm 0 0; box-sizing:border-box; overflow:hidden;">
  <div style="display:flex; justify-content:flex-end; align-items:flex-start; gap:1.5mm; width:100%; height:100%;">
    <img src="${logoAnsmi}" alt="" style="width:${headerLogoAnsmiMm}; height:${headerLogoAnsmiMm}; object-fit:contain; display:block;" />
    <img src="${logoUcrs}" alt="" style="width:${headerLogoUcrsMm}; height:${headerLogoUcrsMm}; object-fit:contain; display:block;" />
  </div>
</div>`;

let md = fs.readFileSync(mdPath, 'utf8');
md = md.replace(/^---[\s\S]*?---\n/, '');

const body = marked.parse(md);
const html = `<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="utf-8"/>
  <title>geoHELP — Guida rapida</title>
  <style>
    body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 11pt; line-height: 1.5; color: #1a1a1a; margin: 0; }
    h1 { color: #b71c1c; font-size: 20pt; border-bottom: 3px solid #b71c1c; padding-bottom: 8px; margin-top: 0; }
    h2 { color: #0d47a1; font-size: 13pt; margin-top: 1.4em; page-break-after: avoid; }
    h3 { font-size: 11pt; color: #333; page-break-after: avoid; }
    p { margin: 0.5em 0; }
    table { border-collapse: collapse; width: 100%; margin: 10px 0 14px; font-size: 10pt; }
    th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; vertical-align: top; }
    th { background: #e8eef5; font-weight: 700; }
    tr:nth-child(even) td { background: #fafafa; }
    strong { color: #111; }
    hr { border: none; border-top: 1px solid #ddd; margin: 18px 0; }
    em { color: #555; font-size: 9.5pt; }
    ul { margin: 0.4em 0; padding-left: 1.2em; }
    li { margin: 0.25em 0; }
    @page { size: A4; }
    @media print { h2, h3 { page-break-after: avoid; } tr { page-break-inside: avoid; } }
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
  displayHeaderFooter: true,
  headerTemplate,
  footerTemplate: '<div></div>',
  margin: { top: pageTopMarginMm, bottom: '12mm', left: '11mm', right: '11mm' },
});
await browser.close();

fs.copyFileSync(pdfPath, pdfAliasPath);
console.log('PDF creato:', pdfPath);
console.log('Copia:', pdfAliasPath);
