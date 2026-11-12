import net from 'node:net';
import readline from 'node:readline';

if (process.argv.includes('--fail')) process.exit(2);
const port = Number(process.argv[2]);
const server = net.createServer(socket => socket.end());
server.listen(port, '127.0.0.1', () => {
    if (!process.argv.includes('--never-ready')) {
        setTimeout(() => console.log('READY'), Number(process.env.READY_DELAY ?? 0));
    }
});
const input = readline.createInterface({ input: process.stdin });
input.on('line', command => {
    if (command === 'stop') {
        console.log('SAVED');
        server.close(() => { input.close(); process.exit(0); });
    }
});
