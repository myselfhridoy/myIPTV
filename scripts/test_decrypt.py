import urllib.request
import base64
import json
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad

url = 'https://myselfhridoy.github.io/LiveEventSchedule/api/live_tv/live_channels.enc'
data = urllib.request.urlopen(url).read().decode('utf-8')
key = b'X3#yS@6k~u&hr$0z'
iv = b'n!0kfd&re%3#pw*s'

cipher = AES.new(key, AES.MODE_CBC, iv)
decrypted = unpad(cipher.decrypt(base64.b64decode(data)), AES.block_size).decode('utf-8')

channels = json.loads(decrypted)
for c in channels:
    if 'toffee' in str(c).lower() or 'tofee' in str(c).lower():
        print(json.dumps(c, indent=4))
