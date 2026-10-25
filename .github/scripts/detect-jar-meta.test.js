#!/usr/bin/env node
'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { detect, detectRequired, GAME_VERSIONS, LOADERS } = require('./detect-jar-meta.js');

let failed = 0;
function test(name, fn) {
  try {
    fn();
    console.log('ok', name);
  } catch (e) {
    failed += 1;
    console.error('FAIL', name, e.message);
  }
}

test('release jar names', () => {
  assert.strictEqual(detect('LPCF-2.0.0.jar').version, '2.0.0');
  assert.strictEqual(detect('releases/LPCF-2.0.0.jar').version, '2.0.0');
  assert.strictEqual(detect('build/libs/LPCF-2.1.0-beta.1.jar').version, '2.1.0-beta.1');
});

test('old and foreign names are refused', () => {
  for (const name of ['LuckPermsChatFormatterFolia-1.2.5.jar', 'LPCF-2.0.0-sources.jar', 'LPCF.jar', 'lpcf-2.0.0.jar']) {
    assert.strictEqual(detect(name).version, null, name);
    assert.throws(() => detectRequired(name), /Not an LPCF release jar/, name);
  }
});

test('version must match the requested release', () => {
  assert.throws(() => detectRequired('LPCF-2.0.0.jar', '2.0.1'), /expected 2.0.1/);
  assert.deepStrictEqual(detectRequired('LPCF-2.0.0.jar', '2.0.0').loaders, ['paper', 'folia', 'purpur']);
});

test('game versions cover 1.20.1 to 26.3 with no duplicates', () => {
  assert.strictEqual(GAME_VERSIONS[0], '1.20.1');
  assert.strictEqual(GAME_VERSIONS[GAME_VERSIONS.length - 1], '26.3');
  assert.strictEqual(new Set(GAME_VERSIONS).size, GAME_VERSIONS.length);
  for (const v of ['1.20.1', '1.21.1', '26.1.2', '26.2', '26.3']) assert.ok(GAME_VERSIONS.includes(v), v);
  assert.ok(!LOADERS.includes('spigot'));
});

test('VERSION file names a valid release', () => {
  const version = fs.readFileSync(path.join(__dirname, '..', '..', 'VERSION'), 'utf8').trim();
  assert.strictEqual(detectRequired(`LPCF-${version}.jar`, version).version, version);
});

if (failed) {
  console.error(`${failed} case(s) failed`);
  process.exit(1);
}
console.log('all detect-jar-meta cases passed');
