import net from 'node:net';

function packet(id, type, text) {
    const body = Buffer.from(text, 'utf8');
    const data = Buffer.alloc(body.length + 14);
    data.writeInt32LE(data.length - 4, 0);
    data.writeInt32LE(id, 4);
    data.writeInt32LE(type, 8);
    body.copy(data, 12);
    return data;
}

/** Executes one console command against a configured Minecraft RCON endpoint. */
export function rconCommand({ host = '127.0.0.1', port, password }, command, timeout = 10_000) {
    return new Promise((resolve, reject) => {
        const socket = net.connect({ host, port });
        let bytes = Buffer.alloc(0);
        let authenticated = false;
        let settled = false;
        const finish = (error, result) => {
            if (settled) return;
            settled = true;
            socket.destroy();
            if (error) reject(error);
            else resolve(result);
        };
        socket.setTimeout(timeout, () => finish(new Error('RCON timed out')));
        socket.on('error', error => finish(error));
        socket.on('end', () => finish(new Error('RCON closed before replying')));
        socket.on('connect', () => socket.write(packet(1, 3, password)));
        socket.on('data', chunk => {
            bytes = Buffer.concat([bytes, chunk]);
            while (bytes.length >= 4) {
                const length = bytes.readInt32LE(0);
                if (length < 10 || length > 4_194_304) {
                    finish(new Error('Invalid RCON packet length'));
                    return;
                }
                if (bytes.length < length + 4) return;
                const response = bytes.subarray(0, length + 4);
                bytes = bytes.subarray(length + 4);
                const id = response.readInt32LE(4);
                const type = response.readInt32LE(8);
                if (id === -1) {
                    finish(new Error('RCON authentication failed'));
                    return;
                }
                if (!authenticated && id === 1 && type === 2) {
                    authenticated = true;
                    socket.write(packet(2, 2, command));
                } else if (authenticated && id === 2) {
                    finish(null, response.subarray(12, response.length - 2).toString('utf8'));
                }
            }
        });
    });
}
