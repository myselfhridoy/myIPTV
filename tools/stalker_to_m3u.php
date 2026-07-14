<?php
/**
 * Stalker Portal to M3U Playlist Generator
 * Supports both Live Portal Fetch (via cURL) & JSON Channel List Conversion
 * 
 * Custom Stream Template Example:
 *   http://line.tvdsz.cc/play/live.php?mac={mac}&stream={stream_id}&extension=m3u8
 */

// Handle CLI vs Web Request
$isCli = (php_sapi_name() === 'cli');

$portalUrl = $_POST['portalUrl'] ?? '';
$macAddress = $_POST['macAddress'] ?? '';
$streamTemplate = $_POST['streamTemplate'] ?? '';
$inputMode = $_POST['inputMode'] ?? 'fetch'; // 'fetch' or 'json'
$rawJsonInput = $_POST['rawJsonInput'] ?? '';
$action = $_POST['action'] ?? '';
$timeout = (int)($_POST['timeout'] ?? 120);
if ($timeout < 10 || $timeout > 600) {
    $timeout = 120;
}

$outputM3u = '';
$errorMessage = '';
$successCount = 0;

function cleanPortalUrl($url) {
    $url = trim($url);
    if (empty($url)) return '';
    $url = rtrim($url, '/');
    // If user passed /c or /portal.php strip it to base or find portal.php location
    if (substr($url, -2) === '/c') {
        $url = substr($url, 0, -2);
    }
    return $url;
}

function fetchStalkerChannels($portalUrl, $mac, $timeout = 120) {
    if (function_exists('set_time_limit')) {
        @set_time_limit($timeout + 60);
    }

    $baseUrl = cleanPortalUrl($portalUrl);
    $apiUrl = rtrim($baseUrl, '/') . '/portal.php?type=itv&action=get_all_channels&JsHttpRequest=1-xml';
    
    $headers = [
        "Cookie: mac=" . urlencode($mac) . "; st=1; timezone=GMT",
        "User-Agent: Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stu/200.01 Safari/533.3",
        "X-User-Agent: Model: MAG250; Link:",
        "Accept: */*",
        "Referer: " . rtrim($baseUrl, '/') . "/c/"
    ];

    // First do handshake if required
    $handshakeUrl = rtrim($baseUrl, '/') . '/portal.php?type=stb&action=handshake&JsHttpRequest=1-xml';
    $connectTimeout = max(20, min(60, (int)($timeout / 2)));
    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL => $handshakeUrl,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_CONNECTTIMEOUT => $connectTimeout,
        CURLOPT_TIMEOUT => min(30, $timeout),
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_MAXREDIRS => 5,
        CURLOPT_ENCODING => '',
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_SSL_VERIFYHOST => 0,
        CURLOPT_IPRESOLVE => CURL_IPRESOLVE_V4,
        CURLOPT_HEADER => true
    ]);
    $handshakeRes = curl_exec($ch);
    $handshakeErr = curl_error($ch);
    $handshakeErrNo = curl_errno($ch);

    // If handshake completely failed due to a connection timeout or connection error, return immediately instead of waiting another round
    if ($handshakeRes === false && ($handshakeErrNo === CURLE_OPERATION_TIMEDOUT || stripos($handshakeErr, 'timed out') !== false || $handshakeErrNo === CURLE_COULDNT_CONNECT)) {
        $tip = "\n\n💡 Diagnosis: The TCP connection timed out right away ($handshakeErr). This almost always means:\n"
             . "1. Geo-Blocking / Firewall: The portal server is silently dropping connection attempts from the IP address hosting this script.\n"
             . "2. HTTP vs HTTPS: Check if the portal requires 'https://' instead of 'http://' (or vice-versa).\n"
             . "3. Offline Portal: The portal domain/port is offline.\n\n"
             . "⚡ Solution: Switch 'Input Mode' above to 'Paste Raw Channel JSON' to bypass server IP blocking.";
        curl_close($ch);
        return ['error' => "cURL Connection Error: " . $handshakeErr . $tip];
    }
    
    // Extract any new cookies from handshake
    $cookies = ["mac=" . urlencode($mac), "st=1", "timezone=GMT"];
    if ($handshakeRes && preg_match_all('/^Set-Cookie:\s*([^;]*)/mi', $handshakeRes, $matches)) {
        foreach ($matches[1] as $item) {
            parse_str($item, $cookie);
            $cookies[] = $item;
        }
    }
    $cookieHeader = "Cookie: " . implode("; ", array_unique($cookies));
    $headers[0] = $cookieHeader;

    // Reset curl handle options cleanly before the second request
    if (function_exists('curl_reset')) {
        curl_reset($ch);
    }

    // Now fetch get_all_channels with full timeout, gzip/deflate decoding, and redirect following
    curl_setopt_array($ch, [
        CURLOPT_URL => $apiUrl,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_CONNECTTIMEOUT => $connectTimeout,
        CURLOPT_TIMEOUT => $timeout,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_MAXREDIRS => 5,
        CURLOPT_ENCODING => '',
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_SSL_VERIFYHOST => 0,
        CURLOPT_IPRESOLVE => CURL_IPRESOLVE_V4,
        CURLOPT_HEADER => false
    ]);
    $res = curl_exec($ch);
    $err = curl_error($ch);
    $errNo = curl_errno($ch);
    curl_close($ch);

    if ($err) {
        $tip = "";
        if ($errNo === CURLE_OPERATION_TIMEDOUT || stripos($err, 'timed out') !== false || $errNo === CURLE_COULDNT_CONNECT) {
            $tip = "\n\n💡 Diagnosis: The connection or request timed out after " . $timeout . "s ($err). If your server IP is geo-blocked or throttled by the portal, switch 'Input Mode' above to 'Paste Raw Channel JSON'.";
        }
        return ['error' => "cURL Error: " . $err . $tip];
    }

    $decoded = json_decode($res, true);
    if (!$decoded) {
        return ['error' => "Failed to parse JSON response from portal. Server returned:\n" . substr($res, 0, 300)];
    }

    $channels = $decoded['js']['data'] ?? $decoded['js'] ?? $decoded['data'] ?? null;
    if (!is_array($channels)) {
        return ['error' => "No channel list found in portal response. Check if MAC address is authorized."];
    }

    return ['channels' => $channels];
}

