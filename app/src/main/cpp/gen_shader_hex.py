#!/usr/bin/env python3
"""
Generate hex array header from GLSL shader source
"""
import sys

def glsl_to_hex(input_file, output_file, array_name):
    with open(input_file, 'rb') as f:
        data = f.read()

    # Convert to hex array
    hex_data = ', '.join(f'0x{b:02x}' for b in data)

    # Generate header
    header = f"""// Generated from {input_file}
static const unsigned char {array_name}[] = {{
{hex_data}
}};
"""

    with open(output_file, 'w') as f:
        f.write(header)

    print(f"Generated {output_file} from {input_file}")
    print(f"Array size: {len(data)} bytes")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python gen_shader_hex.py input.comp output.hex.h array_name")
        sys.exit(1)

    glsl_to_hex(sys.argv[1], sys.argv[2], sys.argv[3])
