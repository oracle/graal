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

//JAVA 17+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.22.0

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Small reference reader for Native Image IDE reports.
 *
 * Run it with {@code jbang IdeReportReader.java <report.json|image.ide-report>}.
 * The default command prints a summary; {@code --list} prints matching records
 * as JSON, optionally filtered by {@code --kind} or {@code --category}.
 *
 * The reusable API accepts canonical JSON payloads and version-1 split
 * envelopes from {@link ByteBuffer}. Locating an embedded section in a Mach-O
 * or ELF image remains the responsibility of an object-file-specific adapter.
 * Decoded payloads are limited to 512 MiB by default; use
 * {@code --max-payload-bytes} only for a trusted larger report.
 *
 * Exit status is 0 for success, 1 for an invalid report or I/O failure, and 2
 * for invalid command-line usage.
 */
public final class IdeReportReader {
    public static final long DEFAULT_MAX_PAYLOAD_BYTES = 512L * 1024 * 1024;
    public static final long MAX_CONFIGURABLE_PAYLOAD_BYTES = 2_000_000_000L;

    private static final byte[] MAGIC = "SVM_IDE_REPORT".getBytes(StandardCharsets.US_ASCII);
    private static final int ENVELOPE_VERSION = 1;
    private static final int PAYLOAD_KIND_JSON = 1;
    private static final int PAYLOAD_VERSION = 1;
    private static final int COMPRESSION_NONE = 0;
    private static final int COMPRESSION_GZIP = 1;
    private static final int CHECKSUM_SHA256 = 1;
    private static final int SHA256_SIZE = 32;
    private static final long MAX_ENVELOPE_OVERHEAD = 128 * 1024;

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public enum SourceFormat {
        JSON,
        SPLIT
    }

    public record DecodedEnvelope(String producerVersion, int compression, byte[] payload) {
        public String compressionName() {
            return compression == COMPRESSION_GZIP ? "gzip" : "none";
        }
    }

    public record Report(int schemaVersion, String payloadScope, List<ObjectNode> records,
                    List<ObjectNode> usedMethods, ObjectNode extensions) {
    }

    public record LoadedReport(SourceFormat sourceFormat, String producerVersion, String compression,
                    Report report) {
    }

    private IdeReportReader() {
    }

    public static DecodedEnvelope decodeEnvelope(ByteBuffer input, long maxPayloadBytes) {
        validateLimit(maxPayloadBytes);
        ByteBuffer buffer = input.slice().order(ByteOrder.BIG_ENDIAN);
        requireRemaining(buffer, MAGIC.length + 4);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw invalid("Invalid IDE report envelope magic");
        }

        int envelopeVersion = Short.toUnsignedInt(buffer.getShort());
        int producerLength = Short.toUnsignedInt(buffer.getShort());
        if (envelopeVersion != ENVELOPE_VERSION) {
            throw invalid("Unsupported IDE report envelope version: " + envelopeVersion);
        }
        requireRemaining(buffer, producerLength + 22 + SHA256_SIZE);
        byte[] producer = new byte[producerLength];
        buffer.get(producer);
        String producerVersion = decodeUtf8(producer, "producer version");

        int payloadKind = Short.toUnsignedInt(buffer.getShort());
        int payloadVersion = Short.toUnsignedInt(buffer.getShort());
        int compression = Byte.toUnsignedInt(buffer.get());
        long uncompressedSize = buffer.getLong();
        long storedSize = buffer.getLong();
        int checksumKind = Byte.toUnsignedInt(buffer.get());
        if (payloadKind != PAYLOAD_KIND_JSON || payloadVersion != PAYLOAD_VERSION) {
            throw invalid("Unsupported IDE report payload kind or version: " + payloadKind + "/" + payloadVersion);
        }
        if (compression != COMPRESSION_NONE && compression != COMPRESSION_GZIP) {
            throw invalid("Unsupported IDE report compression: " + compression);
        }
        if (checksumKind != CHECKSUM_SHA256) {
            throw invalid("Unsupported IDE report checksum: " + checksumKind);
        }
        if (uncompressedSize < 0 || uncompressedSize > maxPayloadBytes) {
            throw invalid("IDE report payload exceeds the " + maxPayloadBytes + " byte limit");
        }
        if (storedSize < 0 || storedSize > maxPayloadBytes || storedSize != buffer.remaining() - SHA256_SIZE) {
            throw invalid("IDE report envelope payload size does not match the header");
        }

