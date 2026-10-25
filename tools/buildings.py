"""Hand-authored blueprints for the mod.

Coordinates start at 0. Every connection-sensitive block (panes, bars, fences,
stairs) is finished to the shape vanilla will keep after a neighbor update,
so the construction engine's exact state check still passes.
"""

from __future__ import annotations

CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
DELTA = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}

FULL_EXACT = {
    "minecraft:stone",
    "minecraft:cobblestone",
    "minecraft:mossy_cobblestone",
    "minecraft:cobbled_deepslate",
    "minecraft:polished_deepslate",
    "minecraft:bookshelf",
    "minecraft:hay_block",
    "minecraft:bricks",
    "minecraft:stone_bricks",
    "minecraft:mossy_stone_bricks",
    "minecraft:cracked_stone_bricks",
    "minecraft:chiseled_stone_bricks",
    "minecraft:deepslate_bricks",
    "minecraft:deepslate_tiles",
}
FULL_SUFFIXES = ("_planks", "_log", "_wood", "_hyphae", "_terracotta", "_wool", "_bricks")

NONE_EXACT = {
    "minecraft:ladder",
    "minecraft:campfire",
    "minecraft:soul_campfire",
    "minecraft:chest",
    "minecraft:trapped_chest",
    "minecraft:barrel",
    "minecraft:furnace",
    "minecraft:smoker",
    "minecraft:blast_furnace",
    "minecraft:crafting_table",
    "minecraft:anvil",
    "minecraft:chipped_anvil",
    "minecraft:damaged_anvil",
    "minecraft:cauldron",
    "minecraft:composter",
    "minecraft:stonecutter",
    "minecraft:flower_pot",
    "minecraft:chain",
    "minecraft:lectern",
}
NONE_SUFFIXES = (
    "_door",
    "_bed",
    "_carpet",
    "_banner",
    "_torch",
    "_button",
    "_pressure_plate",
    "_trapdoor",
    "_candle",
)


def _prop(value) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def is_full(block: str) -> bool:
    return block in FULL_EXACT or block.endswith(FULL_SUFFIXES)


def is_pane(block: str) -> bool:
    return block.endswith("_pane") or block.endswith("_bars")


def is_fence(block: str) -> bool:
    return block.endswith("_fence")


def is_gate(block: str) -> bool:
    return block.endswith("_fence_gate")


def is_none_block(block: str) -> bool:
    if block.startswith("minecraft:potted_"):
        return True
    if "lantern" in block:
        return True
    if block.endswith(NONE_SUFFIXES):
        return True
    return block in NONE_EXACT


class Structure:
    def __init__(self):
        self.cells: dict[tuple[int, int, int], dict] = {}

    def set(self, x, y, z, block, **props):
        if x < 0 or y < 0 or z < 0:
            raise ValueError(f"negative coordinate {x},{y},{z} for {block}")
        self.cells[(x, y, z)] = {
            "x": x,
            "y": y,
            "z": z,
            "block": block,
            "properties": {key: _prop(value) for key, value in props.items()},
        }

    def fill(self, x0, y0, z0, x1, y1, z1, block, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block, **props)

    def walls(self, x0, y0, z0, x1, y1, z1, block, **props):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.set(x, y, min(z0, z1), block, **props)
                self.set(x, y, max(z0, z1), block, **props)
            for z in range(min(z0, z1) + 1, max(z0, z1)):
                self.set(min(x0, x1), y, z, block, **props)
                self.set(max(x0, x1), y, z, block, **props)

    def pop(self, x, y, z):
        self.cells.pop((x, y, z), None)

    def block_at(self, x, y, z):
        cell = self.cells.get((x, y, z))
        return None if cell is None else cell["block"]


def stair(s: Structure, x, y, z, block, facing, shape="straight", half="bottom"):
    s.set(x, y, z, block, facing=facing, half=half, shape=shape, waterlogged="false")


def door(s: Structure, x, y, z, block, facing="south", hinge="left"):
    common = dict(facing=facing, hinge=hinge, open="false", powered="false")
    s.set(x, y, z, block, half="lower", **common)
    s.set(x, y + 1, z, block, half="upper", **common)


