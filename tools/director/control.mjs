import net from 'node:net';
import fs from 'node:fs';
import crypto from 'node:crypto';

/** A manual session remains owned by its running director and can be closed from another terminal. */
export async function startControl(session) {
    const token = crypto.randomBytes(24).toString('hex');
    let finish;
    const closed = new Promise(resolve => { finish = resolve; });
    const sockets = new Set();
    const server = net.createServer(socket => {
        sockets.add(socket);
        socket.on('close', () => sockets.delete(socket));
        socket.on('error', () => {});
        let data = '';
        let handled = false;
        socket.on('data', async chunk => {
            if (handled) return;
            data += chunk.toString('utf8');
            if (data.length > 1_000_000) { socket.destroy(); return; }
            const newline = data.indexOf('\n');
            if (newline < 0) return;
            handled = true;
            let shuttingDown = false;
            try {
                const request = JSON.parse(data.slice(0, newline));
                if (request.token !== token) throw new Error('Invalid session token');
                let result;
                if (request.action === 'status') {
                    result = { state: session.report.state, ...session.snapshot() };
                } else if (request.action === 'down') {
                    await session.stop();
                    result = { state: session.report.state, ...session.snapshot() };
                    shuttingDown = true;
                } else if (request.action.startsWith('server.')) {
                    result = await session.operateServer(request.action.slice(7), request.server, request.options);
                } else if (request.action === 'client.connect') {
                    result = await session.connectRole(request.role, request.server, request.options);
                } else {
                    throw new Error('Unknown session action ' + request.action);
                }
                socket.end(JSON.stringify({ ok: true, result }) + '\n');
            } catch (error) {
                socket.end(JSON.stringify({ ok: false, error: error.message }) + '\n');
            }
            if (shuttingDown) {
                for (const other of sockets) if (other !== socket) other.destroy();
                server.close(finish);
            }
        });
    });
    await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', resolve);
    });
    session.report.write({ ...session.snapshot(), control: { port: server.address().port, token, pid: process.pid } });
    return {
        closed,
        async close() {
            for (const socket of sockets) socket.destroy();
            if (server.listening) await new Promise(resolve => server.close(resolve));
            finish();
        },
    };
}

export async function requestControl(file, action, fields = {}, timeout = 180_000) {
    const document = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (document.state === 'closed') return { state: 'closed', ...document };
    if (!document.control) throw new Error('The report does not contain an active manual session');
    return new Promise((resolve, reject) => {
        const socket = net.connect({ host: '127.0.0.1', port: document.control.port });
        let data = '';
        let replied = false;
        socket.setTimeout(timeout, () => socket.destroy(new Error('Session control timed out')));
        socket.on('error', reject);
        socket.on('end', () => { if (!replied) reject(new Error('Session closed without a reply')); });
        socket.on('connect', () => socket.write(JSON.stringify({ token: document.control.token, action, ...fields }) + '\n'));
        socket.on('data', chunk => {
            data += chunk.toString('utf8');
            if (!data.includes('\n')) return;
            replied = true;
            socket.end();
            try {
                const response = JSON.parse(data.slice(0, data.indexOf('\n')));
                if (!response.ok) reject(new Error(response.error));
                else resolve(response.result);
            } catch (error) { reject(error); }
        });
    });
}
