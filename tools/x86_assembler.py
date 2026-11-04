"""A small 8086 assembler for the PC boot test's DOS mouse programs (Kairo98's MOUSE98.COM,
KairoTowns' TOWNSMS.COM): bytes as the program gives them, with labels for short (rel8) and near
(rel16) jumps and 16-bit data addresses, at a .COM file's origin unless told another.

    a = Assembler()
    a.label("loop"); a.b(0xE4, 0x60); a.jcc(JNZ, "loop")
    a.label("x"); a.b(0, 0)
    code = a.build()
"""

JZ = JE = 0x74
JNZ = 0x75
JC = 0x72
JNC = 0x73
JGE = 0x7D
JLE = 0x7E
JMP_SHORT = 0xEB
LOOP = 0xE2


class Assembler:
    def __init__(self, origin=0x100):
        self.origin = origin
        self.code = bytearray()
        self.labels = {}
        self.fixups = []  # (offset, label, kind): "rel8", "rel16" or "abs16"

    def label(self, name):
        if name in self.labels:
            raise SystemExit(f"label {name} twice")
        self.labels[name] = self.origin + len(self.code)

    def b(self, *values):
        self.code += bytes(values)

    def w(self, value):
        """A 16-bit word: a number, or a label's address."""
        if isinstance(value, str):
            self.fixups.append((len(self.code), value, "abs16"))
            self.code += b"\0\0"
        else:
            self.code += (value & 0xFFFF).to_bytes(2, "little")

    def jcc(self, opcode, label):
        """A short jump (any rel8 opcode: Jcc, JMP short, LOOP) to label."""
        self.b(opcode, 0)
        self.fixups.append((len(self.code) - 1, label, "rel8"))

    def jmp_near(self, label):
        self.b(0xE9, 0, 0)
        self.fixups.append((len(self.code) - 2, label, "rel16"))

    def call(self, label):
        self.b(0xE8, 0, 0)
        self.fixups.append((len(self.code) - 2, label, "rel16"))

    def build(self):
        for offset, label, kind in self.fixups:
            target = self.labels[label]
            if kind == "abs16":
                self.code[offset:offset + 2] = target.to_bytes(2, "little")
            elif kind == "rel8":
                distance = target - (self.origin + offset + 1)
                if not -128 <= distance <= 127:
                    raise SystemExit(f"the jump to {label} is {distance} bytes")
                self.code[offset] = distance & 0xFF
            else:
                distance = target - (self.origin + offset + 2)
                self.code[offset:offset + 2] = (distance & 0xFFFF).to_bytes(2, "little")
        return bytes(self.code)
