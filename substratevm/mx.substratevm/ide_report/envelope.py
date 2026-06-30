#
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

"""Versioned deterministic envelope for canonical IDE report payloads."""

import gzip
import hashlib
import hmac
import io
import struct
import zlib
from dataclasses import dataclass


MAGIC = b"SVM_IDE_REPORT"
ENVELOPE_VERSION = 1
PAYLOAD_KIND_JSON = 1
PAYLOAD_VERSION = 1
COMPRESSION_NONE = 0
COMPRESSION_GZIP = 1
CHECKSUM_SHA256 = 1
COMPRESSION_THRESHOLD = 4096
DEFAULT_MAX_DECODED_PAYLOAD_BYTES = 512 * 1024 * 1024
MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES = 2_000_000_000
_FIXED_HEADER = struct.Struct(">HH")
_PAYLOAD_HEADER = struct.Struct(">HHBQQB")
MAX_ENVELOPE_OVERHEAD = len(MAGIC) + _FIXED_HEADER.size + 0xFFFF + _PAYLOAD_HEADER.size + hashlib.sha256().digest_size


@dataclass(frozen=True)
class DecodedEnvelope:
    producer_version: str
    compression: int
    payload: bytes


def encode(payload, producer_version):
    producer = producer_version.encode("utf-8")
    if len(producer) > 0xFFFF:
        raise ValueError("IDE report producer version is too long")
    compressed = _gzip(payload) if len(payload) >= COMPRESSION_THRESHOLD else payload
    if compressed is not payload and len(compressed) < len(payload):
        compression = COMPRESSION_GZIP
        stored = compressed
    else:
        compression = COMPRESSION_NONE
        stored = payload
    return b"".join(
        [
            MAGIC,
            _FIXED_HEADER.pack(ENVELOPE_VERSION, len(producer)),
            producer,
            _PAYLOAD_HEADER.pack(
                PAYLOAD_KIND_JSON, PAYLOAD_VERSION, compression, len(payload), len(stored), CHECKSUM_SHA256
            ),
            hashlib.sha256(payload).digest(),
            stored,
        ]
    )


def decode(envelope, max_decoded_payload_bytes=DEFAULT_MAX_DECODED_PAYLOAD_BYTES):
    validate_decoded_payload_limit(max_decoded_payload_bytes)
    cursor = 0
    if not envelope.startswith(MAGIC):
        raise ValueError("Invalid IDE report envelope magic")
    cursor += len(MAGIC)
    envelope_version, producer_length = _unpack(_FIXED_HEADER, envelope, cursor)
    cursor += _FIXED_HEADER.size
    if envelope_version != ENVELOPE_VERSION:
        raise ValueError("Unsupported IDE report envelope version: {}".format(envelope_version))
    producer_end = cursor + producer_length
    if producer_end > len(envelope):
        raise ValueError("Truncated IDE report envelope")
    producer_version = envelope[cursor:producer_end].decode("utf-8")
    cursor = producer_end
    payload_kind, payload_version, compression, uncompressed_size, stored_size, checksum_kind = _unpack(
        _PAYLOAD_HEADER, envelope, cursor
    )
    cursor += _PAYLOAD_HEADER.size
    if payload_kind != PAYLOAD_KIND_JSON or payload_version != PAYLOAD_VERSION:
        raise ValueError("Unsupported IDE report payload kind or version: {}/{}".format(payload_kind, payload_version))
    if compression not in (COMPRESSION_NONE, COMPRESSION_GZIP):
        raise ValueError("Unsupported IDE report compression: {}".format(compression))
    if uncompressed_size > max_decoded_payload_bytes:
        raise ValueError("IDE report payload exceeds the {} byte limit".format(max_decoded_payload_bytes))
    if stored_size > max_decoded_payload_bytes:
        raise ValueError("Stored IDE report payload exceeds the {} byte limit".format(max_decoded_payload_bytes))
    if checksum_kind != CHECKSUM_SHA256:
        raise ValueError("Unsupported IDE report checksum: {}".format(checksum_kind))
    checksum_end = cursor + hashlib.sha256().digest_size
    if checksum_end > len(envelope):
        raise ValueError("Truncated IDE report envelope")
    expected_checksum = envelope[cursor:checksum_end]
    cursor = checksum_end
    if len(envelope) - cursor != stored_size:
        raise ValueError("IDE report envelope payload size does not match the header")
    stored = envelope[cursor:]
    try:
        payload = _gunzip(stored, uncompressed_size) if compression == COMPRESSION_GZIP else stored
    except (EOFError, OSError, zlib.error) as exception:
        raise ValueError("Invalid compressed IDE report payload") from exception
    if len(payload) != uncompressed_size:
        raise ValueError("IDE report envelope uncompressed size does not match the header")
    if not hmac.compare_digest(expected_checksum, hashlib.sha256(payload).digest()):
        raise ValueError("IDE report envelope checksum mismatch")
    return DecodedEnvelope(producer_version=producer_version, compression=compression, payload=payload)


def _gzip(payload):
    compressor = zlib.compressobj(level=9, method=zlib.DEFLATED, wbits=-zlib.MAX_WBITS)
    compressed = compressor.compress(payload) + compressor.flush()
    trailer = struct.pack("<II", zlib.crc32(payload), len(payload) & 0xFFFFFFFF)
    return b"\x1f\x8b\x08\x00\x00\x00\x00\x00\x02\xff" + compressed + trailer


def _gunzip(stored, expected_size):
    with gzip.GzipFile(fileobj=io.BytesIO(stored), mode="rb") as compressed:
        payload = compressed.read(expected_size + 1)
    if len(payload) > expected_size:
        raise ValueError("Compressed IDE report payload exceeds its declared size")
    return payload


def validate_decoded_payload_limit(max_decoded_payload_bytes):
    if (
        not isinstance(max_decoded_payload_bytes, int)
        or isinstance(max_decoded_payload_bytes, bool)
        or max_decoded_payload_bytes <= 0
        or max_decoded_payload_bytes > MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES
    ):
        raise ValueError(
            "IDE report payload limit must be between 1 and {} bytes".format(MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES)
        )


def _unpack(formatter, data, offset):
    if len(data) - offset < formatter.size:
        raise ValueError("Truncated IDE report envelope")
    return formatter.unpack_from(data, offset)
