// JS PLAYLIST
(async function() {
    async function customFetch(url, options) {
        if (typeof streamBD !== 'undefined' && typeof streamBD.fetchUrl === 'function') {
            let headers = options && options.headers ? JSON.stringify(options.headers) : null;
            let res = streamBD.fetchUrl(url, headers);
            return {
                text: async () => res,
                json: async () => res ? JSON.parse(res) : {}
            };
        }
        return await fetch(url, options);
    }
    
    try {
        let m3u8 = "#EXTM3U\n";
        
        // --- 1. Fetch Server 2 Channels ---
        let res = await customFetch("https://05.teachub.workers.dev/");
        let html = await res.text();
        
        // --- 2. Create Session to get Cookie ---
        // Assuming Server 2 uses the same backend for session. If different, update the URL.
        let sessionRes = await customFetch("https://s.ipl2026.workers.dev/create-session", {
            headers: {
                "Origin": "https://jp.ipl2026.workers.dev",
                "Referer": "https://jp.ipl2026.workers.dev/"
            }
        });
        let sessionJson = await sessionRes.json();
        
        if (sessionJson.success && sessionJson.sessionId) {
            let cookie = "session=" + sessionJson.sessionId;
            let referer = "https://jp.ipl2026.workers.dev/";
            let origin = "https://jp.ipl2026.workers.dev";
            
            // --- 3. Parse Channels using Regex ---
            let regex = /<div class="card"[^>]*>[\s\S]*?<img src="([^"]+)"[\s\S]*?<div class="title">([^<]+)<\/div>[\s\S]*?<a class="play" href="[^"]*\?id=([^"]+)"/g;
            let match;
            
            while ((match = regex.exec(html)) !== null) {
                let logo = match[1].trim();
                let title = match[2].trim();
                let id = match[3].trim();
                
                // Assuming stream domain is same, update if different for server 2
                let streamUrl = `https://s.ipl2026.workers.dev/live.m3u8?id=${id}`;
                
                // --- 4. Append to M3U8 with Custom Headers ---
                m3u8 += `#EXTINF:-1 tvg-logo="${logo}" group-title="Jio Server 2",${title}\n`;
                
                let headers = [];
                headers.push(`Cookie=${cookie}`);
                headers.push(`Referer=${referer}`);
                headers.push(`Origin=${origin}`);
                
                let finalUrl = streamUrl;
                if (headers.length > 0) {
                    finalUrl += "|" + headers.join("|");
                }
                
                m3u8 += `${finalUrl}\n`;
            }
        }
        
        // --- 5. Return result to the app ---
        streamBD.send(m3u8);
    } catch(err) {
        streamBD.log("Server 2 Error: " + err.message);
        streamBD.send(""); 
    }
})();