def bed(s: Structure, x, y, z, color, facing):
    offset = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[facing]
    block = f"minecraft:{color}_bed"
    s.set(x, y, z, block, facing=facing, part="foot", occupied="false")
    s.set(x + offset[0], y, z + offset[1], block, facing=facing, part="head", occupied="false")


def pane(s: Structure, x, y, z, block="minecraft:glass_pane"):
    s.set(x, y, z, block, north="false", south="false", east="false", west="false", waterlogged="false")


def bars(s: Structure, x, y, z):
    pane(s, x, y, z, "minecraft:iron_bars")


def fence(s: Structure, x, y, z, block="minecraft:oak_fence"):
    s.set(x, y, z, block, north="false", south="false", east="false", west="false", waterlogged="false")


def log(s: Structure, x, y, z, block, axis):
    s.set(x, y, z, block, axis=axis)


def lantern(s: Structure, x, y, z, hanging=False, soul=False):
    block = "minecraft:soul_lantern" if soul else "minecraft:lantern"
    s.set(x, y, z, block, hanging="true" if hanging else "false", waterlogged="false")


def gable(s: Structure, x0, x1, z0, z1, y, stair_block, fill):
    left, right = x0, x1
    level = y
    while left < right:
        for z in range(z0, z1 + 1):
            stair(s, left, level, z, stair_block, "east")
            stair(s, right, level, z, stair_block, "west")
            if right - left > 1:
                s.fill(left + 1, level, z, right - 1, level, z, fill)
        left += 1
        right -= 1
        level += 1
    if left == right:
        s.fill(left, level, z0, left, level, z1, fill)
    return level


def hip(s: Structure, x0, z0, x1, z1, y, stair_block, cap):
    while x0 < x1 and z0 < z1:
        for x in range(x0 + 1, x1):
            stair(s, x, y, z0, stair_block, "south")
            stair(s, x, y, z1, stair_block, "north")
        for z in range(z0 + 1, z1):
            stair(s, x0, y, z, stair_block, "east")
            stair(s, x1, y, z, stair_block, "west")
        stair(s, x0, y, z0, stair_block, "east")
        stair(s, x1, y, z0, stair_block, "west")
        stair(s, x0, y, z1, stair_block, "east")
        stair(s, x1, y, z1, stair_block, "west")
        if x1 - x0 > 1 and z1 - z0 > 1:
            s.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, cap)
        x0 += 1
        z0 += 1
        x1 -= 1
        z1 -= 1
        y += 1
    if x0 == x1 and z0 == z1:
        s.set(x0, y, z0, cap)
    return y


def masonry(x, y, z) -> str:
    kind = (x * 13 + z * 7 + y * 3) % 11
    if kind == 0:
        return "minecraft:mossy_stone_bricks"
    if kind == 1:
        return "minecraft:cracked_stone_bricks"
    return "minecraft:stone_bricks"


