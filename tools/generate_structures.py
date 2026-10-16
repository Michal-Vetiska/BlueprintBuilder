#!/usr/bin/env python3
"""Write Minecraft structure NBT files for the demo cottage and construction camp.

The block lists in this file are the authoring source. The script emits gzip NBT
that Minecraft's structure template loader understands, plus a JSON fallback
the mod can compile into the same template when the NBT is absent.
"""

import gzip
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data" / "blueprintbuilder"
DATA_VERSION = 3955

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


def nbt_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def payload(tag_type: int, value) -> bytes:
    if tag_type == TAG_BYTE:
        return struct.pack(">b", value)
    if tag_type == TAG_SHORT:
        return struct.pack(">h", value)
    if tag_type == TAG_INT:
        return struct.pack(">i", value)
    if tag_type == TAG_STRING:
        return nbt_string(value)
    if tag_type == TAG_LIST:
        element_type, elements = value
        body = b"".join(payload(element_type, element) for element in elements)
        return bytes([element_type]) + struct.pack(">i", len(elements)) + body
    if tag_type == TAG_COMPOUND:
        return compound(value)
    raise TypeError(tag_type)


def compound(fields) -> bytes:
    out = bytearray()
    for tag_type, name, value in fields:
        out.append(tag_type)
        out += nbt_string(name)
        out += payload(tag_type, value)
    out.append(TAG_END)
    return bytes(out)


def named_root(fields) -> bytes:
    return bytes([TAG_COMPOUND]) + nbt_string("") + compound(fields)


def add(blocks, x, y, z, block, **properties):
    blocks.append({
        "x": x,
        "y": y,
        "z": z,
        "block": block,
        "properties": {key: str(value) for key, value in properties.items()},
    })


def cottage():
    blocks = []
    for x in range(7):
        for z in range(7):
            add(blocks, x, 0, z, "minecraft:cobblestone")
            if 1 <= x <= 5 and 1 <= z <= 5:
                add(blocks, x, 1, z, "minecraft:oak_planks")

    for y in range(1, 4):
        for x in range(7):
            for z in range(7):
                perimeter = x in (0, 6) or z in (0, 6)
                if not perimeter:
                    continue
                if x == 3 and z == 0 and y in (1, 2):
                    continue
                corner = x in (0, 6) and z in (0, 6)
                window = y == 2 and (
                    (x == 0 and z in (2, 4))
                    or (x == 6 and z in (2, 4))
                    or (z == 6 and x in (2, 4))
                )
                if corner:
                    add(blocks, x, y, z, "minecraft:oak_log", axis="y")
                elif window:
                    if x == 0 or x == 6:
                        add(blocks, x, y, z, "minecraft:glass_pane", north="true", south="true", east="false", west="false", waterlogged="false")
                    else:
                        add(blocks, x, y, z, "minecraft:glass_pane", east="true", west="true", north="false", south="false", waterlogged="false")
                else:
                    add(blocks, x, y, z, "minecraft:oak_planks")

    door = dict(facing="south", hinge="left", open="false", powered="false")
    add(blocks, 3, 1, 0, "minecraft:oak_door", half="lower", **door)
    add(blocks, 3, 2, 0, "minecraft:oak_door", half="upper", **door)

    def stairs(x, y, z, facing):
        add(blocks, x, y, z, "minecraft:spruce_stairs", facing=facing, half="bottom", shape="straight", waterlogged="false")

    for z in range(7):
        stairs(0, 4, z, "east")
        stairs(6, 4, z, "west")
        for x in range(1, 6):
            add(blocks, x, 4, z, "minecraft:oak_planks")
        stairs(1, 5, z, "east")
        stairs(5, 5, z, "west")
        for x in range(2, 5):
            add(blocks, x, 5, z, "minecraft:oak_planks")
        stairs(2, 6, z, "east")
        stairs(4, 6, z, "west")
        add(blocks, 3, 6, z, "minecraft:oak_planks")

    add(blocks, 2, 2, 4, "minecraft:white_bed", facing="south", part="foot", occupied="false")
    add(blocks, 2, 2, 5, "minecraft:white_bed", facing="south", part="head", occupied="false")
    add(blocks, 4, 2, 2, "minecraft:crafting_table")
    add(blocks, 3, 3, 3, "minecraft:lantern", hanging="true", waterlogged="false")
    add(blocks, 3, 2, 3, "minecraft:red_carpet")
    return blocks


def camp():
    blocks = []
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
        add(blocks, x, 0, z, "minecraft:oak_fence")
        add(blocks, x, 1, z, "minecraft:oak_fence")
    for x in range(5):
        for z in range(5):
            add(blocks, x, 2, z, "minecraft:white_wool")
    add(blocks, 2, 0, 2, "minecraft:campfire", lit="true", signal_fire="false", waterlogged="false", facing="north")
    add(blocks, 1, 0, 1, "minecraft:red_bed", facing="south", part="foot", occupied="false")
    add(blocks, 1, 0, 2, "minecraft:red_bed", facing="south", part="head", occupied="false")
    add(blocks, 3, 0, 1, "minecraft:crafting_table")
    add(blocks, 3, 0, 3, "minecraft:furnace", facing="north", lit="false")
    add(blocks, 2, 1, 2, "minecraft:lantern", hanging="true", waterlogged="false")
    return blocks


def write_nbt(blocks, path: Path):
    palette = []
    index = {}
    for block in blocks:
        key = (block["block"], tuple(sorted(block["properties"].items())))
        if key not in index:
            index[key] = len(palette)
            props = [(TAG_STRING, name, value) for name, value in sorted(block["properties"].items())]
            fields = [(TAG_STRING, "Name", block["block"])]
            if props:
                fields.append((TAG_COMPOUND, "Properties", props))
            palette.append(fields)

    block_tags = []
    for block in blocks:
        key = (block["block"], tuple(sorted(block["properties"].items())))
        block_tags.append([
            (TAG_LIST, "pos", (TAG_INT, [block["x"], block["y"], block["z"]])),
            (TAG_INT, "state", index[key]),
        ])

    max_x = max(block["x"] for block in blocks) + 1
    max_y = max(block["y"] for block in blocks) + 1
    max_z = max(block["z"] for block in blocks) + 1
    root = [
        (TAG_INT, "DataVersion", DATA_VERSION),
        (TAG_LIST, "size", (TAG_INT, [max_x, max_y, max_z])),
        (TAG_LIST, "entities", (TAG_COMPOUND, [])),
        (TAG_LIST, "blocks", (TAG_COMPOUND, block_tags)),
        (TAG_LIST, "palette", (TAG_COMPOUND, palette)),
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as handle:
        handle.write(named_root(root))


def write_blocks_json(blocks, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(blocks, indent=2) + "\n", encoding="utf-8")


def main():
    structures = {
        "medieval_cottage": cottage(),
        "construction_camp": camp(),
    }
    for name, blocks in structures.items():
        write_nbt(blocks, DATA / "structure" / f"{name}.nbt")
        write_blocks_json(blocks, DATA / "blueprint" / f"{name}.blocks.json")
        print(f"{name}: {len(blocks)} blocks -> {max(b['x'] for b in blocks)+1}x{max(b['y'] for b in blocks)+1}x{max(b['z'] for b in blocks)+1}")


if __name__ == "__main__":
    main()