        byte[] expectedChecksum = new byte[SHA256_SIZE];
        buffer.get(expectedChecksum);
        byte[] stored = new byte[(int) storedSize];
        buffer.get(stored);
        byte[] payload = compression == COMPRESSION_GZIP
                        ? decompress(stored, uncompressedSize)
                        : stored;
        if (payload.length != uncompressedSize) {
            throw invalid("IDE report envelope uncompressed size does not match the header");
        }
        if (!MessageDigest.isEqual(expectedChecksum, sha256(payload))) {
            throw invalid("IDE report envelope checksum mismatch");
        }
        return new DecodedEnvelope(producerVersion, compression, payload);
    }

    public static DecodedEnvelope decodeEnvelope(byte[] input, long maxPayloadBytes) {
        return decodeEnvelope(ByteBuffer.wrap(input), maxPayloadBytes);
    }

    public static Report parsePayload(ByteBuffer payload) {
        byte[] bytes = new byte[payload.remaining()];
        payload.slice().get(bytes);
        return parsePayload(bytes);
    }

    public static Report parsePayload(byte[] payload) {
        final JsonNode parsed;
        try {
            parsed = JSON.readTree(payload);
        } catch (IOException exception) {
            throw invalid("Invalid canonical IDE report JSON: " + exception.getMessage(), exception);
        }
        if (!(parsed instanceof ObjectNode root)) {
            throw invalid("Canonical IDE report payload must be a JSON object");
        }

        int schemaVersion = requiredInteger(root, "schema_version");
        if (schemaVersion != PAYLOAD_VERSION) {
            throw invalid("Unsupported canonical IDE report schema version: " + schemaVersion);
        }
        String payloadScope = requiredText(root, "payload_scope");
        if (!payloadScope.equals("full") && !payloadScope.equals("minimal")) {
            throw invalid("Unsupported canonical IDE report payload scope: " + payloadScope);
        }
        List<ObjectNode> records = requiredObjectArray(root, "records");
        List<ObjectNode> usedMethods = requiredObjectArray(root, "used_methods");
        JsonNode extensions = root.get("extensions");
        if (!(extensions instanceof ObjectNode extensionObject)) {
            throw invalid("Canonical IDE report field 'extensions' must be an object");
        }
        return new Report(schemaVersion, payloadScope, records, usedMethods, extensionObject);
    }

    public static LoadedReport read(Path path, long maxPayloadBytes) throws IOException {
        validateLimit(maxPayloadBytes);
        long fileSize = Files.size(path);
        if (fileSize > maxPayloadBytes + MAX_ENVELOPE_OVERHEAD) {
            throw invalid("IDE report input exceeds the configured payload limit");
        }
        byte[] input = Files.readAllBytes(path);
        if (input.length > maxPayloadBytes + MAX_ENVELOPE_OVERHEAD) {
            throw invalid("IDE report input exceeds the configured payload limit");
        }
        if (startsWithMagic(input)) {
            DecodedEnvelope envelope = decodeEnvelope(input, maxPayloadBytes);
            return new LoadedReport(SourceFormat.SPLIT, envelope.producerVersion(), envelope.compressionName(),
                            parsePayload(envelope.payload()));
        }
        if (input.length > maxPayloadBytes) {
            throw invalid("Canonical IDE report payload exceeds the configured payload limit");
        }
        return new LoadedReport(SourceFormat.JSON, null, "none", parsePayload(input));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        final Options options;
        try {
            options = Options.parse(args);
        } catch (UsageException exception) {
            err.println(exception.getMessage());
            err.println(usage());
            return 2;
        }
        if (options.help) {
            out.println(usage());
            return 0;
        }

        try {
            LoadedReport loaded = read(options.path, options.maxPayloadBytes);
            Predicate<ObjectNode> filter = record -> matches(record, "kind", options.kind) &&
                            matches(record, "category", options.category);
            List<ObjectNode> matching = loaded.report().records().stream().filter(filter).toList();
            if (options.list) {
                ArrayNode array = JSON.createArrayNode();
                matching.forEach(array::add);
                out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(array));
            } else {
                printSummary(loaded, matching, out);
            }
            return 0;
        } catch (Exception exception) {
            err.println("IDE report read failed: " + exception.getMessage());
            return 1;
        }
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    private static void printSummary(LoadedReport loaded, List<ObjectNode> records, PrintStream out) {
        Report report = loaded.report();
        out.println("source_format: " + loaded.sourceFormat().name().toLowerCase());
        if (loaded.producerVersion() != null) {
            out.println("producer_version: " + loaded.producerVersion());
        }
        out.println("compression: " + loaded.compression());
        out.println("schema_version: " + report.schemaVersion());
        out.println("payload_scope: " + report.payloadScope());
        out.println("records: " + report.records().size());
        if (records.size() != report.records().size()) {
            out.println("matching_records: " + records.size());
        }
        out.println("used_methods: " + report.usedMethods().size());
        printCounts("records_by_kind", records, "kind", out);
        printCounts("records_by_category", records, "category", out);
    }

    private static void printCounts(String heading, List<ObjectNode> records, String field, PrintStream out) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ObjectNode record : records) {
            JsonNode value = record.get(field);
            String key = value != null && value.isTextual() ? value.textValue() : "(missing)";
            counts.merge(key, 1, Integer::sum);
        }
        out.println(heading + ":");
        counts.forEach((key, count) -> out.println("  " + key + ": " + count));
    }

    private static boolean matches(ObjectNode record, String field, String expected) {
        return expected == null || expected.equals(record.path(field).asText(null));
    }

    private static List<ObjectNode> requiredObjectArray(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw invalid("Canonical IDE report field '" + field + "' must be an array");
        }
        List<ObjectNode> result = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (!(element instanceof ObjectNode object)) {
                throw invalid("Canonical IDE report field '" + field + "' must contain only objects");
            }
            result.add(object);
        }
        return List.copyOf(result);
    }

    private static int requiredInteger(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid("Canonical IDE report field '" + field + "' must be an integer");
        }
        return value.intValue();
    }

    private static String requiredText(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid("Canonical IDE report field '" + field + "' must be a string");
        }
        return value.textValue();
    }

    private static byte[] decompress(byte[] stored, long expectedSize) {
        try (InputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(stored))) {
            byte[] payload = new byte[(int) expectedSize];
            int offset = 0;
            while (offset < payload.length) {
                int count = input.read(payload, offset, payload.length - offset);
                if (count < 0) {
                    throw invalid("IDE report envelope uncompressed size does not match the header");
                }
                offset += count;
            }
            if (input.read() != -1) {
                throw invalid("Compressed IDE report payload exceeds its declared or configured size");
            }
            return payload;
        } catch (IOException exception) {
            throw invalid("Invalid compressed IDE report payload", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes, String description) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException exception) {
            throw invalid("Invalid UTF-8 in IDE report " + description, exception);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static boolean startsWithMagic(byte[] bytes) {
        if (bytes.length < MAGIC.length) {
            return false;
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (bytes[index] != MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static void requireRemaining(ByteBuffer buffer, int count) {
        if (count < 0 || buffer.remaining() < count) {
            throw invalid("Truncated IDE report envelope");
        }
    }

    private static void validateLimit(long maxPayloadBytes) {
        if (maxPayloadBytes <= 0 || maxPayloadBytes > MAX_CONFIGURABLE_PAYLOAD_BYTES) {
            throw invalid("Payload limit must be between 1 and " + MAX_CONFIGURABLE_PAYLOAD_BYTES + " bytes");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static String usage() {
        return """
                        Usage: jbang IdeReportReader.java [options] <report.json|image.ide-report>

                        Options:
                          --list                     Print matching records as a JSON array
                          --kind <kind>              Filter records by exact kind
                          --category <category>      Filter records by exact category
                          --max-payload-bytes <n>    Override the 512 MiB decoded-payload limit
                          -h, --help                 Show this help
                        """.stripTrailing();
    }

    private record Options(Path path, boolean list, String kind, String category, long maxPayloadBytes,
                    boolean help) {
        static Options parse(String[] args) {
            Path path = null;
            boolean list = false;
            String kind = null;
            String category = null;
            long maxPayloadBytes = DEFAULT_MAX_PAYLOAD_BYTES;
            boolean help = false;
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                switch (argument) {
                    case "--list" -> list = true;
                    case "--kind" -> kind = requireValue(args, ++index, argument);
                    case "--category" -> category = requireValue(args, ++index, argument);
                    case "--max-payload-bytes" -> {
                        String value = requireValue(args, ++index, argument);
                        try {
                            maxPayloadBytes = Long.parseLong(value);
                        } catch (NumberFormatException exception) {
                            throw new UsageException("Invalid byte limit: " + value);
                        }
                    }
                    case "-h", "--help" -> help = true;
                    default -> {
                        if (argument.startsWith("-")) {
                            throw new UsageException("Unknown option: " + argument);
                        }
                        if (path != null) {
                            throw new UsageException("Only one report path may be specified");
                        }
                        path = Path.of(argument);
                    }
                }
            }
            if (!help && path == null) {
                throw new UsageException("Missing report path");
            }
            if (maxPayloadBytes <= 0 || maxPayloadBytes > MAX_CONFIGURABLE_PAYLOAD_BYTES) {
                throw new UsageException("Payload limit must be between 1 and " + MAX_CONFIGURABLE_PAYLOAD_BYTES + " bytes");
            }
            return new Options(path, list, kind, category, maxPayloadBytes, help);
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new UsageException("Missing value for " + option);
            }
            return args[index];
        }
    }

    private static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
