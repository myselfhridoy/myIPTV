<?php
/**
 * myIPTV - cURL to M3U Playlist Formatter
 * 
 * Supports:
 * - cURL (Bash, CMD, PowerShell, DevTools Copy as cURL)
 * - Kotlin OkHttp Request.Builder code
 * - Raw HTTP Request Headers
 */

function parseCurlOrHeaders($input) {
    $input = trim($input);
    if (empty($input)) return null;

    $url = "";
    $headers = [];

    // 1. Check if input is Kotlin OkHttp Builder code
    if (strpos($input, 'Request.Builder') !== false || strpos($input, '.addHeader') !== false || strpos($input, '.url(') !== false) {
        if (preg_match('/\.url\([\x22\x27]([^\x22\x27]+)[\x22\x27]\)/', $input, $m)) {
            $url = $m[1];
        }
        if (preg_match_all('/\.(?:addHeader|header)\([\x22\x27]([^\x22\x27]+)[\x22\x27],\s*[\x22\x27]([^\x22\x27]+)[\x22\x27]\)/', $input, $m, PREG_SET_ORDER)) {
            foreach ($m as $match) {
                $headers[$match[1]] = $match[2];
            }
        }
    } 
    // 2. Standard cURL or raw text
    else {
        // Remove line continuation characters (\, ^, `) at end of lines
        $clean = preg_replace('/[\\\\^`]\s*[\r\n]+/', ' ', $input);

        // Extract URL: matches after curl command or first http/https URL
        if (preg_match('/curl(?:\.exe)?\s+(?:-[^\s]+\s+)*(?:[\x22\x27]([^\x22\x27]+)[\x22\x27]|([^\s\x22\x27]+))/i', $clean, $m)) {
            $url = !empty($m[1]) ? $m[1] : (!empty($m[2]) ? $m[2] : "");
        }
        if (empty($url) || !preg_match('/^https?:\/\//i', $url)) {
            if (preg_match('/(https?:\/\/[^\s\x22\x27<>]+)/i', $clean, $m)) {
                $url = $m[1];
            }
        }

        // Extract headers: -H "Key: Value" or --header "Key: Value" or -H 'Key: Value'
        if (preg_match_all('/(?:-H|--header)\s+[\x22\x27]([^\x22\x27:]+)\s*:\s*([^\x22\x27]*)[\x22\x27]/i', $clean, $m, PREG_SET_ORDER)) {
            foreach ($m as $match) {
                $headers[trim($match[1])] = trim($match[2]);
            }
        }

        // Extract User-Agent: -A "..." or --user-agent "..."
        if (preg_match('/(?:-A|--user-agent)\s+[\x22\x27]([^\x22\x27]+)[\x22\x27]/i', $clean, $m)) {
            $headers["User-Agent"] = trim($m[1]);
        }

        // Extract Referer: -e "..." or --referer "..."
        if (preg_match('/(?:-e|--referer)\s+[\x22\x27]([^\x22\x27]+)[\x22\x27]/i', $clean, $m)) {
            $headers["Referer"] = trim($m[1]);
        }

        // Extract Cookie: -b "..." or --cookie "..."
        if (preg_match('/(?:-b|--cookie)\s+[\x22\x27]([^\x22\x27]+)[\x22\x27]/i', $clean, $m)) {
            $headers["Cookie"] = trim($m[1]);
        }

        // If no -H matched, check if raw HTTP headers were pasted line by line
        if (empty($headers)) {
            $lines = explode("\n", $input);
            foreach ($lines as $line) {
                $line = trim($line);
                if (strpos($line, ':') !== false && !preg_match('/^https?:/i', $line)) {
                    $parts = explode(':', $line, 2);
                    $k = trim($parts[0]);
                    $v = trim($parts[1]);
                    if (!empty($k) && !empty($v)) {
                        $headers[$k] = $v;
                    }
                }
            }
        }
    }

    // Filter out hop-by-hop or auto-managed transport headers that break ExoPlayer
    $ignoreHeaders = ['host', 'content-length', 'connection', 'accept-encoding'];
    $filteredHeaders = [];
    foreach ($headers as $k => $v) {
        if (!in_array(strtolower($k), $ignoreHeaders)) {
            $lower = strtolower($k);
            if ($lower === 'user-agent') $filteredHeaders['User-Agent'] = $v;
            elseif ($lower === 'referer') $filteredHeaders['Referer'] = $v;
            elseif ($lower === 'origin') $filteredHeaders['Origin'] = $v;
            elseif ($lower === 'cookie') $filteredHeaders['Cookie'] = $v;
            elseif ($lower === 'accept-language') $filteredHeaders['Accept-Language'] = $v;
            elseif ($lower === 'sec-ch-ua') $filteredHeaders['sec-ch-ua'] = $v;
            else $filteredHeaders[$k] = $v;
        }
    }

    // Ensure Accept-Language is present if sec-ch-ua is present
    if (isset($filteredHeaders['sec-ch-ua']) && !isset($filteredHeaders['Accept-Language'])) {
        $filteredHeaders['Accept-Language'] = 'en-US,en;q=0.9';
    }

    return [
        'url' => $url,
        'headers' => $filteredHeaders
    ];
}

