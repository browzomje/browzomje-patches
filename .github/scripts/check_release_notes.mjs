import assert from 'node:assert/strict';
import { cosmiconfig } from 'cosmiconfig';
import { fileURLToPath } from 'node:url';
import { generateNotes } from '@semantic-release/release-notes-generator';
import { analyzeCommits } from '@semantic-release/commit-analyzer';

// Exercise the real preset, parser and writer together before publishing a release.
const cwd = fileURLToPath(new URL('../../', import.meta.url));
const { config } = await cosmiconfig('semantic-release').load(
  fileURLToPath(new URL('../../.releaserc', import.meta.url)),
);
const optionsFor = name => config.plugins.find(plugin => Array.isArray(plugin) && plugin[0] === name)[1];
const context = {
  cwd,
  logger: { log() {}, warn() {}, error() {} },
  options: { repositoryUrl: 'https://github.com/browzomje/browzomje-patches.git' },
  lastRelease: { version: '1.10.0', gitTag: 'v1.10.0' },
  nextRelease: { version: '1.11.0-dev.1', gitTag: 'v1.11.0-dev.1' },
  commits: [
    { hash: 'a'.repeat(40), message: 'feat(Pinterest): localize pin menu controls' },
    { hash: 'b'.repeat(40), message: 'fix(Pinterest): preserve native toast feedback' },
    { hash: 'c'.repeat(40), message: 'bump(Pinterest): support a new app version' },
    { hash: 'd'.repeat(40), message: 'docs: internal release checklist' },
  ],
};
const notes = await generateNotes(optionsFor('@semantic-release/release-notes-generator'), context);
for (const text of ['New Features', 'Bug Fixes', 'Updated App Support',
  'localize pin menu controls', 'preserve native toast feedback', 'support a new app version']) {
  assert.ok(notes.includes(text), `Release notes missing: ${text}`);
}
assert.ok(!notes.includes('internal release checklist'), 'Documentation commits should stay hidden');
assert.equal(await analyzeCommits(optionsFor('@semantic-release/commit-analyzer'), context), 'minor');
assert.equal(await analyzeCommits(optionsFor('@semantic-release/commit-analyzer'), {
  ...context, commits: [context.commits[1]],
}), 'patch');
console.log('Release notes render feat/fix/bump; minor and patch analysis pass.');
