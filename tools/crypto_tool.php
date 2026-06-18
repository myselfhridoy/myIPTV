<?php
// myIPTV Encryption/Decryption Tool
// Hardcoded Keys exactly matching the Android app
$AES_KEY = "X3#yS@6k~u&hr$0z";
$AES_IV  = "n!0kfd&re%3#pw*s";
$ALGO    = "aes-128-cbc";

$output = "";
$inputText = "";
$action = "";

if ($_SERVER["REQUEST_METHOD"] == "POST") {
    $inputText = $_POST["inputText"] ?? "";
    $action = $_POST["action"] ?? "";

    if (!empty($inputText)) {
        if ($action == "encrypt") {
            // PHP openssl_encrypt automatically handles PKCS7 padding (same as PKCS5 for AES)
            $encrypted = openssl_encrypt($inputText, $ALGO, $AES_KEY, OPENSSL_RAW_DATA, $AES_IV);
            if ($encrypted !== false) {
                // Return as Base64 string
                $output = base64_encode($encrypted);
            } else {
                $output = "Error: Failed to encrypt data.";
            }
        } elseif ($action == "decrypt") {
            // Decode Base64 first
            $decoded = base64_decode(trim($inputText), true);
            if ($decoded !== false) {
                $decrypted = openssl_decrypt($decoded, $ALGO, $AES_KEY, OPENSSL_RAW_DATA, $AES_IV);
                if ($decrypted !== false) {
                    $output = $decrypted;
                } else {
                    $output = "Error: Failed to decrypt data. Make sure it's valid Base64 and encrypted with the correct key.";
                }
            } else {
                $output = "Error: Invalid Base64 string.";
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>myIPTV Crypto Tool</title>
    <!-- Use Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0f172a;
            --surface-color: #1e293b;
            --primary-color: #0ea5e9;
            --primary-hover: #0284c7;
            --success-color: #10b981;
            --success-hover: #059669;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --border-color: #334155;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Inter', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 40px 20px;
        }

        .container {
            width: 100%;
            max-width: 900px;
            background: var(--surface-color);
            border-radius: 16px;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5);
            padding: 30px;
            border: 1px solid var(--border-color);
        }

        .header {
            text-align: center;
            margin-bottom: 30px;
        }

        .header h1 {
            font-size: 28px;
            font-weight: 700;
            color: var(--primary-color);
            margin-bottom: 8px;
        }

        .header p {
            color: var(--text-muted);
            font-size: 14px;
        }

        .grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 24px;
        }

        @media (max-width: 768px) {
            .grid {
                grid-template-columns: 1fr;
            }
        }

        .input-group {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        label {
            font-weight: 600;
            font-size: 14px;
            color: var(--text-main);
            display: flex;
            justify-content: space-between;
        }

        textarea {
            width: 100%;
            height: 350px;
            background-color: var(--bg-color);
            color: var(--text-main);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 16px;
            font-family: monospace;
            font-size: 14px;
            resize: none;
            outline: none;
            transition: border-color 0.2s;
        }

        textarea:focus {
            border-color: var(--primary-color);
        }

        .button-group {
            display: flex;
            gap: 12px;
            margin-top: 16px;
        }

        button {
            flex: 1;
            padding: 12px 24px;
            border: none;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            color: white;
        }

        .btn-encrypt {
            background-color: var(--primary-color);
        }

        .btn-encrypt:hover {
            background-color: var(--primary-hover);
        }

        .btn-decrypt {
            background-color: var(--success-color);
        }

        .btn-decrypt:hover {
            background-color: var(--success-hover);
        }

        .btn-copy {
            background-color: #475569;
            color: white;
            padding: 4px 12px;
            font-size: 12px;
            border-radius: 6px;
            width: auto;
            flex: none;
        }

        .btn-copy:hover {
            background-color: #64748b;
        }

        .copy-toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: var(--success-color);
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            font-weight: 600;
            opacity: 0;
            transform: translateY(20px);
            transition: all 0.3s;
            pointer-events: none;
        }

        .copy-toast.show {
            opacity: 1;
            transform: translateY(0);
        }
    </style>
</head>
<body>

    <div class="container">
        <div class="header">
            <h1>myIPTV Crypto Tool</h1>
            <p>Securely Encrypt & Decrypt your JSON Playlists and APIs without manually typing keys.</p>
        </div>

        <form method="POST" id="cryptoForm">
            <div class="grid">
                <!-- Input Area -->
                <div class="input-group">
                    <label>
                        Input (JSON or Base64)
                        <button type="button" class="btn-copy" onclick="document.getElementById('inputText').value = '';">Clear</button>
                    </label>
                    <textarea name="inputText" id="inputText" placeholder="Paste your raw JSON or Encrypted Base64 text here..."><?php echo htmlspecialchars($inputText); ?></textarea>
                    
                    <div class="button-group">
                        <button type="submit" name="action" value="encrypt" class="btn-encrypt">
                            <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
                            Encrypt
                        </button>
                        <button type="submit" name="action" value="decrypt" class="btn-decrypt">
                            <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z"></path></svg>
                            Decrypt
                        </button>
                    </div>
                </div>

                <!-- Output Area -->
                <div class="input-group">
                    <label>
                        Output Result
                        <button type="button" class="btn-copy" onclick="copyToClipboard()">Copy Result</button>
                    </label>
                    <textarea id="outputText" readonly placeholder="Result will appear here..."><?php echo htmlspecialchars($output); ?></textarea>
                </div>
            </div>
        </form>
    </div>

    <div class="copy-toast" id="toast">Copied to clipboard!</div>

    <script>
        function copyToClipboard() {
            var copyText = document.getElementById("outputText");
            if (!copyText.value) return;
            
            copyText.select();
            copyText.setSelectionRange(0, 9999999); // For mobile devices
            
            navigator.clipboard.writeText(copyText.value).then(() => {
                const toast = document.getElementById("toast");
                toast.classList.add("show");
                setTimeout(() => {
                    toast.classList.remove("show");
                }, 2000);
            }).catch(err => {
                console.error('Failed to copy: ', err);
                // Fallback for older browsers
                document.execCommand("copy");
                const toast = document.getElementById("toast");
                toast.classList.add("show");
                setTimeout(() => {
                    toast.classList.remove("show");
                }, 2000);
            });
        }
        
        // Form resubmission prevention
        if ( window.history.replaceState ) {
            window.history.replaceState( null, null, window.location.href );
        }
    </script>
</body>
</html>
