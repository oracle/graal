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

package com.oracle.graal.pointsto.standalone.test.classes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.HexFormat;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

/**
 * Compact explicit-entry fixture for standalone reachability profiling.
 *
 * The methods intentionally exercise only {@code java.base} APIs so the test project keeps the same
 * module footprint while still driving analysis through representative JDK subsystems.
 */
public final class SmallReachabilityCase {
    private SmallReachabilityCase() {
    }

    public static String collectionsProfile() {
        return helperCollections() + helperMapsAndSets() + helperOptionalAndStats() + helperDequeAndBits() + helperTreeAlgorithms() + helperCollectors() + helperOptionalInt();
    }

    public static String concurrencyProfile() {
        return helperConcurrent() + helperProperties();
    }

    public static String encodingProfile() {
        return helperBase64AndCharset() + helperBufferAndHex() + helperChecksums();
    }

    public static String formattingProfile() {
        return helperFormatting() + helperText();
    }

    public static String mathProfile() {
        return helperMath();
    }

    public static String platformProfile() {
        return helperUrisAndUuid() + helperLocaleAndCurrency() + helperComparableRecords();
    }

    public static String regexProfile() {
        return helperRegex() + helperPatternsAndNormalization();
    }

    public static String timeProfile() {
        return helperTime() + helperAdvancedTime();
    }

    private static String helperCollections() {
        List<String> values = new ArrayList<>();
        values.add("gamma");
        values.add("beta");
        values.add("alpha");
        values.sort(Comparator.naturalOrder());
        Collections.rotate(values, 1);
        Collections.reverse(values);
        return values.get(0) + values.contains("alpha");
    }

    private static String helperMapsAndSets() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("one", 1);
        counts.merge("two", 2, Integer::sum);
        counts.computeIfAbsent("three", String::length);

        TreeSet<String> sorted = new TreeSet<>(counts.keySet());
        sorted.floor("three");
        sorted.ceiling("one");
        return sorted.first() + counts.get("three");
    }

    private static String helperOptionalAndStats() {
        Optional<String> value = Optional.of("standalone").filter(v -> v.length() > 3).map(String::toUpperCase);
        IntSummaryStatistics stats = IntStream.rangeClosed(1, 5).summaryStatistics();
        return value.orElseThrow() + stats.getAverage();
    }

    private static String helperDequeAndBits() {
        ArrayDeque<String> deque = new ArrayDeque<>();
        deque.addFirst("first");
        deque.addLast("last");

        BitSet bits = new BitSet();
        bits.set(1);
        bits.flip(0, 3);
        return deque.removeFirst() + bits.cardinality();
    }

    private static String helperTreeAlgorithms() {
        TreeMap<String, Integer> map = new TreeMap<>();
        map.put("b", 2);
        map.put("a", 1);
        map.put("c", 3);
        return map.descendingMap().firstEntry().getKey() + map.subMap("a", true, "c", true).size();
    }

    private static String helperCollectors() {
        return List.of("a", "bb", "ccc").stream()
                        .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.joining("|")))
                        .values()
                        .stream()
                        .collect(Collectors.joining(";"));
    }

    private static String helperOptionalInt() {
        OptionalInt value = IntStream.of(4, 5, 6).filter(v -> v % 2 == 0).findFirst();
        return Integer.toString(value.orElseThrow());
    }

    private static String helperConcurrent() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("value", 1);
        AtomicInteger counter = new AtomicInteger();
        CompletableFuture<Integer> future = CompletableFuture.completedFuture(map.compute("value", (k, v) -> v + 1));
        return Integer.toString(counter.addAndGet(future.join()));
    }

    private static String helperProperties() {
        Properties properties = new Properties();
        properties.setProperty("k", "v");
        properties.putIfAbsent("x", "y");
        return properties.getProperty("k") + properties.get("x");
    }

    private static String helperBase64AndCharset() {
        byte[] encoded = Base64.getEncoder().encode("graal".getBytes(StandardCharsets.UTF_8));
        Charset charset = Charset.forName("UTF-8");
        return charset.decode(ByteBuffer.wrap(encoded)).toString();
    }

    private static String helperBufferAndHex() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(7);
        buffer.flip();
        return HexFormat.of().toHexDigits(buffer.getInt());
    }

    private static String helperChecksums() {
        CRC32 crc32 = new CRC32();
        crc32.update("standalone".getBytes(StandardCharsets.UTF_8));
        return Long.toString(crc32.getValue());
    }

    private static String helperFormatting() {
        DecimalFormat format = new DecimalFormat("#,##0.00");
        return MessageFormat.format("{0}-{1}", format.format(12345.678), 7);
    }

    private static String helperText() {
        StringJoiner joiner = new StringJoiner(":");
        joiner.add("x").add("y").add("z");
        StringCharacterIterator iterator = new StringCharacterIterator(joiner.toString());
        return String.valueOf(iterator.first()) + iterator.last();
    }

    private static String helperMath() {
        BigDecimal value = new BigDecimal("42.125").setScale(2, RoundingMode.HALF_UP);
        return value.movePointLeft(1).stripTrailingZeros().toPlainString();
    }

    private static String helperUrisAndUuid() {
        URI normalized = URI.create("https://example.com/a/../b?x=1").normalize();
        return normalized.getPath() + UUID.nameUUIDFromBytes(new byte[]{1, 2, 3});
    }

    private static String helperLocaleAndCurrency() {
        Locale locale = Locale.forLanguageTag("en-US");
        Currency currency = Currency.getInstance(locale);
        return locale.getDisplayLanguage(Locale.ROOT) + currency.getCurrencyCode();
    }

    private static String helperComparableRecords() {
        RecordValue left = new RecordValue("alpha", 1);
        RecordValue right = new RecordValue("beta", 2);
        return Comparator.comparing(RecordValue::name).thenComparingInt(RecordValue::count).compare(left, right) < 0 ? left.name() : right.name();
    }

    private static String helperRegex() {
        Pattern pattern = Pattern.compile("[a-z]+");
        MatchResult result = pattern.matcher("abc-123").results().findFirst().orElseThrow();
        return result.group() + pattern.splitAsStream("a,b,c").collect(Collectors.joining());
    }

    private static String helperPatternsAndNormalization() {
        return Normalizer.normalize("cafe\u0301", Normalizer.Form.NFC).replace("\u00e9", "e");
    }

    private static String helperTime() {
        LocalDate date = LocalDate.parse("2026-04-22", DateTimeFormatter.ISO_LOCAL_DATE);
        Period period = Period.between(date, date.plusDays(5));
        return date.plus(period).toString();
    }

    private static String helperAdvancedTime() {
        OffsetDateTime value = OffsetDateTime.of(LocalDateTime.of(2026, 4, 22, 10, 15), ZoneOffset.UTC);
        Duration duration = Duration.between(value, value.plusHours(3));
        return MonthDay.from(value).toString() + duration.toHours();
    }

    private record RecordValue(String name, int count) {
    }
}
