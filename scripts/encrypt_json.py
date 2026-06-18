import base64
import json
import sys
import os
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

# Same keys as used in the Android App (CryptoUtils.kt)
AES_KEY = b"X3#yS@6k~u&hr$0z"
AES_IV = b"n!0kfd&re%3#pw*s"

def encrypt_file(input_file_path, output_file_path):
    # Read the JSON content
    with open(input_file_path, 'r', encoding='utf-8') as f:
        data = f.read()

    # Create cipher object and encrypt the data
    cipher = AES.new(AES_KEY, AES.MODE_CBC, AES_IV)
    # Pad the data to block size (16 bytes)
    padded_data = pad(data.encode('utf-8'), AES.block_size)
    
    encrypted_bytes = cipher.encrypt(padded_data)
    
    # Encode with base64 so it can be safely stored as text
    encrypted_base64 = base64.b64encode(encrypted_bytes).decode('utf-8')

    # Save to the output file
    with open(output_file_path, 'w', encoding='utf-8') as f:
        f.write(encrypted_base64)
        
    print(f"Successfully encrypted {input_file_path} -> {output_file_path}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python encrypt_json.py <input.json> <output.enc>")
        sys.exit(1)
        
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    if not os.path.exists(input_file):
        print(f"Error: Input file {input_file} not found.")
        sys.exit(1)
        
    encrypt_file(input_file, output_file)
