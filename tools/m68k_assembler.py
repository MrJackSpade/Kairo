"""A small 68000 assembler for the PC boot test's guest programs (KairoST's WRITE.TOS and READ.TOS,
Kairo68's MOUSE68.R): words as the program gives them, with labels for 16-bit PC-relative
displacements (Bcc.w, BSR.w, DBcc, LEA and PEA d16(PC)), measured from the displacement word, as
the 68000 does. The code is position independent: it has no relocations.

    a = Assembler()
    a.label("loop"); a.emit(0x0812, 4); a.branch(BEQ, "loop")
    a.data("text", b"KAIRO\\0")
    code = a.build()
"""

BRA, BSR, BNE, BEQ, BPL, BMI, BGE, BLT, BGT, BLE = (
    0x6000, 0x6100, 0x6600, 0x6700, 0x6A00, 0x6B00, 0x6C00, 0x6D00, 0x6E00, 0x6F00)


class Assembler:
    def __init__(self):
        self.code = bytearray()
        self.labels = {}
        self.fixups = []  # (offset of the displacement word, label)

    def emit(self, *words):
        for word in words:
            self.code += (word & 0xFFFF).to_bytes(2, "big")

    def label(self, name):
        if name in self.labels:
            raise SystemExit(f"label {name} twice")
        self.labels[name] = len(self.code)

    def data(self, name, data):
        """Bytes at label name, padded to a word."""
        self.label(name)
        self.code += data
        if len(self.code) % 2:
            self.code += b"\0"

    def displacement(self, label):
        self.fixups.append((len(self.code), label))
        self.emit(0)

    def branch(self, opcode, label):
        """Bcc.w or BSR.w (opcode with a zero byte displacement) to label."""
        self.emit(opcode)
        self.displacement(label)

    def dbra(self, register, label):
        self.emit(0x51C8 | register)
        self.displacement(label)

    def lea_pc(self, label, address_register):
        self.emit(0x41FA | address_register << 9)
        self.displacement(label)

    def pea(self, label):
        self.emit(0x487A)
        self.displacement(label)

    def build(self):
        for offset, label in self.fixups:
            distance = self.labels[label] - offset
            if not -32768 <= distance <= 32767:
                raise SystemExit(f"{label} is {distance} bytes away")
            self.code[offset:offset + 2] = distance.to_bytes(2, "big", signed=True)
        return bytes(self.code)
