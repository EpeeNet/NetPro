import re
import base64
import os

file_path = "/Users/macflurry/Downloads/NetPro-main/EPEE/NetPro/EPEE/client/src/main/resources/background.svg"

try:
    with open(file_path, "r") as f:
        content = f.read()
        
    # Find base64 data
    match = re.search(r'xlink:href="data:image/png;base64,([^"]+)"', content)
    if match:
        base64_data = match.group(1)
        png_data = base64.b64decode(base64_data)
        
        # Save as png
        new_path = file_path.replace(".svg", ".png")
        with open(new_path, "wb") as out:
            out.write(png_data)
        print(f"Converted {file_path} to {new_path}")
    else:
        print(f"No base64 data found in {file_path}")
except Exception as e:
    print(f"Error processing {file_path}: {e}")
