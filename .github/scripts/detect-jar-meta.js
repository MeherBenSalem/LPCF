#!/usr/bin/env node
'use strict';

/**
 * Release metadata for the LPCF plugin jar.
 *
 * LPCF ships one jar for every supported server: releases/LPCF-{version}.jar. Unlike a mod there is no Minecraft
 * version in the file name, so the game versions and loaders the jar is published for live here, in one place.
 * They must match the README compatibility table and docs/QA-{version}.md.
 */

const path = require('path');

// Platforms (Modrinth loaders). Spigot/Bukkit are not listed: LPCF needs the Paper API (Adventure chat events).
const LOADERS = ['paper', 'folia', 'purpur'];

// Every Minecraft release from 1.20.1 to 26.3. The plugin targets API 1.20 with Java 17 bytecode and uses newer
// APIs only when they exist.
const GAME_VERSIONS = [
  '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
  '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
  '26.1', '26.1.1', '26.1.2', '26.2', '26.3',
];

// CurseForge lists server software as "game versions" too.
const CURSEFORGE_PLATFORMS = ['Bukkit', 'Paper', 'Folia', 'Purpur'];

// Modrinth project ids: LuckPerms is required, PlaceholderAPI optional.
const MODRINTH_DEPENDENCIES = [
  { project_id: 'Vebnzrzj', dependency_type: 'required' },
  { project_id: 'lKEzGugV', dependency_type: 'optional' },
];
const CURSEFORGE_RELATIONS = [
  { slug: 'luckperms', type: 'requiredDependency' },
  { slug: 'placeholderapi', type: 'optionalDependency' },
];

const JAR = /^LPCF-(\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)(?:\.\d+)?)?)\.jar$/;

/** @param {string} jar path or basename @returns {{ version: string|null }} */
function detect(jar) {
  const match = path.basename(jar).match(JAR);
  return { version: match ? match[1] : null };
}

/** The release metadata for a jar; throws when the name is not LPCF-{version}.jar or the version differs. */
function detectRequired(jar, expectedVersion) {
  const { version } = detect(jar);
  if (!version) {
    throw new Error(`Not an LPCF release jar: ${path.basename(jar)}. Expected LPCF-{version}.jar`);
  }
  if (expectedVersion && version !== expectedVersion) {
    throw new Error(`${path.basename(jar)} is version ${version}, expected ${expectedVersion}`);
  }
  return { version, loaders: [...LOADERS], gameVersions: [...GAME_VERSIONS] };
}

module.exports = {
  detect, detectRequired, LOADERS, GAME_VERSIONS, CURSEFORGE_PLATFORMS, MODRINTH_DEPENDENCIES, CURSEFORGE_RELATIONS,
};

if (require.main === module) {
  const jar = process.argv[2];
  if (!jar) {
    console.error('Usage: detect-jar-meta.js <jar-path-or-name> [expected-version]');
    process.exit(2);
  }
  console.log(JSON.stringify(detectRequired(jar, process.argv[3])));
}