function generateM3uFromChannels($channels, $mac, $template, $portalUrl = '') {
    $lines = ["#EXTM3U"];
    $count = 0;
    $baseUrl = rtrim(cleanPortalUrl($portalUrl), '/');

    foreach ($channels as $ch) {
        if (!is_array($ch)) continue;

        $name = trim($ch['name'] ?? $ch['title'] ?? 'Unknown Channel');
        $logo = trim($ch['logo'] ?? $ch['cmd_logo'] ?? '');
        $genre = trim($ch['tv_genre_id'] ?? $ch['category'] ?? 'Live TV');
        
        $cmd = trim($ch['cmd'] ?? $ch['url'] ?? '');
        $streamId = $ch['id'] ?? $ch['stream_id'] ?? null;

        // Determine Stream URL
        $streamUrl = '';
        if (!empty($template)) {
            // User provided an explicit custom template
            if (!$streamId && !empty($cmd)) {
                if (preg_match('/\/(\d+)(?:\s|$|\?|_)/', $cmd, $m)) {
                    $streamId = $m[1];
                } else {
                    $streamId = basename($cmd);
                }
            }
            if (empty($streamId)) continue;
            $encodedCmd = urlencode($cmd);
            $streamUrl = str_replace(
                ['{mac}', '{MAC}', '{stream_id}', '{id}', '{ID}', '{cmd}', '{cmd_encoded}', '{portal}'],
                [$mac, $mac, $streamId, $streamId, $streamId, $cmd, $encodedCmd, $baseUrl],
                $template
            );
        } else {
            // Auto-detect directly from JSON / Portal
            // Check if cmd contains a URL, but ignore internal server URLs (localhost/127.0.0.1)
            $rawUrl = '';
            if (preg_match('/https?:\/\/[^\s\'"]+/i', $cmd, $matches)) {
                $parsedHost = parse_url($matches[0], PHP_URL_HOST);
                if ($parsedHost !== 'localhost' && $parsedHost !== '127.0.0.1' && $parsedHost !== '0.0.0.0') {
                    $rawUrl = $matches[0];
                }
            }

            if (!empty($rawUrl)) {
                // Always convert live.php URLs to clean HLS format (&extension=m3u8) without temporary tokens
                if (strpos($rawUrl, 'live.php') !== false) {
                    $parsedHost = parse_url($rawUrl);
                    $hostBase = ($parsedHost['scheme'] ?? 'http') . '://' . ($parsedHost['host'] ?? '') . (!empty($parsedHost['port']) ? ':' . $parsedHost['port'] : '');
                    $sId = $streamId;
                    if (!$sId && preg_match('/[?&]stream=([^&]+)/i', $rawUrl, $sm)) {
                        $sId = $sm[1];
                    }
                    if ($sId) {
                        $streamUrl = $hostBase . '/play/live.php?mac=' . $mac . '&stream=' . $sId . '&extension=m3u8';
                    } else {
                        $streamUrl = preg_replace('/([?&]extension=)[^&]+/i', '${1}m3u8', preg_replace('/&play_token=[^&]+/i', '', $rawUrl));
                    }
                } else {
                    $streamUrl = $rawUrl;
                }
            } else {
                // Extract numeric stream ID if needed from cmd or id field
                if (!$streamId && !empty($cmd)) {
                    if (preg_match('/[^\d](\d+)(?:[^\d]|$|_)/', $cmd, $m)) {
                        $streamId = $m[1];
                    } elseif (is_numeric($cmd)) {
                        $streamId = $cmd;
                    }
                }
                if (empty($streamId)) continue;
                // Auto build standard live.php HLS stream URL from Portal & MAC
                $streamUrl = ($baseUrl ?: 'http://line.tvdsz.cc') . '/play/live.php?mac=' . $mac . '&stream=' . $streamId . '&extension=m3u8';
            }
        }

        $extInf = sprintf(
            '#EXTINF:-1 tvg-id="%s" tvg-logo="%s" group-title="%s",%s',
            htmlspecialchars((string)$streamId),
            htmlspecialchars($logo),
            htmlspecialchars($genre),
            $name
        );

        $lines[] = $extInf;
        $lines[] = $streamUrl;
        $count++;
    }

    return ['m3u' => implode("\n", $lines), 'count' => $count];
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && !empty($action)) {
    if ($inputMode === 'fetch' && (empty($portalUrl) || empty($macAddress))) {
        $errorMessage = "Please enter both the Stalker Portal URL and MAC Address.";
    } elseif ($inputMode === 'fetch') {
        $result = fetchStalkerChannels($portalUrl, $macAddress, $timeout);
        if (isset($result['error'])) {
            $errorMessage = $result['error'];
        } else {
            $gen = generateM3uFromChannels($result['channels'], $macAddress, $streamTemplate, $portalUrl);
            $outputM3u = $gen['m3u'];
            $successCount = $gen['count'];
        }
    } elseif ($inputMode === 'json') {
        if (empty($macAddress)) {
            $errorMessage = "Please enter your MAC Address above so we can construct the stream URLs correctly.";
        } else {
            $decoded = json_decode($rawJsonInput, true);
            $channels = $decoded['js']['data'] ?? $decoded['js'] ?? $decoded['data'] ?? $decoded;
            if (!is_array($channels)) {
                $errorMessage = "Invalid JSON input. Must be a valid Stalker channel list JSON or array.";
            } else {
                $gen = generateM3uFromChannels($channels, $macAddress, $streamTemplate, $portalUrl);
                $outputM3u = $gen['m3u'];
                $successCount = $gen['count'];
            }
        }
    }
}

