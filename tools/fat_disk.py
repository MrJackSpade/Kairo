"""FAT disks for the PC boot test: empty hard disk images for its hardDisk check (Kairo#123), an MBR
with one FAT16 partition over the whole disk, which a machine's own DOS mounts as a drive (EmuTOS on
an ST, Nextor on an MSX's Sunrise IDE); and a file added to an existing FAT12 or FAT16 volume, such
as a test program on a DOS disk (Kairo98's mouse program).

    from fat_disk import mbr_fat16, add_file
    image = mbr_fat16(16)
    add_file(image_bytearray, volume_offset, "MOUSE98.COM", program)
"""

import struct

SECTOR = 512


def add_file(image, volume, name, data):
    """Writes data as file name (8.3, upper case) into the root directory of the FAT12 or FAT16
    volume whose boot sector is at byte volume of image (a bytearray), in free clusters chained in
    every FAT. A name already there, or no room, fails."""
    bps, per_cluster, reserved, fats, roots, total16, _, fat_sectors = struct.unpack("<HBHBHHBH", image[volume + 11:volume + 24])
    total = total16 or struct.unpack("<I", image[volume + 32:volume + 36])[0]
    root = volume + (reserved + fats * fat_sectors) * bps
    first_data = root + roots * 32
    clusters = (total - reserved - fats * fat_sectors - (roots * 32 + bps - 1) // bps) // per_cluster
    fat12 = clusters < 4085
    fat = volume + reserved * bps

    def entry(n):
        if fat12:
            at = fat + n * 3 // 2
            word = image[at] | image[at + 1] << 8
            return (word >> 4) if n & 1 else (word & 0xFFF)
        return struct.unpack("<H", image[fat + n * 2:fat + n * 2 + 2])[0]

    def set_entry(n, value):
        for copy in range(fats):
            base = fat + copy * fat_sectors * bps
            if fat12:
                at = base + n * 3 // 2
                word = image[at] | image[at + 1] << 8
                word = (word & 0x000F) | (value << 4) if n & 1 else (word & 0xF000) | value
                image[at], image[at + 1] = word & 0xFF, word >> 8
            else:
                image[base + n * 2:base + n * 2 + 2] = struct.pack("<H", value)

    stem, _, ext = name.upper().partition(".")
    short = (stem.ljust(8) + ext.ljust(3)).encode("ascii")
    if len(short) != 11:
        raise SystemExit(f"{name} is not an 8.3 name")
    slot = None
    for i in range(roots):
        at = root + i * 32
        if image[at] in (0x00, 0xE5):
            if slot is None:
                slot = at
            if image[at] == 0x00:
                break
        elif image[at:at + 11] == short:
            raise SystemExit(f"{name} is already on the disk")
    if slot is None:
        raise SystemExit("the root directory is full")
    cluster_bytes = bps * per_cluster
    needed = max(1, (len(data) + cluster_bytes - 1) // cluster_bytes)
    free = [n for n in range(2, clusters + 2) if entry(n) == 0][:needed]
    if len(free) < needed:
        raise SystemExit(f"{name} needs {needed} clusters; the disk has {len(free)} free")
    last = 0xFFF if fat12 else 0xFFFF
    for n, cluster in enumerate(free):
        set_entry(cluster, free[n + 1] if n + 1 < len(free) else last)
        part = data[n * cluster_bytes:(n + 1) * cluster_bytes]
        at = first_data + (cluster - 2) * cluster_bytes
        image[at:at + cluster_bytes] = part.ljust(cluster_bytes, b"\0")
    record = bytearray(32)
    record[0:11] = short
    record[11] = 0x20
    record[26:28] = struct.pack("<H", free[0])
    record[28:32] = struct.pack("<I", len(data))
    image[slot:slot + 32] = record


def mbr_fat16(megabytes):
    """A disk of megabytes MB: an MBR with one FAT16 partition under 32 MB (type 04) from sector 1 to
    the end, formatted and empty: 4 sectors a cluster, 1 reserved, 2 FATs of 32 sectors, 512 root
    entries."""
    sectors = megabytes * 1024 * 1024 // SECTOR
    start = 1
    part = sectors - start
    if part >= 65536:
        raise SystemExit(f"a {megabytes} MB disk does not fit a FAT16 partition under 32 MB")
    image = bytearray(sectors * SECTOR)
    entry = bytes([0x00, 0, 0, 0, 0x04, 0, 0, 0]) + start.to_bytes(4, "little") + part.to_bytes(4, "little")
    image[446:462] = entry
    image[510:512] = b"\x55\xAA"
    per_cluster, reserved, fats, fat_sectors, roots = 4, 1, 2, 32, 512
    boot = bytearray(SECTOR)
    boot[0:3] = b"\xEB\x3C\x90"
    boot[3:11] = b"KAIRO   "
    boot[11:13] = SECTOR.to_bytes(2, "little")
    boot[13] = per_cluster
    boot[14:16] = reserved.to_bytes(2, "little")
    boot[16] = fats
    boot[17:19] = roots.to_bytes(2, "little")
    boot[19:21] = part.to_bytes(2, "little")
    boot[21] = 0xF8
    boot[22:24] = fat_sectors.to_bytes(2, "little")
    boot[24:26] = (32).to_bytes(2, "little")
    boot[26:28] = (64).to_bytes(2, "little")
    boot[28:32] = start.to_bytes(4, "little")
    boot[38] = 0x29
    boot[43:54] = b"KAIRO TEST "
    boot[54:62] = b"FAT16   "
    boot[510:512] = b"\x55\xAA"
    clusters = (part - reserved - fats * fat_sectors - roots * 32 // SECTOR) // per_cluster
    if not 4085 <= clusters < 65525 or (clusters + 2) * 2 > fat_sectors * SECTOR:
        raise SystemExit(f"{clusters} clusters do not make a FAT16 of {fat_sectors} sectors")
    base = start * SECTOR
    image[base:base + SECTOR] = boot
    for fat in range(fats):
        at = base + (reserved + fat * fat_sectors) * SECTOR
        image[at:at + 4] = b"\xF8\xFF\xFF\xFF"
    return bytes(image)
