// Full migrated Instanced suite. Default: visible windows, grouped by role count and language.
// HMC_MODS must include Stagecraft and Instanced. --party-mods adds Parties + Cardinal Components.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { director } from './director.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const groups = [
    { name: 'single', roles: ['A'], lang: 'es_es', scenarios: ['smoke','config','samples','demo','showcase','fortress','teleports','return','waiting','instances','restart','dimensions','slots','hud','tracker','capture','place','editor','convert','postprocess','admin','settings-reset','marker-portals'] },
    { name: 'english', roles: ['A'], lang: 'en_us', scenarios: ['lang'] },
    { name: 'duo', roles: ['A','B'], lang: 'es_es', parties: true, scenarios: ['tpa','rejoin','numismatic-parties'] },
    { name: 'party', roles: ['A','B','C','D'], lang: 'es_es', parties: true, scenarios: ['parties'] },
];

const argv = process.argv.slice(2);
let windowed = true, group, only, partyMods, stopServer = false;
let server = { host: 'localhost', port: 25571 };
let serverCwd = path.resolve(root, '../Instanced');
while (argv.length) {
    const flag = argv.shift();
    if (flag === '--headless') windowed = false;
    else if (flag === '--stop-server') stopServer = true;
    else if (flag === '--group') group = argv.shift();
    else if (flag === '--only') only = argv.shift().split(',');
    else if (flag === '--party-mods') partyMods = path.resolve(argv.shift());
    else if (flag === '--server-cwd') serverCwd = path.resolve(argv.shift());
    else if (flag === '--server') { const [host,port] = argv.shift().split(':'); server = { host, port: Number(port ?? 25565) }; }
    else throw new Error(`Unknown option ${flag}`);
}
const selected = groups.filter(g => !group || g.name === group).map(g => ({...g, scenarios: g.scenarios.filter(s => !only || only.includes(s))})).filter(g => g.scenarios.length);
if (!selected.length) throw new Error('No scenarios selected');
if (only && only.some(name => !selected.some(g => g.scenarios.includes(name)))) throw new Error('Unknown scenario in --only');
if (selected.some(g => g.parties) && !partyMods) throw new Error('Pass --party-mods <folder> with Parties and Cardinal Components for the multiplayer groups');
const output = path.join(root,'build','instanced-suite-'+new Date().toISOString().replace(/[:.]/g,'-'));
fs.mkdirSync(output,{recursive:true});
const report = { started: new Date().toISOString(), windowed, server, groups: selected, results: [], passed: false };
const save = () => fs.writeFileSync(path.join(output,'summary.json'),JSON.stringify(report,null,2));
save();
try {
    for (const g of selected) {
        console.log(`[suite] ${g.name}: ${g.roles.length} ${windowed ? 'visible' : 'headless'} client(s), ${g.lang}`);
        const roles = Object.fromEntries(g.roles.map(role => [role,{client:'HmcInstanced'+role,window:windowed,mods:g.parties ? [partyMods] : []}]));
        const d = await director({roles,server,start:true,lang:g.lang,serverControl:{start:'gradlew.bat :fabric-server:runServer --console=plain',cwd:serverCwd,log:path.join(output,'server.log')}});
        let groupPassed = false;
        try {
            for (const name of g.scenarios) {
                const result = await d.run({file:path.join(root,'scenarios/instanced',name+'.lua')});
                fs.writeFileSync(path.join(output,name+'.json'),JSON.stringify(result,null,2));
                report.results.push({scenario:name,group:g.name,passed:result.passed}); save();
                if (!result.passed) throw new Error(`${name} failed; see ${output}`);
            }
            groupPassed = true;
        } finally {
            try {
                if (stopServer && (!groupPassed || g === selected.at(-1))) await d.operateServer('stop', 'default', {reconnect:false});
            } finally { await d.stop(); }
        }
    }
    report.passed = true;
} finally {
    report.finished = new Date().toISOString(); save();
    console.log(`[suite] ${report.results.filter(x=>x.passed).length}/${selected.reduce((n,g)=>n+g.scenarios.length,0)} passed; ${output}`);
}