def masonry_walls(s: Structure, x0, y0, z0, x1, y1, z1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            s.set(x, y, z0, masonry(x, y, z0))
            s.set(x, y, z1, masonry(x, y, z1))
        for z in range(z0 + 1, z1):
            s.set(x0, y, z, masonry(x0, y, z))
            s.set(x1, y, z, masonry(x1, y, z))


def timber_shell(s: Structure, x0, z0, x1, z1, y0, y1, plaster, beam):
    s.walls(x0, y0, z0, x1, y1, z1, plaster)
    for x in range(x0, x1 + 1):
        log(s, x, y0, z0, beam, "x")
        log(s, x, y1, z0, beam, "x")
        log(s, x, y0, z1, beam, "x")
        log(s, x, y1, z1, beam, "x")
    for z in range(z0 + 1, z1):
        log(s, x0, y0, z, beam, "z")
        log(s, x0, y1, z, beam, "z")
        log(s, x1, y0, z, beam, "z")
        log(s, x1, y1, z, beam, "z")


def relation(block: str | None, kind: str) -> str:
    if block is None:
        return "none"
    if kind == "pane" and (is_pane(block) or is_full(block)):
        return "connect"
    if kind == "fence" and (is_fence(block) or is_gate(block) or is_full(block)):
        return "connect"
    if is_none_block(block):
        return "none"
    if kind == "pane" and (is_fence(block) or is_gate(block)):
        return "none"
    if kind == "fence" and is_pane(block):
        return "none"
    return "error"


def stair_shape(s: Structure, x, y, z, facing, half) -> str:
    fx, fz = DELTA[facing]
    front = s.cells.get((x + fx, y, z + fz))
    if front and front["block"].endswith("_stairs") and front["properties"].get("half") == half:
        front_facing = front["properties"]["facing"]
        if DELTA[front_facing][0] != fx and DELTA[front_facing][1] != fz:
            side = OPP[front_facing]
            sx, sz = DELTA[side]
            neighbor = s.cells.get((x + sx, y, z + sz))
            blocked = (
                neighbor
                and neighbor["block"].endswith("_stairs")
                and neighbor["properties"].get("facing") == facing
                and neighbor["properties"].get("half") == half
            )
            if not blocked:
                return "outer_left" if front_facing == CCW[facing] else "outer_right"
    bx, bz = DELTA[OPP[facing]]
    back = s.cells.get((x + bx, y, z + bz))
    if back and back["block"].endswith("_stairs") and back["properties"].get("half") == half:
        back_facing = back["properties"]["facing"]
        if DELTA[back_facing][0] != fx and DELTA[back_facing][1] != fz:
            sx, sz = DELTA[back_facing]
            neighbor = s.cells.get((x + sx, y, z + sz))
            blocked = (
                neighbor
                and neighbor["block"].endswith("_stairs")
                and neighbor["properties"].get("facing") == facing
                and neighbor["properties"].get("half") == half
            )
            if not blocked:
                return "inner_left" if back_facing == CCW[facing] else "inner_right"
    return "straight"


def finalize(s: Structure):
    for (x, y, z), cell in s.cells.items():
        block = cell["block"]
        props = cell["properties"]
        if is_pane(block) or is_fence(block):
            kind = "pane" if is_pane(block) else "fence"
            for name, dx, dz in (("north", 0, -1), ("south", 0, 1), ("east", 1, 0), ("west", -1, 0)):
                other = s.block_at(x + dx, y, z + dz)
                how = relation(other, kind)
                if how == "error":
                    raise SystemExit(
                        f"{block} at {x},{y},{z} cannot sit beside {other} ({name}). "
                        "Move it next to a full block, air, or another connector."
                    )
                props[name] = "true" if how == "connect" else "false"
            props["waterlogged"] = "false"
        elif block.endswith("_stairs"):
            props["shape"] = stair_shape(s, x, y, z, props["facing"], props["half"])

    for (x, y, z), cell in s.cells.items():
        if not cell["block"].endswith("_stairs"):
            continue
        props = cell["properties"]
        again = stair_shape(s, x, y, z, props["facing"], props["half"])
        if again != props["shape"]:
            raise SystemExit(f"Unstable stair at {x},{y},{z}: {props['shape']} -> {again}")


def validate(s: Structure):
    for (x, y, z), cell in s.cells.items():
        block = cell["block"]
        props = cell["properties"]
        if block.endswith("_door") and props.get("half") == "lower":
            above = s.cells.get((x, y + 1, z))
            if above is None or above["block"] != block or above["properties"].get("half") != "upper":
                raise SystemExit(f"Door at {x},{y},{z} is missing its upper half")
        if block.endswith("_bed") and props.get("part") == "foot":
            dx, dz = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[props["facing"]]
            head = s.cells.get((x + dx, y, z + dz))
            if head is None or head["block"] != block or head["properties"].get("part") != "head":
                raise SystemExit(f"Bed at {x},{y},{z} is missing its head")
        if "lantern" in block and props.get("hanging") == "true":
            above = s.block_at(x, y + 1, z)
            if above is None or not is_full(above):
                raise SystemExit(f"Hanging lantern at {x},{y},{z} has nothing solid above it ({above})")


def emit(s: Structure):
    finalize(s)
    validate(s)
    return [s.cells[key] for key in sorted(s.cells, key=lambda pos: (pos[1], pos[2], pos[0]))]


def stone_keep():
    """Square keep: four towers, a gated curtain wall, and a lived-in courtyard."""
    s = Structure()
    deep = "minecraft:cobbled_deepslate"
    dark = "minecraft:deepslate_bricks"
    s.fill(0, 0, 0, 16, 0, 16, deep)
    s.fill(5, 0, 5, 11, 0, 11, "minecraft:mossy_cobblestone")
    s.fill(7, 0, 1, 8, 0, 6, "minecraft:stone_bricks")
    s.fill(7, 0, 10, 8, 0, 15, "minecraft:stone_bricks")

    def tower(x0, z0, ladder, facing, windows):
        masonry_walls(s, x0, 1, z0, x0 + 4, 8, z0 + 4)
        for y in range(1, 9):
            for cx, cz in ((x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4)):
                s.set(cx, y, cz, dark)
        s.fill(x0 + 1, 4, z0 + 1, x0 + 3, 4, z0 + 3, "minecraft:oak_planks")
        s.fill(x0 + 1, 8, z0 + 1, x0 + 3, 8, z0 + 3, "minecraft:oak_planks")
        lx, lz = ladder
        for y in range(1, 9):
            s.set(lx, y, lz, "minecraft:ladder", facing=facing, waterlogged="false")
        for x in range(x0, x0 + 5):
            for z in (z0, z0 + 4):
                if (x - x0) % 2 == 0:
                    s.set(x, 9, z, deep)
        for z in range(z0 + 1, z0 + 4):
            for x in (x0, x0 + 4):
                if (z - z0) % 2 == 0:
                    s.set(x, 9, z, deep)
        for x, y, z in windows:
            pane(s, x, y, z)
        lantern(s, x0 + 2, 3, z0 + 2, hanging=True)
        lantern(s, x0 + 2, 7, z0 + 2, hanging=True)

    tower(0, 0, (1, 1), "south", ((2, 3, 0), (2, 6, 0), (0, 3, 2), (0, 6, 2)))
    tower(12, 0, (15, 1), "south", ((14, 3, 0), (14, 6, 0), (16, 3, 2), (16, 6, 2)))
    tower(0, 12, (1, 15), "north", ((2, 3, 16), (2, 6, 16), (0, 3, 14), (0, 6, 14)))
    tower(12, 12, (15, 15), "north", ((14, 3, 16), (14, 6, 16), (16, 3, 14), (16, 6, 14)))

    def curtain_x(x0, x1, z, gate=False):
        for x in range(x0, x1 + 1):
            for y in range(1, 5):
                s.set(x, y, z, masonry(x, y, z))
            s.set(x, 5, z, dark)
            if not (gate and x in (7, 8)) and (x + z) % 2 == 0:
                s.set(x, 6, z, deep)

    def curtain_z(z0, z1, x):
        for z in range(z0, z1 + 1):
            for y in range(1, 5):
                s.set(x, y, z, masonry(x, y, z))
            s.set(x, 5, z, dark)
            if (x + z) % 2 == 0:
                s.set(x, 6, z, deep)

    curtain_x(5, 11, 0, gate=True)
    curtain_x(5, 11, 16)
    curtain_z(5, 11, 0)
    curtain_z(5, 11, 16)

    door(s, 7, 1, 0, "minecraft:spruce_door", "south", "right")
    door(s, 8, 1, 0, "minecraft:spruce_door", "south", "left")
    bars(s, 7, 3, 0)
    bars(s, 8, 3, 0)
    s.set(7, 4, 0, "minecraft:chiseled_stone_bricks")
    s.set(8, 4, 0, "minecraft:chiseled_stone_bricks")

    for x in (5, 6, 10, 11):
        pane(s, x, 3, 0)
    for x in (6, 10):
        pane(s, x, 3, 16)
    for z in (6, 10):
        pane(s, 0, 3, z)
        pane(s, 16, 3, z)

    s.set(6, 4, 1, "minecraft:blue_wall_banner", facing="south")
    s.set(9, 4, 1, "minecraft:white_wall_banner", facing="south")
    s.set(5, 2, 1, "minecraft:wall_torch", facing="south")
    s.set(10, 2, 1, "minecraft:wall_torch", facing="south")

    for x, z in ((7, 7), (9, 7), (7, 9), (9, 9), (7, 8), (9, 8), (8, 7), (8, 9)):
        s.set(x, 1, z, "minecraft:cobblestone")
    s.set(8, 1, 8, "minecraft:cauldron")

    s.set(6, 1, 6, "minecraft:campfire", facing="south", lit="true", signal_fire="false", waterlogged="false")
    s.set(15, 1, 6, "minecraft:chest", facing="west", type="single", waterlogged="false")
    s.set(15, 1, 7, "minecraft:furnace", facing="west", lit="false")
    s.set(15, 1, 8, "minecraft:blast_furnace", facing="west", lit="false")
    s.set(14, 1, 7, "minecraft:anvil", facing="north")
    s.set(14, 1, 8, "minecraft:barrel", facing="up", open="false")

    for x, z in ((5, 5), (11, 5), (5, 11), (11, 11)):
        s.set(x, 1, z, "minecraft:stone_bricks")
        lantern(s, x, 2, z)

    return emit(s)


def timber_manor():
    """Two-storey jetty house with a porch, a hearth, and a dark gable roof."""
    s = Structure()
    stone = "minecraft:stone_bricks"
    oak = "minecraft:oak_planks"
    beam = "minecraft:stripped_oak_log"
    plaster = "minecraft:white_terracotta"
    brick = "minecraft:bricks"
    dark = "minecraft:dark_oak_planks"
    dark_stair = "minecraft:dark_oak_stairs"

    s.fill(0, 0, 0, 12, 0, 10, stone)
    s.fill(2, 0, 3, 10, 0, 9, oak)
    s.fill(2, 0, 1, 10, 0, 1, oak)
    for x in range(5, 8):
        stair(s, x, 0, 0, "minecraft:spruce_stairs", "north")

    timber_shell(s, 1, 2, 11, 10, 1, 3, plaster, beam)
    for x in (1, 5, 7, 11):
        for y in range(1, 4):
            log(s, x, y, 2, beam, "y")
            log(s, x, y, 10, beam, "y")
    for z in (2, 6, 10):
        for y in range(1, 4):
            log(s, 1, y, z, beam, "y")
            log(s, 11, y, z, beam, "y")
    door(s, 6, 1, 2, "minecraft:dark_oak_door", "south", "left")
    for x in (3, 4, 8, 9):
        pane(s, x, 2, 2)
    for z in (4, 8):
        pane(s, 1, 2, z)
    for z in (4, 5):
        pane(s, 11, 2, z)

    for x in (2, 10):
        for y in range(1, 4):
            log(s, x, y, 1, beam, "y")
    for x in (3, 4, 5, 7, 8, 9):
        fence(s, x, 1, 1)
    s.set(6, 1, 1, "minecraft:oak_fence_gate", facing="south", in_wall="false", open="false", powered="false")
    s.set(4, 1, 0, "minecraft:potted_poppy")
    s.set(8, 1, 0, "minecraft:potted_dandelion")
    lantern(s, 6, 3, 1, hanging=True)

    stair(s, 2, 0, 4, "minecraft:oak_stairs", "north")
    s.set(2, 0, 5, oak)
    stair(s, 2, 1, 5, "minecraft:oak_stairs", "north")
    s.fill(2, 0, 6, 2, 1, 6, oak)
    stair(s, 2, 2, 6, "minecraft:oak_stairs", "north")
    s.fill(2, 0, 7, 2, 2, 7, oak)
    stair(s, 2, 3, 7, "minecraft:oak_stairs", "north")

    s.set(5, 1, 5, "minecraft:oak_slab", type="bottom", waterlogged="false")
    s.set(6, 1, 5, "minecraft:oak_slab", type="bottom", waterlogged="false")
    stair(s, 5, 1, 4, "minecraft:oak_stairs", "south")
    stair(s, 6, 1, 6, "minecraft:oak_stairs", "north")
    s.set(4, 1, 6, "minecraft:red_carpet")
    s.set(5, 1, 6, "minecraft:red_carpet")
    lantern(s, 5, 3, 6, hanging=True)
    s.set(8, 1, 3, "minecraft:crafting_table")
    s.set(9, 1, 3, "minecraft:furnace", facing="south", lit="false")
    s.set(9, 1, 4, "minecraft:smoker", facing="west", lit="false")
    s.set(10, 1, 3, "minecraft:barrel", facing="up", open="false")
    s.set(10, 1, 4, "minecraft:chest", facing="west", type="single", waterlogged="false")
    s.set(9, 0, 8, brick)
    s.set(10, 0, 8, brick)
    s.set(10, 1, 7, brick)
    s.set(10, 1, 9, brick)
    s.set(9, 1, 8, "minecraft:campfire", facing="east", lit="true", signal_fire="false", waterlogged="false")

    s.fill(0, 4, 1, 12, 4, 10, oak)
    s.pop(2, 4, 7)
    for x in range(0, 13):
        log(s, x, 4, 1, beam, "x")
        log(s, x, 4, 10, beam, "x")
    for z in range(2, 10):
        log(s, 0, 4, z, beam, "z")
        log(s, 12, 4, z, beam, "z")
    log(s, 0, 4, 1, beam, "y")
    log(s, 12, 4, 1, beam, "y")
    log(s, 0, 4, 10, beam, "y")
    log(s, 12, 4, 10, beam, "y")

    timber_shell(s, 0, 1, 12, 10, 5, 7, plaster, beam)
    for x in (0, 4, 8, 12):
        for y in range(5, 8):
            log(s, x, y, 1, beam, "y")
            log(s, x, y, 10, beam, "y")
    for z in (1, 6, 10):
        for y in range(5, 8):
            log(s, 0, y, z, beam, "y")
            log(s, 12, y, z, beam, "y")
    for x in (2, 3, 5, 6, 9, 10):
        pane(s, x, 6, 1)
    for x in (2, 3, 6, 7, 9):
        pane(s, x, 6, 10)
    for z in (3, 4, 8, 9):
        pane(s, 0, 6, z)
        pane(s, 12, 6, z)

    fence(s, 1, 5, 7)
    fence(s, 3, 5, 7)
    fence(s, 2, 5, 6)
    bed(s, 8, 5, 8, "red", "west")
    s.set(9, 5, 9, "minecraft:bookshelf")
    s.set(10, 5, 9, "minecraft:bookshelf")
    s.set(8, 5, 6, "minecraft:white_carpet")
    s.set(7, 5, 6, "minecraft:light_blue_carpet")
    lantern(s, 6, 7, 4, hanging=True)

    gable(s, 0, 12, 1, 10, 8, dark_stair, dark)
    for y in range(8, 15):
        log(s, 6, y, 1, beam, "y")
        log(s, 6, y, 10, beam, "y")
    for y in range(1, 14):
        s.set(10, y, 8, brick)
    s.set(10, 14, 8, "minecraft:brick_slab", type="bottom", waterlogged="false")
    return emit(s)


def watchtower():
    """Tall stone tower with an interior ladder, a wooden gallery, and a spire."""
    s = Structure()
    deep = "minecraft:cobbled_deepslate"
    brick = "minecraft:stone_bricks"
    dark = "minecraft:deepslate_bricks"
    floor = "minecraft:spruce_planks"

    s.fill(0, 0, 0, 6, 0, 6, deep)
    s.fill(1, 0, 1, 5, 0, 5, floor)
    s.walls(0, 1, 0, 6, 4, 6, brick)
    s.walls(0, 5, 0, 6, 5, 6, dark)
    door(s, 3, 1, 6, "minecraft:spruce_door", "south")
    pane(s, 1, 3, 6)
    pane(s, 5, 3, 6)
    pane(s, 0, 3, 2)
    pane(s, 0, 3, 4)
    pane(s, 6, 3, 2)
    pane(s, 6, 3, 4)

    s.walls(1, 6, 1, 5, 12, 5, brick)
    for y in range(6, 13):
        s.set(3, y, 0, dark)
    for y in (7, 8, 11):
        pane(s, 1, y, 3)
        pane(s, 5, y, 3)
        pane(s, 3, y, 5)
        pane(s, 2, y, 1)

    s.fill(1, 4, 1, 5, 4, 5, floor)
    s.fill(2, 8, 2, 4, 8, 4, floor)
    s.fill(2, 12, 2, 4, 12, 4, floor)
    s.fill(0, 13, 0, 6, 13, 6, "minecraft:oak_planks")
    for x in range(7):
        fence(s, x, 14, 0)
        fence(s, x, 14, 6)
    for z in range(1, 6):
        fence(s, 0, 14, z)
        fence(s, 6, 14, z)
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        log(s, x, 14, z, "minecraft:stripped_oak_log", "y")
        log(s, x, 15, z, "minecraft:stripped_oak_log", "y")
    for y in range(1, 14):
        s.set(3, y, 1, "minecraft:ladder", facing="south", waterlogged="false")

    s.set(5, 1, 4, "minecraft:barrel", facing="up", open="false")
    s.set(5, 1, 5, "minecraft:chest", facing="west", type="single", waterlogged="false")
    lantern(s, 4, 3, 4, hanging=True)
    lantern(s, 4, 7, 3, hanging=True)

    cap = hip(s, 1, 1, 5, 5, 16, "minecraft:spruce_stairs", floor)
    lantern(s, 3, cap + 1, 3, soul=True)
    return emit(s)


def hill_windmill():
    """Stone mill with a timber upper storey and a cloth sail crossed on the front."""
    s = Structure()
    stone = "minecraft:stone_bricks"
    deep = "minecraft:cobbled_deepslate"
    oak = "minecraft:oak_planks"
    beam = "minecraft:stripped_oak_log"
    wool = "minecraft:white_wool"

    s.fill(2, 0, 1, 8, 0, 7, deep)
    s.fill(3, 0, 2, 7, 0, 6, oak)
    s.set(7, 0, 0, stone)
    s.set(8, 0, 0, stone)
    s.fill(0, 0, 4, 1, 0, 5, deep)

    s.walls(2, 1, 1, 8, 3, 7, stone)
    for x in range(2, 9):
        stair(s, x, 4, 1, "minecraft:stone_brick_stairs", "north")
        stair(s, x, 4, 7, "minecraft:stone_brick_stairs", "south")
    for z in range(2, 7):
        stair(s, 2, 4, z, "minecraft:stone_brick_stairs", "east")
        stair(s, 8, 4, z, "minecraft:stone_brick_stairs", "west")
    stair(s, 2, 4, 1, "minecraft:stone_brick_stairs", "east")
    stair(s, 8, 4, 1, "minecraft:stone_brick_stairs", "west")
    stair(s, 2, 4, 7, "minecraft:stone_brick_stairs", "east")
    stair(s, 8, 4, 7, "minecraft:stone_brick_stairs", "west")
    s.set(4, 4, 1, stone)
    door(s, 7, 1, 1, "minecraft:spruce_door", "south")

    s.walls(3, 5, 1, 7, 9, 5, oak)
    for y in range(5, 10):
        for x, z in ((3, 1), (7, 1), (3, 5), (7, 5)):
            log(s, x, y, z, beam, "y")
    pane(s, 3, 7, 3)
    pane(s, 7, 7, 3)
    pane(s, 5, 7, 5)
    pane(s, 4, 7, 5)
    s.fill(3, 4, 2, 7, 4, 6, oak)

    for y in range(2, 14):
        for x in range(4, 7):
            s.set(x, y, 0, wool)
    for x in range(0, 11):
        for y in range(7, 10):
            s.set(x, y, 0, wool)
    log(s, 5, 8, 0, beam, "z")

    for y in range(1, 9):
        s.set(4, y, 2, "minecraft:ladder", facing="south", waterlogged="false")
    s.set(6, 1, 5, "minecraft:chest", facing="north", type="single", waterlogged="false")
    s.set(6, 1, 4, "minecraft:crafting_table")
    s.set(5, 1, 5, "minecraft:stonecutter", facing="south")
    lantern(s, 5, 3, 4, hanging=True)

    s.set(0, 1, 4, "minecraft:hay_block", axis="y")
    s.set(0, 1, 5, "minecraft:hay_block", axis="y")
    s.set(1, 1, 4, "minecraft:barrel", facing="up", open="false")
    s.set(1, 1, 5, "minecraft:composter", level="4")

    hip(s, 3, 1, 7, 5, 10, "minecraft:spruce_stairs", "minecraft:spruce_planks")
    return emit(s)
