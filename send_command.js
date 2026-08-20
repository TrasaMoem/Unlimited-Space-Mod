const net = require('net');

const HOST = '127.0.0.1';
const PORT = 25575;
const PASSWORD = 'admin';
const COMMAND = process.argv[2] || '/unlimitedspace nav 910 2 1';

function buildPacket(requestId, type, payload) {
    const payloadBytes = Buffer.from(payload + '\x00', 'utf8');
    const typeBuf = Buffer.alloc(4);
    typeBuf.writeUInt32LE(type, 0);
    const idBuf = Buffer.alloc(4);
    idBuf.writeUInt32LE(requestId, 0);
    const packetBody = Buffer.concat([idBuf, typeBuf, payloadBytes]);
    const lengthBuf = Buffer.alloc(4);
    lengthBuf.writeUInt32LE(packetBody.length, 0);
    return Buffer.concat([lengthBuf, packetBody]);
}

function readPacket(socket) {
    return new Promise((resolve, reject) => {
        let lengthBuf = Buffer.alloc(0);
        let received = 0;

        function onData(data) {
            lengthBuf = Buffer.concat([lengthBuf, data]);
            if (lengthBuf.length < 4) return; // not enough for length

            if (received === 0) {
                received = data.length;
                if (received < 4) return; // still need more
            }

            const packetLength = lengthBuf.readUInt32LE(0);
            if (lengthBuf.length < 4 + packetLength) return; // need full packet

            socket.removeListener('data', onData);
            const packet = lengthBuf.subarray(0, 4 + packetLength);
            const requestId = packet.readUInt32LE(4);
            const type = packet.readUInt32LE(8);
            const payload = packet.subarray(12, 12 + packetLength - 8).toString('utf8').replace(/\x00$/, '');
            resolve({ requestId, type, payload });
        }

        socket.on('data', onData);
        socket.on('error', reject);
    });
}

const socket = new net.Socket();
socket.connect(PORT, HOST, () => {
    console.log('[RCON] Connected to ' + HOST + ':' + PORT);

    // Login
    const loginPacket = buildPacket(1, 3, PASSWORD);
    socket.write(loginPacket);

    readPacket(socket).then(loginResp => {
        if (loginResp.type === 0 || loginResp.requestId === 0xffffffff) {
            console.error('[RCON] Login failed');
            socket.destroy();
            process.exit(1);
        }
        console.log('[RCON] Login successful');

        // Send command
        const cmdPacket = buildPacket(2, 2, COMMAND);
        socket.write(cmdPacket);
        console.log('[RCON] Sending command: ' + COMMAND);

        return readPacket(socket);
    }).then(cmdResp => {
        console.log('[RCON] Response: ' + cmdResp.payload);
        socket.destroy();
        process.exit(0);
    }).catch(err => {
        console.error('[RCON] Error:', err.message);
        socket.destroy();
        process.exit(1);
    });
});

socket.on('error', (err) => {
    console.error('[RCON] Connection error:', err.message);
    process.exit(1);
});

// Timeout
setTimeout(() => {
    console.error('[RCON] Connection timed out');
    socket.destroy();
    process.exit(1);
}, 15000);