$input = $_POST['input'] ?? '';
$channelName = trim($_POST['channelName'] ?? 'Sony HD');
$channelId = trim($_POST['channelId'] ?? '');
$groupTitle = trim($_POST['groupTitle'] ?? 'Live TV');
$tokenUrl = trim($_POST['tokenUrl'] ?? '');

$parsed = parseCurlOrHeaders($input);
$m3uLine = "";
$fullM3u = "";
$jsonOutput = "";

if ($parsed && !empty($parsed['url'])) {
    $url = $parsed['url'];
    $headerParts = [];
    foreach ($parsed['headers'] as $k => $v) {
        $headerParts[] = "$k=$v";
    }
    if (!empty($tokenUrl)) {
        $headerParts[] = "tokenUrl=$tokenUrl";
    }

    $pipeString = !empty($headerParts) ? "|" . implode("|", $headerParts) : "";
    $m3uLine = $url . $pipeString;

    if (empty($channelId)) {
        $channelId = strtolower(preg_replace('/[^a-zA-Z0-9]+/', '_', trim($channelName)));
    }

    $fullM3u = "#EXTINF:-1 tvg-id=\"{$channelId}\" tvg-name=\"{$channelName}\" group-title=\"{$groupTitle}\",{$channelName}\n" . $m3uLine;

    $jsonArray = [
        "name" => $channelName,
        "id" => $channelId,
        "group" => $groupTitle,
        "url" => $url,
        "headers" => $parsed['headers']
    ];
    if (!empty($tokenUrl)) $jsonArray['tokenUrl'] = $tokenUrl;
    $jsonOutput = json_encode($jsonArray, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>myIPTV - cURL to M3U Formatter</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0d1117;
            --surface-color: #161b22;
            --surface-border: #30363d;
            --accent-color: #00e676;
            --accent-hover: #00c853;
            --accent-glow: rgba(0, 230, 118, 0.2);
            --text-main: #f0f6fc;
            --text-muted: #8b949e;
            --code-bg: #090d13;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Outfit', sans-serif;
        }

        body {
            background-color: var(--bg-color);
            color: var(--text-main);
            min-height: 100vh;
            padding: 2.5rem 1rem;
            display: flex;
            justify-content: center;
        }

        .container {
            width: 100%;
            max-width: 960px;
        }

        .header {
            text-align: center;
            margin-bottom: 2rem;
        }

        .badge {
            display: inline-block;
            background: var(--surface-color);
            border: 1px solid var(--surface-border);
            color: var(--accent-color);
            padding: 4px 14px;
            border-radius: 999px;
            font-size: 0.85rem;
            font-weight: 600;
            margin-bottom: 0.75rem;
            letter-spacing: 0.5px;
        }

        h1 {
            font-size: 2.2rem;
            font-weight: 700;
            letter-spacing: -0.5px;
            margin-bottom: 0.5rem;
            background: linear-gradient(135deg, #ffffff 0%, #a5b4fc 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        p.subtitle {
            color: var(--text-muted);
            font-size: 1rem;
        }

        .card {
            background: var(--surface-color);
            border: 1px solid var(--surface-border);
            border-radius: 16px;
            padding: 2rem;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
            margin-bottom: 2rem;
        }

        .form-group {
            margin-bottom: 1.5rem;
        }

        label {
            display: block;
            font-size: 0.95rem;
            font-weight: 600;
            color: var(--text-main);
            margin-bottom: 0.5rem;
        }

        .row {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
            margin-bottom: 1.5rem;
        }

        input[type="text"], textarea {
            width: 100%;
            background: var(--code-bg);
            border: 1px solid var(--surface-border);
            border-radius: 10px;
            color: var(--text-main);
            padding: 12px 14px;
            font-size: 0.95rem;
            transition: all 0.2s ease;
        }

        input[type="text"]:focus, textarea:focus {
            outline: none;
            border-color: var(--accent-color);
            box-shadow: 0 0 0 3px var(--accent-glow);
        }

        textarea {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.9rem;
            min-height: 140px;
            resize: vertical;
            line-height: 1.5;
        }

        .btn-group {
            display: flex;
            gap: 1rem;
            align-items: center;
        }

        button.btn {
            background: var(--accent-color);
            color: #0d1117;
            border: none;
            border-radius: 10px;
            padding: 12px 24px;
            font-size: 1rem;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            gap: 8px;
        }

        button.btn:hover {
            background: var(--accent-hover);
            transform: translateY(-1px);
        }

        button.btn-secondary {
            background: transparent;
            color: var(--text-muted);
            border: 1px solid var(--surface-border);
        }

        button.btn-secondary:hover {
            background: #21262d;
            color: var(--text-main);
        }

        .output-box {
            position: relative;
            background: var(--code-bg);
            border: 1px solid var(--surface-border);
            border-radius: 12px;
            padding: 1rem;
            margin-top: 1rem;
        }

        .output-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.6rem;
            font-size: 0.85rem;
            color: var(--text-muted);
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        pre {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.88rem;
            color: #7ee787;
            white-space: pre-wrap;
            word-break: break-all;
            line-height: 1.5;
            max-height: 250px;
            overflow-y: auto;
        }

        .copy-btn {
            background: #21262d;
            border: 1px solid var(--surface-border);
            color: var(--text-main);
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.8rem;
            cursor: pointer;
            transition: all 0.2s;
        }

        .copy-btn:hover {
            background: #30363d;
            border-color: var(--accent-color);
            color: var(--accent-color);
        }

        .toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: var(--accent-color);
            color: #000;
            padding: 10px 20px;
            border-radius: 8px;
            font-weight: 600;
            opacity: 0;
            transform: translateY(20px);
            transition: all 0.3s ease;
            pointer-events: none;
            box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);
            z-index: 999;
        }

        .toast.show {
            opacity: 1;
            transform: translateY(0);
        }
    </style>
