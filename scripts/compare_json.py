import urllib.request
import json
import base64
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend

key = b'X3#yS@6k~u&hr$0z'
iv = b'n!0kfd&re%3#pw*s'

netlify = urllib.request.urlopen('https://streambd-iptv.netlify.app/playlists/IPTV.json').read()
github_enc = urllib.request.urlopen('https://myselfhridoy.github.io/streambd-libs/playlists/IPTV.json').read()

encrypted = base64.b64decode(github_enc)
cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
decryptor = cipher.decryptor()
decrypted = decryptor.update(encrypted) + decryptor.finalize()

pad = decrypted[-1]
decrypted = decrypted[:-pad]

print('Netlify == GitHub?', netlify == decrypted)
print('Decrypted GitHub Starts With:', decrypted[:50])
