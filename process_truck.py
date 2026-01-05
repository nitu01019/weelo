from PIL import Image

# Open the truck container image
img = Image.open('/Users/nitishbhardwaj/Desktop/truck_container.svg.png')
img = img.convert('RGBA')

# Get image data
pixels = img.load()
width, height = img.size

# Remove white background - make it transparent
for y in range(height):
    for x in range(width):
        r, g, b, a = pixels[x, y]
        # Remove white and very light backgrounds
        if r > 230 and g > 230 and b > 230:
            pixels[x, y] = (r, g, b, 0)

# Save with transparency
img.save('/Users/nitishbhardwaj/Desktop/truck_container_transparent.png', 'PNG')
print("✓ Transparent truck icon created")
