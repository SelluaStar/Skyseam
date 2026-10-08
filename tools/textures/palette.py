"""The shared Skyseam palette (spec section 20: one palette file so the whole texture set stays coherent).

Pastel glass from reference image 2: lavender sky, peach and pink light, teal mirror water, and the white-gold of
the Seam's outline and threads. Colours are (r, g, b) in 0-255. Every texture script imports from here.
"""

# Halcyon sky, top to bottom.
SKY_ZENITH = (104, 96, 168)
SKY_HIGH = (150, 128, 196)
SKY_LILAC = (206, 160, 214)
SKY_GLOW = (255, 198, 190)
HORIZON = (255, 228, 206)

# Mirror Sea, horizon to deep.
SEA_SHALLOW = (150, 214, 210)
SEA_TEAL = (112, 190, 196)
SEA_DEEP = (92, 138, 176)

# Light.
LANTERN_CORE = (255, 253, 246)
LANTERN_GLOW = (255, 214, 176)
PEARL = (247, 242, 255)
GOLD = (255, 204, 90)
GOLD_LIGHT = (255, 232, 160)

# Prismatic interior tints (pink, orange, teal) and the dust and mote pastels.
PRISM_PINK = (255, 178, 214)
PRISM_ORANGE = (255, 196, 150)
PRISM_TEAL = (150, 238, 226)
PASTEL_LAVENDER = (210, 196, 255)

# Distant island silhouettes.
ISLAND_FAR = (176, 150, 200)
ISLAND_NEAR = (150, 128, 186)
