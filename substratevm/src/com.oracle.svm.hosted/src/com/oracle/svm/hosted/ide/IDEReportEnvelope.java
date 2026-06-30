/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.ide;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

/** Versioned deterministic envelope around an IDE report canonical payload. */
public final class IDEReportEnvelope {
    public static final byte[] MAGIC = "SVM_IDE_REPORT".getBytes(StandardCharsets.US_ASCII);
    public static final int ENVELOPE_VERSION = 1;
    public static final int PAYLOAD_KIND_JSON = 1;
    public static final int PAYLOAD_VERSION = 1;
    public static final int COMPRESSION_NONE = 0;
    public static final int COMPRESSION_GZIP = 1;
    public static final int CHECKSUM_SHA256 = 1;
    public static final int COMPRESSION_THRESHOLD = 4096;
    public static final long DEFAULT_MAX_DECODED_PAYLOAD_BYTES = 512L * 1024 * 1024;
    public static final long MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES = 2_000_000_000L;

    private static final int SHA256_SIZE = 32;

    private IDEReportEnvelope() {
    }

    public static byte[] encode(byte[] payload, String producerVersion) {
        byte[] producerBytes = producerVersion.getBytes(StandardCharsets.UTF_8);
        if (producerBytes.length > 0xffff) {
            throw new IllegalArgumentException("IDE report producer version is too long");
        }
        byte[] compressed = payload.length >= COMPRESSION_THRESHOLD ? gzip(payload) : payload;
        int compression = compressed != payload && compressed.length < payload.length ? COMPRESSION_GZIP : COMPRESSION_NONE;
        byte[] storedPayload = compression == COMPRESSION_GZIP ? compressed : payload;
        byte[] checksum = sha256(payload);

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(MAGIC);
                output.writeShort(ENVELOPE_VERSION);
                output.writeShort(producerBytes.length);
                output.write(producerBytes);
                output.writeShort(PAYLOAD_KIND_JSON);
                output.writeShort(PAYLOAD_VERSION);
                output.writeByte(compression);
                output.writeLong(payload.length);
                output.writeLong(storedPayload.length);
                output.writeByte(CHECKSUM_SHA256);
                output.write(checksum);
                output.write(storedPayload);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Decoded decode(byte[] envelope) {
        return decode(envelope, DEFAULT_MAX_DECODED_PAYLOAD_BYTES);
    }

    public static Decoded decode(byte[] envelope, long maxDecodedPayloadBytes) {
        validateDecodedPayloadLimit(maxDecodedPayloadBytes);
        try {
            ByteBuffer input = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN);
            requireRemaining(input, MAGIC.length + Short.BYTES * 2);
            byte[] magic = new byte[MAGIC.length];
            input.get(magic);
            if (!Arrays.equals(MAGIC, magic)) {
                throw new IllegalArgumentException("Invalid IDE report envelope magic");
            }
            int envelopeVersion = Short.toUnsignedInt(input.getShort());
            if (envelopeVersion != ENVELOPE_VERSION) {
                throw new IllegalArgumentException("Unsupported IDE report envelope version: " + envelopeVersion);
            }
            int producerLength = Short.toUnsignedInt(input.getShort());
            requireRemaining(input, producerLength + Short.BYTES * 2 + Byte.BYTES + Long.BYTES * 2 + Byte.BYTES + SHA256_SIZE);
            byte[] producerBytes = new byte[producerLength];
            input.get(producerBytes);
            String producerVersion = new String(producerBytes, StandardCharsets.UTF_8);
            int payloadKind = Short.toUnsignedInt(input.getShort());
            int payloadVersion = Short.toUnsignedInt(input.getShort());
            if (payloadKind != PAYLOAD_KIND_JSON || payloadVersion != PAYLOAD_VERSION) {
                throw new IllegalArgumentException("Unsupported IDE report payload kind or version: " + payloadKind + "/" + payloadVersion);
            }
            int compression = Byte.toUnsignedInt(input.get());
            if (compression != COMPRESSION_NONE && compression != COMPRESSION_GZIP) {
                throw new IllegalArgumentException("Unsupported IDE report compression: " + compression);
            }
            long uncompressedSize = input.getLong();
            long storedSize = input.getLong();
            if (uncompressedSize < 0 || uncompressedSize > maxDecodedPayloadBytes) {
                throw new IllegalArgumentException("IDE report payload exceeds the " + maxDecodedPayloadBytes + " byte limit");
            }
            if (storedSize < 0 || storedSize > maxDecodedPayloadBytes) {
                throw new IllegalArgumentException("Stored IDE report payload exceeds the " + maxDecodedPayloadBytes + " byte limit");
            }
            int checksumKind = Byte.toUnsignedInt(input.get());
            if (checksumKind != CHECKSUM_SHA256) {
                throw new IllegalArgumentException("Unsupported IDE report checksum: " + checksumKind);
            }
            byte[] expectedChecksum = new byte[SHA256_SIZE];
            input.get(expectedChecksum);
            if (input.remaining() != (int) storedSize) {
                throw new IllegalArgumentException("IDE report envelope payload size does not match the header");
            }
            byte[] storedPayload = new byte[(int) storedSize];
            input.get(storedPayload);
            byte[] payload = compression == COMPRESSION_GZIP ? gunzip(storedPayload, (int) uncompressedSize) : storedPayload;
            if (payload.length != (int) uncompressedSize) {
                throw new IllegalArgumentException("IDE report envelope uncompressed size does not match the header");
            }
            if (!MessageDigest.isEqual(expectedChecksum, sha256(payload))) {
                throw new IllegalArgumentException("IDE report envelope checksum mismatch");
            }
            return new Decoded(producerVersion, compression, payload);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid compressed IDE report payload", exception);
        }
    }

    public record Decoded(String producerVersion, int compression, byte[] payload) {
        public Decoded {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private static byte[] gzip(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(new byte[]{0x1f, (byte) 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, (byte) 0xff});
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
            try (DeflaterOutputStream compressed = new DeflaterOutputStream(output, deflater)) {
                compressed.write(payload);
            } finally {
                deflater.end();
            }
            writeLittleEndianInt(output, crc.getValue());
            writeLittleEndianInt(output, Integer.toUnsignedLong(payload.length));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] gunzip(byte[] payload, int expectedSize) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            byte[] result = new byte[expectedSize];
            int offset = 0;
            while (offset < result.length) {
                int count = input.read(result, offset, result.length - offset);
                if (count < 0) {
                    throw new IllegalArgumentException("IDE report envelope uncompressed size does not match the header");
                }
                offset += count;
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Compressed IDE report payload exceeds its declared size");
            }
            return result;
        }
    }

    private static void validateDecodedPayloadLimit(long maxDecodedPayloadBytes) {
        if (maxDecodedPayloadBytes <= 0 || maxDecodedPayloadBytes > MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("IDE report payload limit must be between 1 and " + MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES + " bytes");
        }
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void requireRemaining(ByteBuffer input, int required) {
        if (input.remaining() < required) {
            throw new IllegalArgumentException("Truncated IDE report envelope");
        }
    }

    private static void writeLittleEndianInt(ByteArrayOutputStream output, long value) {
        output.write((int) value & 0xff);
        output.write((int) (value >>> 8) & 0xff);
        output.write((int) (value >>> 16) & 0xff);
        output.write((int) (value >>> 24) & 0xff);
    }
}