</head>
<body>

<div class="container">
    <div class="header">
        <span class="badge">myIPTV Tools</span>
        <h1>cURL / OkHttp to M3U Formatter</h1>
        <p class="subtitle">Paste any cURL command or Kotlin OkHttp code to instantly generate ExoPlayer M3U format</p>
    </div>

    <div class="card">
        <form method="POST" id="convertForm">
            <div class="form-group">
                <label for="input">Paste cURL Command / OkHttp Code / Raw Headers:</label>
                <textarea id="input" name="input" placeholder="curl 'https://example.com/live.m3u8' -H 'User-Agent: ...' -H 'Referer: ...'&#10;&#10;OR OkHttp:&#10;val request = Request.Builder().url('...').addHeader('...').build()"><?= htmlspecialchars($input) ?></textarea>
            </div>

            <div class="row">
                <div>
                    <label for="channelName">Channel Name</label>
                    <input type="text" id="channelName" name="channelName" value="<?= htmlspecialchars($channelName) ?>" placeholder="e.g. Sony HD">
                </div>
                <div>
                    <label for="groupTitle">Category / Group</label>
                    <input type="text" id="groupTitle" name="groupTitle" value="<?= htmlspecialchars($groupTitle) ?>" placeholder="e.g. Sports, Entertainment">
                </div>
                <div>
                    <label for="tokenUrl">Token Parser (Optional)</label>
                    <input type="text" id="tokenUrl" name="tokenUrl" value="<?= htmlspecialchars($tokenUrl) ?>" placeholder="e.g. dlhd, crichd, /2">
                </div>
            </div>

            <div class="btn-group">
                <button type="button" class="btn" onclick="processClientSide()">⚡ Format Now (Instant)</button>
                <button type="submit" class="btn btn-secondary">Submit (PHP)</button>
                <button type="button" class="btn btn-secondary" onclick="loadExample()">Load Sony HD Example</button>
            </div>
        </form>
    </div>

    <!-- Output Section -->
    <div class="card" id="outputSection" style="<?= empty($m3uLine) ? 'display: none;' : '' ?>">
        <h2 style="font-size: 1.3rem; margin-bottom: 1.2rem;">Generated Playlist Formats</h2>

        <!-- 1. Full M3U Block -->
        <div class="output-box">
            <div class="output-header">
                <span>Complete M3U Entry (#EXTINF + Stream)</span>
                <button class="copy-btn" onclick="copyText('fullM3uText')">Copy Entry</button>
            </div>
            <pre id="fullM3uText"><?= htmlspecialchars($fullM3u) ?></pre>
        </div>

        <!-- 2. Stream URL Only (Single line with headers) -->
        <div class="output-box">
            <div class="output-header">
                <span>Direct M3U Stream URL (With |Headers)</span>
                <button class="copy-btn" onclick="copyText('m3uLineText')">Copy Stream URL</button>
            </div>
            <pre id="m3uLineText"><?= htmlspecialchars($m3uLine) ?></pre>
        </div>

        <!-- 3. JSON Format -->
        <div class="output-box">
            <div class="output-header">
                <span>JSON API Format</span>
                <button class="copy-btn" onclick="copyText('jsonText')">Copy JSON</button>
            </div>
            <pre id="jsonText"><?= htmlspecialchars($jsonOutput) ?></pre>
        </div>
    </div>
</div>

<div class="toast" id="toast">Copied to clipboard!</div>

<script>
function copyText(elemId) {
    const text = document.getElementById(elemId).innerText;
    navigator.clipboard.writeText(text).then(() => {
        showToast("Copied to clipboard!");
    });
}

function showToast(msg) {
    const toast = document.getElementById('toast');
    toast.innerText = msg;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 2000);
}

