async function generatePlusBoxPlaylist() {
    try {
        const baseUrl = 'https://plusbox.tv';
        const userAgent = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36';
        
        let htmlText = "";
        
        // Use native bridge if available (bypasses CORS in WebView)
        if (typeof streamBD !== 'undefined' && typeof streamBD.fetchUrl === 'function') {
            htmlText = streamBD.fetchUrl(baseUrl, JSON.stringify({ 'User-Agent': userAgent }));
        } else {
            // Fallback to standard fetch (for browser testing)
            const htmlRes = await fetch(baseUrl, { headers: { 'User-Agent': userAgent } });
            if (!htmlRes.ok) throw new Error(`HTTP Error: ${htmlRes.status}`);
            htmlText = await htmlRes.text();
        }

        if (!htmlText || htmlText.length < 100) {
            throw new Error("Failed to load HTML from " + baseUrl);
        }
        
        let m3u = "#EXTM3U\n";
        const blockRegex = /<a\s+class="playignitor thumbnail"([^>]*)>\s*<img([^>]*)>/gi;
        let match;
        
        const channelsData = [];
        while ((match = blockRegex.exec(htmlText)) !== null) {
            const aAttrs = match[1];
            const imgAttrs = match[2];
            const hrefMatch = aAttrs.match(/href="#([^"]+)"/);
            const dataNameMatch = aAttrs.match(/data-name="([^"]+)"/);
            const srcMatch = imgAttrs.match(/src="([^"]+)"/);
            
            if (hrefMatch && dataNameMatch && srcMatch) {
                channelsData.push({
                    title: hrefMatch[1].trim(),
                    dataName: dataNameMatch[1],
                    rawImg: srcMatch[1]
                });
            }
        }

        if (channelsData.length === 0) {
            throw new Error("No channels found by regex.");
        }

        for (const channel of channelsData) {
            const { title, dataName, rawImg } = channel;
            const imgUrl = rawImg.startsWith('http') ? rawImg : `${baseUrl}/${rawImg}`;
            
            try {
                let token = "";
                const headers = {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest',
                    'User-Agent': userAgent,
                    'Referer': `${baseUrl}/`,
                    'Origin': baseUrl
                };
                const body = 'ch_name=' + encodeURIComponent(dataName);
                
                if (typeof streamBD !== 'undefined' && typeof streamBD.postUrl === 'function') {
                    token = streamBD.postUrl(`${baseUrl}/token.php`, JSON.stringify(headers), body);
                } else {
                    const tokenRes = await fetch(`${baseUrl}/token.php`, {
                        method: 'POST',
                        headers: headers,
                        body: body
                    });
                    if (!tokenRes.ok) throw new Error(`HTTP_${tokenRes.status}`);
                    token = await tokenRes.text();
                }
                
                const finalToken = token.trim() || 'token_blank';
                
                const streamUrl = `https://backend.plusbox.tv/${dataName}/tracks-v1/index.fmp4.m3u8?token=${finalToken}`;
                const embedUrl = `https://backend.plusbox.tv/${dataName}/embed.html?mute=false&autoplay=true&volume=50&token=${finalToken}`;
                
                const finalUrl = `${streamUrl}|User-Agent=${userAgent}|Referer=${embedUrl}|Origin=${baseUrl}`;
                
                m3u += `#EXTINF:-1 tvg-logo="${imgUrl}" group-title="Live TV",${title}\n${finalUrl}\n`;
            } catch (err) {
                // Ignore single channel error
            }
        }
        
        if (typeof streamBD !== 'undefined') {
            streamBD.send(m3u);
        } else {
            console.log(m3u);
        }
        
    } catch (error) {
        const errorM3u = `#EXTM3U\n#EXTINF:-1 group-title="Error",${error.message}\nerror_url\n`;
        if (typeof streamBD !== 'undefined') {
            streamBD.send(errorM3u);
        } else {
            console.log(errorM3u);
        }
    }
}

generatePlusBoxPlaylist();
