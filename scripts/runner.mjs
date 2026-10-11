// Buduje kod pracy w tle (src/runner/background.js) w jeden plik bez importow: dist/runners/background.js.
// Background Runner na Androidzie wczytuje zwykly skrypt (bez modulow), wiec wszystko musi byc w srodku.
import { build } from 'esbuild';

await build({
  entryPoints: ['src/runner/background.js'],
  bundle: true,
  format: 'iife',
  target: 'es2020',
  outfile: 'dist/runners/background.js',
  legalComments: 'none',
  logLevel: 'info',
});
