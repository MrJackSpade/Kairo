"""Empty hard disk images for the PC boot test's hardDisk check (Kairo#123): an MBR with one FAT16
partition over the whole disk, which a machine's own DOS mounts as a drive (EmuTOS on an ST, Nextor on
an MSX's Sunrise IDE).

    from fat_disk import mbr_fat16
    image = mbr_fat16(16)
"""

SECTOR = 512


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
