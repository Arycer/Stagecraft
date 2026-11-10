// Downloads the pinned HeadlessMC launcher and hmc-specifics (Fabric 1.21.1) into the HMC home
// (STAGECRAFT_HMC_HOME, default <Stagecraft>/run/hmc). Minecraft itself is downloaded on the first launch.
import fs from 'node:fs';
import path from 'node:path';
import { HOME } from './hmc.mjs';

const FILES = [
    ['headlessmc-launcher.jar', 'https://github.com/headlesshq/headlessmc/releases/download/2.10.0/headlessmc-launcher-2.10.0.jar'],
    ['mods/hmc-specifics-1.21.1-fabric.jar', 'https://github.com/headlesshq/hmc-specifics/releases/download/1.21.1-latest/hmc-specifics-1.21.1-fabric-latest.jar'],
];

for (const [file, url] of FILES) {
    const target = path.join(HOME, file);
    if (fs.existsSync(target)) { console.log(`already there: ${target}`); continue; }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    const response = await fetch(url);
    if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
    fs.writeFileSync(target, Buffer.from(await response.arrayBuffer()));
    console.log(`downloaded ${target}`);
}