function loadExample() {
    document.getElementById('input').value = `val client = OkHttpClient()
val request = Request.Builder()
.url("https://allinonereborn2.online/livtest3/stream_proxy.php?url=https%3A%2F%2Fallinonereborn2.online%2Fsony-new%2Fplaylists%2Fsony_hd.m3u8")
.get()
.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36")
.addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
.addHeader("sec-ch-ua", "\\"Chromium\\";v=\\"152\\", \\"Not?A_Brand\\";v=\\"24\\", \\"Google Chrome\\";v=\\"152\\"")
.addHeader("referer", "https://allinonereborn2.online/sony/ptest1.html?id=sony-hd")
.addHeader("accept-language", "en-BD,en;q=0.9,bn-BD;q=0.8,bn;q=0.7,en-GB;q=0.6,en-US;q=0.5")
.build()`;
    document.getElementById('channelName').value = "Sony HD";
    document.getElementById('groupTitle').value = "Entertainment";
    processClientSide();
}

function processClientSide() {
    const raw = document.getElementById('input').value.trim();
    if (!raw) return;

    let channelName = document.getElementById('channelName').value.trim() || "Live Channel";
    let groupTitle = document.getElementById('groupTitle').value.trim() || "Live TV";
    let tokenUrl = document.getElementById('tokenUrl').value.trim();
    let channelId = channelName.toLowerCase().replace(/[^a-z0-9]+/g, '_');

    let url = "";
    let headers = {};

    // 1. OkHttp Builder match
    if (raw.includes("Request.Builder") || raw.includes(".url(") || raw.includes(".addHeader(")) {
        const urlMatch = raw.match(/\.url\(["']([^"']+)["']\)/);
        if (urlMatch) url = urlMatch[1];

        const headerRegex = /\.(?:addHeader|header)\(["']([^"']+)["'],\s*["']([^"']+)["']\)/g;
        let m;
        while ((m = headerRegex.exec(raw)) !== null) {
            headers[m[1]] = m[2];
        }
    } 
    // 2. cURL command
    else {
        // Remove line continuations
        const clean = raw.replace(/[\\^`]\s*[\r\n]+/g, ' ');

        // Find URL
        const urlMatch = clean.match(/curl(?:\.exe)?\s+(?:-[^\s]+\s+)*(?:["']([^"']+)["']|([^\s"']+))/i);
        if (urlMatch) {
            url = urlMatch[1] || urlMatch[2] || "";
        }
        if (!url || !url.startsWith("http")) {
            const fallbackUrl = clean.match(/(https?:\/\/[^\s"'<>]+)/i);
            if (fallbackUrl) url = fallbackUrl[1];
        }

        // Find Headers (-H / --header)
        const headerRegex = /(?:-H|--header)\s+["']([^"':]+)\s*:\s*([^"']*)["']/gi;
        let hm;
        while ((hm = headerRegex.exec(clean)) !== null) {
            headers[hm[1].trim()] = hm[2].trim();
        }

        // -A / --user-agent
        const uaMatch = clean.match(/(?:-A|--user-agent)\s+["']([^"']+)["']/i);
        if (uaMatch) headers["User-Agent"] = uaMatch[1].trim();

        // -e / --referer
        const refMatch = clean.match(/(?:-e|--referer)\s+["']([^"']+)["']/i);
        if (refMatch) headers["Referer"] = refMatch[1].trim();

        // -b / --cookie
        const cookieMatch = clean.match(/(?:-b|--cookie)\s+["']([^"']+)["']/i);
        if (cookieMatch) headers["Cookie"] = cookieMatch[1].trim();
    }

    if (!url) {
        alert("Could not detect a valid HTTP/HTTPS URL from input!");
        return;
    }

    // Filter out hop-by-hop headers
    const ignoreList = ['host', 'content-length', 'connection', 'accept-encoding'];
    const filteredHeaders = {};
    for (const [k, v] of Object.entries(headers)) {
        if (!ignoreList.includes(k.toLowerCase())) {
            const lk = k.toLowerCase();
            if (lk === 'user-agent') filteredHeaders['User-Agent'] = v;
            else if (lk === 'referer') filteredHeaders['Referer'] = v;
            else if (lk === 'origin') filteredHeaders['Origin'] = v;
            else if (lk === 'cookie') filteredHeaders['Cookie'] = v;
            else if (lk === 'accept-language') filteredHeaders['Accept-Language'] = v;
            else if (lk === 'sec-ch-ua') filteredHeaders['sec-ch-ua'] = v;
            else filteredHeaders[k] = v;
        }
    }

    // Ensure Accept-Language is present if sec-ch-ua is present
    if (filteredHeaders['sec-ch-ua'] && !filteredHeaders['Accept-Language']) {
        filteredHeaders['Accept-Language'] = 'en-US,en;q=0.9';
    }

    // Build M3U Pipe string
    const parts = [];
    for (const [k, v] of Object.entries(filteredHeaders)) {
        parts.push(`${k}=${v}`);
    }
    if (tokenUrl) {
        parts.push(`tokenUrl=${tokenUrl}`);
    }

    const m3uLine = url + (parts.length > 0 ? '|' + parts.join('|') : '');
    const fullM3u = `#EXTINF:-1 tvg-id="${channelId}" tvg-name="${channelName}" group-title="${groupTitle}",${channelName}\n${m3uLine}`;
    const jsonOutput = JSON.stringify({
        name: channelName,
        id: channelId,
        group: groupTitle,
        url: url,
        headers: filteredHeaders,
        ...(tokenUrl ? { tokenUrl: tokenUrl } : {})
    }, null, 2);

    document.getElementById('fullM3uText').innerText = fullM3u;
    document.getElementById('m3uLineText').innerText = m3uLine;
    document.getElementById('jsonText').innerText = jsonOutput;
    document.getElementById('outputSection').style.display = 'block';
    document.getElementById('outputSection').scrollIntoView({ behavior: 'smooth' });
}

// Auto-process on page load if input exists
window.addEventListener('DOMContentLoaded', () => {
    if (document.getElementById('input').value.trim()) {
        processClientSide();
    }
});
</script>
</body>
</html>