// Download M3U action
if (isset($_POST['download']) && !empty($outputM3u)) {
    header('Content-Type: audio/x-mpegurl');
    header('Content-Disposition: attachment; filename="stalker_playlist.m3u8"');
    echo $outputM3u;
    exit;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Stalker Portal to M3U Generator</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-primary: #0f172a;
            --bg-card: #1e293b;
            --accent: #3b82f6;
            --accent-hover: #2563eb;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --border: #334155;
            --success: #10b981;
            --error: #ef4444;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', sans-serif; }
        body {
            background: var(--bg-primary);
            color: var(--text-main);
            min-height: 100vh;
            padding: 2rem 1rem;
        }
        .container {
            max-width: 950px;
            margin: 0 auto;
        }
        .header {
            text-align: center;
            margin-bottom: 2rem;
        }
        .header h1 {
            font-size: 2rem;
            font-weight: 700;
            background: linear-gradient(135deg, #60a5fa, #3b82f6);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            margin-bottom: 0.5rem;
        }
        .header p { color: var(--text-muted); font-size: 0.95rem; }
        .card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 2rem;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
            margin-bottom: 2rem;
        }
        .form-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1.25rem;
            margin-bottom: 1.25rem;
        }
        @media (max-width: 640px) { .form-grid { grid-template-columns: 1fr; } }
        .form-group { display: flex; flex-direction: column; gap: 0.5rem; }
        .form-group.full-width { grid-column: 1 / -1; }
        label { font-weight: 600; font-size: 0.85rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
        input[type="text"], input[type="number"], textarea, select {
            background: #0f172a;
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 0.75rem 1rem;
            color: var(--text-main);
            font-size: 0.95rem;
            transition: border-color 0.2s;
        }
        input[type="text"]:focus, input[type="number"]:focus, textarea:focus, select:focus {
            outline: none;
            border-color: var(--accent);
        }
        .btn-group { display: flex; gap: 1rem; margin-top: 1.5rem; }
        button {
            background: var(--accent);
            color: white;
            border: none;
            border-radius: 8px;
            padding: 0.75rem 1.5rem;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s, transform 0.1s;
        }
        button:hover { background: var(--accent-hover); }
        button:active { transform: scale(0.98); }
        .btn-secondary {
            background: #334155;
        }
        .btn-secondary:hover { background: #475569; }
        .alert {
            padding: 1rem;
            border-radius: 8px;
            margin-bottom: 1.5rem;
            font-size: 0.9rem;
        }
        .alert-error { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--error); color: #fca5a5; }
        .alert-success { background: rgba(16, 185, 129, 0.15); border: 1px solid var(--success); color: #6ee7b7; }
        .output-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.75rem;
        }
        .badge {
            background: rgba(59, 130, 246, 0.2);
            color: #93c5fd;
            padding: 0.25rem 0.75rem;
            border-radius: 9999px;
            font-size: 0.8rem;
            font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Stalker Portal to M3U Generator</h1>
            <p>Generate bufferless custom M3U playlists from any Stalker / Ministra Portal</p>
        </div>

        <?php if (!empty($errorMessage)): ?>
            <div class="alert alert-error"><?= htmlspecialchars($errorMessage) ?></div>
        <?php endif; ?>

        <?php if ($successCount > 0): ?>
            <div class="alert alert-success">Successfully generated <?= $successCount ?> channels!</div>
        <?php endif; ?>

        <div class="card">
            <form method="POST">
                <input type="hidden" name="action" value="generate">
                
                <div class="form-grid">
                    <div class="form-group">
                        <label>Input Mode</label>
                        <select name="inputMode" id="inputMode" onchange="toggleInputMode()">
                            <option value="fetch" <?= $inputMode === 'fetch' ? 'selected' : '' ?>>Auto Fetch from Portal API</option>
                            <option value="json" <?= $inputMode === 'json' ? 'selected' : '' ?>>Paste Raw Channel JSON</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label>MAC Address</label>
                        <input type="text" name="macAddress" value="<?= htmlspecialchars($macAddress) ?>" placeholder="e.g. 00:1A:2B:3C:4D:5E">
                    </div>

                    <div class="form-group full-width" id="portalUrlGroup">
                        <label>Stalker Portal Base URL</label>
                        <input type="text" name="portalUrl" value="<?= htmlspecialchars($portalUrl) ?>" placeholder="e.g. http://portal.example.com/c/">
                    </div>

                    <div class="form-group" id="timeoutGroup" style="display: <?= $inputMode === 'fetch' ? 'flex' : 'none' ?>;">
                        <label>Request Timeout (Seconds)</label>
                        <input type="number" name="timeout" value="<?= htmlspecialchars((string)$timeout) ?>" min="10" max="600" placeholder="120">
                    </div>

                    <div class="form-group full-width" id="jsonInputGroup" style="display: <?= $inputMode === 'json' ? 'flex' : 'none' ?>;">
                        <label>Stalker Channel List JSON</label>
                        <textarea name="rawJsonInput" rows="5" placeholder='Paste JSON returned by get_all_channels API...'></textarea>
                        <div style="background: rgba(59, 130, 246, 0.1); border: 1px solid rgba(59, 130, 246, 0.3); border-radius: 8px; padding: 1rem; margin-top: 0.5rem; font-size: 0.85rem; color: #93c5fd; line-height: 1.5;">
                            <b>💡 How to fetch raw JSON if your portal blocks or times out via API:</b><br>
                            If the portal drops connection attempts from this script's IP, run this command from your local computer terminal where your MAC address is authorized:
                            <div style="background: #0f172a; padding: 0.75rem; border-radius: 6px; margin-top: 0.5rem; font-family: monospace; font-size: 0.8rem; word-break: break-all; color: #f8fafc; user-select: all; border: 1px solid var(--border);">
                                curl -H "Cookie: mac=YOUR_MAC_ADDRESS; st=1; timezone=GMT" -H "User-Agent: Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG200 stu/200.01" "YOUR_PORTAL_BASE_URL/portal.php?type=itv&action=get_all_channels&JsHttpRequest=1-xml"
                            </div>
                            Copy the entire JSON output returned by that command and paste it into the box above to generate your M3U bufferless playlist immediately!
                        </div>
                    </div>

                    <div class="form-group full-width">
                        <label>Custom Stream URL Template <span style="text-transform: none; font-weight: normal; color: #64748b;">(Optional - auto-detected from JSON / Portal if empty)</span></label>
                        <input type="text" name="streamTemplate" value="<?= htmlspecialchars($streamTemplate) ?>" placeholder="e.g. http://your-server.com/play/live.php?mac={mac}&stream={stream_id}&extension=m3u8">
                        <span style="font-size: 0.75rem; color: var(--text-muted); margin-top: 4px;">
                            Leave empty to auto-generate from Portal & JSON. Or use placeholders: <b>{mac}</b>, <b>{stream_id}</b>, <b>{portal}</b>, <b>{cmd_encoded}</b>.<br>
                            💡 <b>Stalker create_link Example:</b> <code>{portal}/portal.php?type=itv&action=create_link&cmd={cmd_encoded}&JsHttpRequest=1-xml</code>
                        </span>
                    </div>
                </div>

                <div class="btn-group">
                    <button type="submit">⚡ Generate M3U Playlist</button>
                    <?php if (!empty($outputM3u)): ?>
                        <button type="submit" name="download" value="1" class="btn-secondary">💾 Download .m3u8 File</button>
                    <?php endif; ?>
                </div>
            </form>
        </div>

        <?php if (!empty($outputM3u)): ?>
        <div class="card">
            <div class="output-header">
                <h3>Generated M3U Playlist</h3>
                <span class="badge"><?= $successCount ?> Channels</span>
            </div>
            <textarea id="m3uOutput" rows="12" readonly style="width: 100%; font-family: monospace; font-size: 0.85rem;"><?= htmlspecialchars($outputM3u) ?></textarea>
            <div class="btn-group" style="margin-top: 1rem;">
                <button type="button" onclick="copyM3u()" class="btn-secondary">📋 Copy M3U to Clipboard</button>
            </div>
        </div>
        <?php endif; ?>
    </div>

    <script>
        function toggleInputMode() {
            const mode = document.getElementById('inputMode').value;
            document.getElementById('portalUrlGroup').style.display = 'flex';
            document.getElementById('timeoutGroup').style.display = (mode === 'fetch') ? 'flex' : 'none';
            document.getElementById('jsonInputGroup').style.display = (mode === 'json') ? 'flex' : 'none';
        }
        function copyM3u() {
            const el = document.getElementById('m3uOutput');
            el.select();
            document.execCommand('copy');
            alert('M3U playlist copied to clipboard!');
        }
    </script>
</body>
</html>
