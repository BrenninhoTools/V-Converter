(function () {
    const VC = {};
    VC.ctx = null;

    VC.audio = function () {
        if (!VC.ctx) {
            const Context = window.AudioContext || window.webkitAudioContext;
            VC.ctx = Context ? new Context() : null;
        }
        if (VC.ctx && VC.ctx.state === 'suspended') VC.ctx.resume().catch(function () {});
        return VC.ctx;
    };

    const wake = function () {
        if (VC.ctx && VC.ctx.state === 'suspended') VC.ctx.resume().catch(function () {});
    };
    document.addEventListener('pointerdown', wake, true);
    document.addEventListener('keydown', wake, true);

    VC.putLatin1 = function (array, offset, text) {
        for (let i = 0; i < text.length; i++) array[offset + i] = text.charCodeAt(i);
    };

    VC.getLatin1 = function (array, start, end) {
        let out = '';
        for (let i = start; i < end; i += 8192) out += String.fromCharCode.apply(null, array.subarray(i, Math.min(end, i + 8192)));
        return out;
    };

    VC.xhr = function (method, url, headers, body) {
        const request = new XMLHttpRequest();
        try {
            request.open(method, url, false);
            request.overrideMimeType('text/plain; charset=x-user-defined');
            const lines = headers ? headers.split('\n') : [];
            for (const line of lines) {
                const at = line.indexOf(':');
                if (at > 0) {
                    try { request.setRequestHeader(line.slice(0, at), line.slice(at + 1)); } catch (e) {}
                }
            }
            request.send(body || null);
            return { status: request.status, text: request.responseText || '', headers: request.getAllResponseHeaders() || '', error: '' };
        } catch (e) {
            return { status: 0, text: '', headers: '', error: String((e && e.message) || e) };
        }
    };

    VC.storageGet = function (key) {
        try { return localStorage.getItem(key); } catch (e) { return null; }
    };

    VC.storageSet = function (key, value) {
        try { localStorage.setItem(key, value); } catch (e) {}
    };

    VC.open = function (url) {
        window.open(url, '_blank', 'noopener');
    };

    VC.download = function (name, bytes) {
        const url = URL.createObjectURL(new Blob([bytes]));
        const link = document.createElement('a');
        link.href = url;
        link.download = name;
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
    };

    VC.pick = function (accept, multiple, folder, done) {
        const input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.multiple = !!multiple || !!folder;
        if (folder) input.webkitdirectory = true;
        input.style.display = 'none';
        input.addEventListener('change', function () {
            const files = Array.from(input.files || []);
            input.remove();
            done(files.map(function (file) { return { path: file.webkitRelativePath || file.name, file: file }; }));
        });
        input.addEventListener('cancel', function () { input.remove(); });
        document.body.appendChild(input);
        input.click();
    };

    VC.read = function (file, done) {
        file.arrayBuffer().then(function (buffer) { done(new Uint8Array(buffer)); }, function () { done(null); });
    };

    VC.decode = function (bytes, done) {
        const context = VC.audio();
        if (!context) { done(null); return; }
        try {
            const copy = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
            context.decodeAudioData(copy, function (buffer) { done(buffer); }, function () { done(null); });
        } catch (e) {
            done(null);
        }
    };

    VC.player = function () {
        const context = VC.audio();
        if (!context) return null;
        const gain = context.createGain();
        gain.connect(context.destination);
        return { buffers: [], gain: gain, sources: [], offset: 0, started: 0, playing: false };
    };

    VC.addBuffer = function (player, buffer) {
        player.buffers.push(buffer);
    };

    VC.length = function (player) {
        return player.buffers.reduce(function (longest, buffer) { return Math.max(longest, buffer.duration); }, 0);
    };

    VC.stopSources = function (player) {
        for (const source of player.sources) {
            source.onended = null;
            try { source.stop(); } catch (e) {}
        }
        player.sources = [];
    };

    VC.position = function (player) {
        if (!player.playing) return player.offset;
        return Math.min(VC.length(player), player.offset + VC.ctx.currentTime - player.started);
    };

    VC.isPlaying = function (player) {
        return player.playing;
    };

    VC.play = function (player) {
        const context = VC.audio();
        if (!context || player.playing) return;
        const length = VC.length(player);
        if (player.offset >= length - 0.001) player.offset = 0;
        player.started = context.currentTime;
        player.playing = true;
        let longest = null;
        for (const buffer of player.buffers) {
            const source = context.createBufferSource();
            source.buffer = buffer;
            source.connect(player.gain);
            source.start(0, Math.min(player.offset, buffer.duration));
            player.sources.push(source);
            if (!longest || buffer.duration > longest.buffer.duration) longest = source;
        }
        if (longest) {
            longest.onended = function () {
                if (!player.playing) return;
                player.offset = VC.length(player);
                player.playing = false;
                player.sources = [];
            };
        }
    };

    VC.pause = function (player) {
        if (!player.playing) return;
        player.offset = VC.position(player);
        player.playing = false;
        VC.stopSources(player);
    };

    VC.seek = function (player, seconds) {
        const resume = player.playing;
        if (resume) {
            player.playing = false;
            VC.stopSources(player);
        }
        player.offset = Math.max(0, Math.min(seconds, VC.length(player)));
        if (resume) VC.play(player);
    };

    VC.volume = function (player, value) {
        player.gain.gain.value = value;
    };

    VC.release = function (player) {
        player.playing = false;
        VC.stopSources(player);
        try { player.gain.disconnect(); } catch (e) {}
    };

    VC.shot = function (buffer, volume) {
        const context = VC.audio();
        if (!context || !buffer) return;
        const source = context.createBufferSource();
        source.buffer = buffer;
        const gain = context.createGain();
        gain.gain.value = volume;
        source.connect(gain);
        gain.connect(context.destination);
        source.start();
    };

    VC.socket = function (url, onOpen, onText, onClose) {
        let socket;
        try {
            socket = new WebSocket(url);
        } catch (e) {
            setTimeout(function () { onClose(1006, String((e && e.message) || e)); }, 0);
            return null;
        }
        socket.onopen = function () { onOpen(); };
        socket.onmessage = function (event) { if (typeof event.data === 'string') onText(event.data); };
        socket.onclose = function (event) { onClose(event.code || 1006, event.reason || 'Connection lost'); };
        return socket;
    };

    VC.socketSend = function (socket, text) {
        try { socket.send(text); return true; } catch (e) { return false; }
    };

    VC.socketClose = function (socket) {
        try { socket.close(1000); } catch (e) {}
    };

    VC.ready = function () {
        const loading = document.getElementById('loading');
        if (loading) loading.remove();
    };

    window.VC = VC;
})();
