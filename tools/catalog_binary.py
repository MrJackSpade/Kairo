"""Decoder for the compiled catalog parts written by shared/gradle/catalog-binary.gradle."""
import json
import struct


def decode_document(data):
    """One document as nested (key, value) lists, so key order is part of equality."""
    pos = 0

    def integer():
        nonlocal pos
        value = struct.unpack_from('>i', data, pos)[0]
        pos += 4
        return value

    def text():
        nonlocal pos
        length = integer()
        value = data[pos:pos + length].decode('utf-8')
        pos += length
        return value

    keys = [text() for _ in range(integer())]

    def value():
        nonlocal pos
        tag = data[pos]
        pos += 1
        if tag == 0: return None
        if tag == 1: return False
        if tag == 2: return True
        if tag == 3: return integer()
        if tag == 4:
            result = struct.unpack_from('>q', data, pos)[0]
            pos += 8
            return result
        if tag == 5:
            result = struct.unpack_from('>d', data, pos)[0]
            pos += 8
            return result
        if tag == 6: return text()
        if tag == 7: return ('object', [(keys[integer()], value()) for _ in range(integer())])
        if tag == 8: return ('array', [value() for _ in range(integer())])
        raise ValueError(f'Unknown catalog value tag {tag}')

    result = value()
    assert pos == len(data), 'Trailing bytes after catalog document'
    return result


def ordered(value):
    """Parsed JSON in the same shape decode_document returns."""
    if isinstance(value, dict): return ('object', [(k, ordered(v)) for k, v in value.items()])
    if isinstance(value, list): return ('array', [ordered(v) for v in value])
    return value


def verify_part(index_bytes, blob, source_documents):
    """Asserts a compiled <part>.idx + <part>.bin hold exactly the source part's documents."""
    index = json.loads(index_bytes)
    assert index['schemaVersion'] == 2, 'Compiled catalog index must be schema 2'
    ranges = index['documents']
    assert list(ranges) == sorted(source_documents), 'Compiled index does not list the source documents'
    expected_offset = 0
    for name, (offset, size) in ranges.items():
        assert offset == expected_offset, f'Gap or overlap before {name}'
        assert decode_document(blob[offset:offset + size]) == ordered(source_documents[name]), name
        expected_offset = offset + size
    assert expected_offset == len(blob), 'Trailing bytes after the last catalog document'
