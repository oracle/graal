/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.TracingSourceCacheListener.MAX_SOURCE_NAME_LENGTH;
import static com.oracle.truffle.polyglot.TracingSourceCacheListener.truncateString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;

final class SourceCacheStatisticsListener implements SourceCacheListener {
    private static final int MAX_ENTRIES_PER_CATEGORY = 3;
    private static final int LEFT_COLUMN_WIDTH = 47;

    private final boolean showAllDetails;
    private final Map<CacheCounterKey, SourceCacheCounters> cacheCounters = new ConcurrentHashMap<>();

    private SourceCacheStatisticsListener(boolean sourceCacheStatisticDetails) {
        this.showAllDetails = sourceCacheStatisticDetails;
    }

    static SourceCacheStatisticsListener createOrNull(PolyglotEngineImpl engine) {
        boolean sourceCacheStatistics = engine.engineOptionValues.get(PolyglotEngineOptions.SourceCacheStatistics);
        boolean sourceCacheStatisticDetails = engine.engineOptionValues.get(PolyglotEngineOptions.SourceCacheStatisticDetails);
        return sourceCacheStatistics || sourceCacheStatisticDetails ? new SourceCacheStatisticsListener(sourceCacheStatisticDetails) : null;
    }

    private void finalizeAllCounters(SourceCacheCounters counters) {
        assert Thread.holdsLock(this);
        counters.finalizeCounters();
        if (counters.nestedCounters != null) {
            for (SourceCacheCounters nestedCounters : counters.nestedCounters.values()) {
                finalizeAllCounters(nestedCounters);
            }
        }
    }

    synchronized void onEngineClose(PolyglotEngineImpl engine) {
        for (SourceCacheCounters cacheCounter : cacheCounters.values()) {
            finalizeAllCounters(cacheCounter);
        }
        StringBuilder logBuilder = new StringBuilder("Polyglot source cache statistics for engine " + engine.engineId);
        logBuilder.append(System.lineSeparator());
        for (SourceCacheCounters cacheCounter : cacheCounters.values()) {
            logBuilder.append("--- SHARING LAYER ").append(cacheCounter.sharingLayerId).append("; ").append(cacheCounter.cacheType.name()).append(" CACHE -------------------------").append(
                            System.lineSeparator());
            int indent = 4;
            logBuilder.append(" ".repeat(indent));
            logBuilder.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent) + "s", "Languages")).append(":").append(System.lineSeparator());
            /*
             * Sort the languages by source cache event count in descending order and then print
             * statistics for each of the languages in the computed order.
             */
            List<String> keys = new ArrayList<>(cacheCounter.nestedCounters.keySet());
            keys.sort((s1, s2) -> {
                SourceCacheCounters counter1 = cacheCounter.nestedCounters.get(s1);
                SourceCacheCounters counter2 = cacheCounter.nestedCounters.get(s2);
                int signum = Long.signum(counter2.eventCount.get() - counter1.eventCount.get());
                if (signum != 0) {
                    return signum;
                } else {
                    return counter1.sortString.compareTo(counter2.sortString);
                }
            });
            for (String key : keys) {
                SourceCacheCounters languageCounter = cacheCounter.nestedCounters.get(key);
                logBuilder.append(" ".repeat(indent + 4));
                logBuilder.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", languageCounter.key)).append(":").append(System.lineSeparator());
                /*
                 * First print statistics for character based sources of the language.
                 */
                if (languageCounter.missParseTimeCharacters.getCount() + languageCounter.failureParseTimeCharacters.getCount() > 0) {
                    logBuilder.append(" ".repeat(indent + 8));
                    logBuilder.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", "Character Based Sources Stats")).append(":").append(System.lineSeparator());
                    printSourcesStatistics(indent + 12, logBuilder, languageCounter, true);
                    printCacheStatistics(indent + 12, logBuilder, languageCounter, true);
                }
                /*
                 * Second print statistics for byte based sources of the language.
                 */
                if (languageCounter.missParseTimeBytes.getCount() + languageCounter.failureParseTimeBytes.getCount() > 0) {
                    logBuilder.append(" ".repeat(indent + 8));
                    logBuilder.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", "Byte Based Sources Stats")).append(":").append(System.lineSeparator());
                    printSourcesStatistics(indent + 12, logBuilder, languageCounter, false);
                    printCacheStatistics(indent + 12, logBuilder, languageCounter, false);
                }
            }
            engine.getEngineLogger().log(Level.INFO, logBuilder.toString());
        }
    }

    private SourceCacheCounters getCacheCounter(long sharingLayerId, CacheType cacheType) {
        return cacheCounters.computeIfAbsent(new CacheCounterKey(sharingLayerId, cacheType),
                        layerAndType -> new SourceCacheCounters(layerAndType.sharinglayerId, layerAndType.cacheType, false, null, null, null));
    }

    /**
     * Appends statistics for individual sources. The source keys are filtered and sorted and then
     * the statistics for at most maxEntries are appended.
     * 
     * @param sb string builder to append to.
     * @param m mapping of source keys to individual source statistics.
     * @param maxEntries maximum source entries to append.
     * @param filter source filter.
     * @param comparator source comparator.
     * @param appender BiConsumer that does the appending using the specified string builder and
     *            individual source statistics.
     * @return the number of applicable entries not limited by maxEntries in order for the caller to
     *         be able to determine whether the list was truncated.
     */
    private static int appendSourceEntries(StringBuilder sb, Map<String, SourceCacheCounters> m, int maxEntries, Predicate<String> filter, Comparator<String> comparator,
                    BiConsumer<StringBuilder, SourceCacheCounters> appender) {
        List<String> keys = new ArrayList<>(m.keySet().stream().filter(filter).toList());
        keys.sort(comparator);
        for (String key : keys.subList(0, Math.min(maxEntries, keys.size()))) {
            appender.accept(sb, m.get(key));
        }
        return keys.size();
    }

    /**
     * Print cache statistics for sources of a certain language observed by a particular cache
     * instance.
     *
     * @param indent base indent for printing.
     * @param sb string builder to append to.
     * @param languageCounter data for caching of sources of a certain language.
     * @param characterBased if <code>true</code>, statistics for character based sources are
     *            printed, otherwise statistics for byte based sources are printed.
     */
    private void printCacheStatistics(int indent, StringBuilder sb, SourceCacheCounters languageCounter, boolean characterBased) {
        char sizeUnit = characterBased ? 'C' : 'B';
        LongStatistics missParseSize = characterBased ? languageCounter.missParseSizeCharacters : languageCounter.missParseSizeBytes;
        LongStatistics missParseTime = characterBased ? languageCounter.missParseTimeCharacters : languageCounter.missParseTimeBytes;
        LongStatistics failureParseTime = characterBased ? languageCounter.failureParseTimeCharacters : languageCounter.failureParseTimeBytes;
        if (missParseTime.getCount() + failureParseTime.getCount() > 0) {
            AtomicLong hitCount = characterBased ? languageCounter.hitCountCharacters : languageCounter.hitCountBytes;
            AtomicLong missCount = characterBased ? languageCounter.missCountCharacters : languageCounter.missCountBytes;
            AtomicLong evictionCount = characterBased ? languageCounter.evictionCountCharacters : languageCounter.evictionCountBytes;
            /*
             * Print overall cache stats.
             */
            sb.append(" ".repeat(indent));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent) + "s", "Cache")).append(": ").append(
                            String.format("parse time(ms)=%6d, parse rate(%c/s)=%12.2f, hits=%7d, misses=%6d, evictions=%5d, failures=%5d, hit rate=%2d%%",
                                            missParseTime.getSum(),
                                            sizeUnit,
                                            1000.0d * missParseSize.getSum() / missParseTime.getSum(),
                                            hitCount.get(),
                                            missCount.get(),
                                            evictionCount.get(),
                                            failureParseTime.getCount(),
                                            100 * hitCount.get() / (hitCount.get() + missCount.get())));
            sb.append(System.lineSeparator());
            /*
             * Print parsing statistics.
             */
            sb.append(" ".repeat(indent + 4));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", "Parse Successful")).append(": ").append(
                            String.format("count=%15d",
                                            missParseTime.getCount()));
            sb.append(System.lineSeparator());
            /*
             * Parsing time statistics.
             */
            sb.append(" ".repeat(indent + 8));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", "Time (ms)")).append(": ").append(
                            String.format("count=%15d, sum=%24d, min=%8d, avg=%9.2f, max=%11d, maxSource=%s",
                                            missParseTime.getCount(),
                                            missParseTime.getSum(),
                                            missParseTime.getMin(),
                                            missParseTime.getAverage(),
                                            missParseTime.getMax(),
                                            missParseTime.maxId));
            sb.append(System.lineSeparator());
            /*
             * Parsed size statistics.
             */
            sb.append(" ".repeat(indent + 8));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", String.format("Size (%c)", sizeUnit))).append(": ").append(
                            String.format("count=%15d, sum=%24d, min=%8d, avg=%9.2f, max=%11d, maxSource=%s",
                                            missParseSize.getCount(),
                                            missParseSize.getSum(),
                                            missParseSize.getMin(),
                                            missParseSize.getAverage(),
                                            missParseSize.getMax(),
                                            missParseSize.maxId));
            sb.append(System.lineSeparator());
            sb.append(" ".repeat(indent + 4));
            /*
             * Print cache statistics for individual sources sorted by hit count in descending
             * order.
             */
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", "Sources With Most Hits")).append(":").append(System.lineSeparator());
            BiConsumer<StringBuilder, SourceCacheCounters> appendSingleSourceStats = (b, c) -> {
                LongStatistics sourceMissParseTime = characterBased ? c.missParseTimeCharacters : c.missParseTimeBytes;
                LongStatistics sourceMissParseSize = characterBased ? c.missParseSizeCharacters : c.missParseSizeBytes;
                LongStatistics sourceFailureTimeStats = characterBased ? c.failureParseTimeCharacters : c.failureParseTimeBytes;
                AtomicLong sourceHitCount = characterBased ? c.hitCountCharacters : c.hitCountBytes;
                AtomicLong sourceMissCount = characterBased ? c.missCountCharacters : c.missCountBytes;
                AtomicLong sourceEvictionCount = characterBased ? c.evictionCountCharacters : c.evictionCountBytes;
                b.append(" ".repeat(indent + 8));
                b.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", c.keyFixedLengthPrefix)).append(": ").append(
                                String.format("parse time(ms)=%6d, parse rate(%c/s)=%12.2f, hits=%7d, misses=%6d, evictions=%5d, failures=%5d, hit rate=%2d%%, name=%s",
                                                sourceMissParseTime.getSum(),
                                                sizeUnit,
                                                1000.0d * sourceMissParseSize.getSum() / sourceMissParseTime.getSum(),
                                                sourceHitCount.get(),
                                                sourceMissCount.get(),
                                                sourceEvictionCount.get(),
                                                sourceFailureTimeStats.getCount(),
                                                100 * sourceHitCount.get() / (sourceHitCount.get() + sourceMissCount.get()),
                                                c.keyVariableLengthSuffix));
                b.append(System.lineSeparator());
            };
            int totalEntries = appendSourceEntries(sb, languageCounter.nestedCounters, getMaxEntriesPerCategory(), getSourcesFilter(languageCounter, characterBased), (s1, s2) -> {
                SourceCacheCounters counter1 = languageCounter.nestedCounters.get(s1);
                AtomicLong sourceHitCount1 = characterBased ? counter1.hitCountCharacters : counter1.hitCountBytes;
                SourceCacheCounters counter2 = languageCounter.nestedCounters.get(s2);
                AtomicLong sourceHitCount2 = characterBased ? counter2.hitCountCharacters : counter2.hitCountBytes;
                int signum = Long.signum(sourceHitCount2.get() - sourceHitCount1.get());
                if (signum != 0) {
                    return signum;
                } else {
                    return counter1.sortString.compareTo(counter2.sortString);
                }
            }, appendSingleSourceStats);
            if (totalEntries > getMaxEntriesPerCategory()) {
                sb.append(" ".repeat(indent + 8)).append("...").append(System.lineSeparator());
            }
            /*
             * Print cache statistics for individual sources sorted by miss count in descending
             * order.
             */
            sb.append(" ".repeat(indent + 4));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", "Sources With Most Misses")).append(":").append(System.lineSeparator());
            totalEntries = appendSourceEntries(sb, languageCounter.nestedCounters, getMaxEntriesPerCategory(), getSourcesFilter(languageCounter, characterBased), (s1, s2) -> {
                SourceCacheCounters counter1 = languageCounter.nestedCounters.get(s1);
                AtomicLong sourceMissCount1 = characterBased ? counter1.missCountCharacters : counter1.missCountBytes;
                SourceCacheCounters counter2 = languageCounter.nestedCounters.get(s2);
                AtomicLong sourceMissCount2 = characterBased ? counter2.missCountCharacters : counter2.missCountBytes;
                int signum = Long.signum(sourceMissCount2.get() - sourceMissCount1.get());
                if (signum != 0) {
                    return signum;
                } else {
                    return counter1.sortString.compareTo(counter2.sortString);
                }
            }, appendSingleSourceStats);
            if (totalEntries > getMaxEntriesPerCategory()) {
                sb.append(" ".repeat(indent + 8)).append("...").append(System.lineSeparator());
            }
            /*
             * Print cache statistics for individual sources sorted by eviction count in descending
             * order.
             */
            sb.append(" ".repeat(indent + 4));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", "Sources With Most Evictions")).append(":").append(System.lineSeparator());
            totalEntries = appendSourceEntries(sb, languageCounter.nestedCounters, getMaxEntriesPerCategory(), getSourcesFilter(languageCounter, characterBased), (s1, s2) -> {
                SourceCacheCounters counter1 = languageCounter.nestedCounters.get(s1);
                AtomicLong sourceEvictionCount1 = characterBased ? counter1.evictionCountCharacters : counter1.evictionCountBytes;
                SourceCacheCounters counter2 = languageCounter.nestedCounters.get(s2);
                AtomicLong sourceEvictionCount2 = characterBased ? counter2.evictionCountCharacters : counter2.evictionCountBytes;
                int signum = Long.signum(sourceEvictionCount2.get() - sourceEvictionCount1.get());
                if (signum != 0) {
                    return signum;
                } else {
                    return counter1.sortString.compareTo(counter2.sortString);
                }
            }, appendSingleSourceStats);
            if (totalEntries > getMaxEntriesPerCategory()) {
                sb.append(" ".repeat(indent + 8)).append("...").append(System.lineSeparator());
            }
            /*
             * Print number of parsing errors.
             */
            sb.append(" ".repeat(indent + 4));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", "Failures")).append(": ").append(
                            String.format("count=%15d",
                                            failureParseTime.getCount()));
            sb.append(System.lineSeparator());
            if (failureParseTime.getCount() > 0) {
                /*
                 * Print the count of unique sources with parsing errors if there is any.
                 */
                sb.append(" ".repeat(indent + 8));
                sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", "Sources With Failures")).append(": ").append(
                                String.format("count=%15d",
                                                languageCounter.nestedCounters.entrySet().stream().filter(nestedEntry -> !nestedEntry.getValue().failures.isEmpty() &&
                                                                ((characterBased && nestedEntry.getValue().failureParseTimeCharacters.getCount() > 0) ||
                                                                                (!characterBased && nestedEntry.getValue().failureParseTimeBytes.getCount() > 0))).count()));
                sb.append(System.lineSeparator());
            }
            /*
             * Print parsing errors for all individual sources with parsing errors.
             */
            appendSourceEntries(sb, languageCounter.nestedCounters, Integer.MAX_VALUE, getSourcesFilter(languageCounter, characterBased), (s1, s2) -> {
                SourceCacheCounters counter1 = languageCounter.nestedCounters.get(s1);
                LongStatistics failureTimeStats1 = characterBased ? counter1.failureParseTimeCharacters : counter1.failureParseTimeBytes;
                SourceCacheCounters counter2 = languageCounter.nestedCounters.get(s2);
                LongStatistics failureTimeStats2 = characterBased ? counter2.failureParseTimeCharacters : counter2.failureParseTimeBytes;
                int signum = Long.signum(failureTimeStats2.getCount() - failureTimeStats1.getCount());
                if (signum != 0) {
                    return signum;
                } else {
                    return counter1.sortString.compareTo(counter2.sortString);
                }
            }, (b, c) -> {
                LongStatistics sourceFailureParseTime = characterBased ? c.failureParseTimeCharacters : c.failureParseTimeBytes;
                LongStatistics sourceFailureParseSize = characterBased ? c.failureParseSizeCharacters : c.failureParseSizeBytes;
                AtomicLong sourceHitCount = characterBased ? c.hitCountCharacters : c.hitCountBytes;
                AtomicLong sourceMissCount = characterBased ? c.missCountCharacters : c.missCountBytes;
                AtomicLong sourceEvictionCount = characterBased ? c.evictionCountCharacters : c.evictionCountBytes;
                if (!c.failures.isEmpty()) {
                    b.append(" ".repeat(indent + 12));
                    b.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 12) + "s", c.keyFixedLengthPrefix)).append(": ").append(
                                    String.format("parse time(ms)=%6d, parse rate(%c/s)=%12.2f, hits=%7d, misses=%6d, evictions=%5d, failures=%5d, hit rate=%2d%%, name=%s",
                                                    sourceFailureParseTime.getSum(),
                                                    sizeUnit,
                                                    1000.0d * sourceFailureParseSize.getSum() / sourceFailureParseTime.getSum(),
                                                    sourceHitCount.get(),
                                                    sourceMissCount.get(),
                                                    sourceEvictionCount.get(),
                                                    sourceFailureParseTime.getCount(),
                                                    100 * sourceHitCount.get() / (sourceHitCount.get() + sourceMissCount.get()),
                                                    c.keyVariableLengthSuffix));
                    b.append(System.lineSeparator());
                    int index = 0;
                    for (Map.Entry<String, Long> failureEntry : c.failures.entrySet()) {
                        index++;
                        b.append(" ".repeat(indent + 16));
                        b.append(String.format("Failure #%-" + (LEFT_COLUMN_WIDTH - indent - 16 - "Failure #".length()) + "d", index)).append(": ").append(
                                        String.format("count=%15d",
                                                        failureEntry.getValue()));
                        b.append(", failure=").append(failureEntry.getKey());
                        b.append(System.lineSeparator());
                    }
                }
            });
        }
    }

    /**
     * Print count and size information about sources of a certain language observed by a particular
     * cache instance.
     *
     * @param indent base indent for printing.
     * @param sb string builder to append to.
     * @param languageCounter data for sources of a certain language.
     * @param characterBased if <code>true</code>, statistics for character based sources are
     *            printed, otherwise statistics for byte based sources are printed.
     */
    private void printSourcesStatistics(int indent, StringBuilder sb, SourceCacheCounters languageCounter, boolean characterBased) {
        char sizeUnit = characterBased ? 'C' : 'B';
        LongStatistics sizeStats = characterBased ? languageCounter.sourceSizeCharacters : languageCounter.sourceSizeBytes;
        if (sizeStats.getCount() > 0) {
            sb.append(" ".repeat(indent));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent) + "s", "Sources")).append(": ").append(
                            String.format("count=%15d", sizeStats.getCount()));
            sb.append(System.lineSeparator());
            sb.append(" ".repeat(indent + 4));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", String.format("Size (%c)", sizeUnit))).append(": ").append(
                            String.format("count=%15d, sum=%24d, min=%8d, avg=%9.2f, max=%11d, maxSource=%s",
                                            sizeStats.getCount(),
                                            sizeStats.getSum(),
                                            sizeStats.getMin(),
                                            sizeStats.getAverage(),
                                            sizeStats.getMax(),
                                            sizeStats.maxId));
            sb.append(System.lineSeparator());
            sb.append(" ".repeat(indent + 4));
            sb.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 4) + "s", "Biggest Sources")).append(":").append(System.lineSeparator());
            int totalEntries = appendSourceEntries(sb, languageCounter.nestedCounters, getMaxEntriesPerCategory(), getSourcesFilter(languageCounter, characterBased), (s1, s2) -> {
                SourceCacheCounters counter1 = languageCounter.nestedCounters.get(s1);
                SourceCacheCounters counter2 = languageCounter.nestedCounters.get(s2);
                long size1 = characterBased ? counter1.sourceSizeCharacters.getSum() : counter1.sourceSizeBytes.getSum();
                long size2 = characterBased ? counter2.sourceSizeCharacters.getSum() : counter2.sourceSizeBytes.getSum();
                int signum = Long.signum(size2 - size1);
                if (signum != 0) {
                    return signum;
                } else {
                    return counter1.sortString.compareTo(counter2.sortString);
                }
            }, (b, c) -> {
                long size = characterBased ? c.sourceSizeCharacters.getSum() : c.sourceSizeBytes.getSum();
                b.append(" ".repeat(indent + 8));
                b.append(String.format("%-" + (LEFT_COLUMN_WIDTH - indent - 8) + "s", c.keyFixedLengthPrefix)).append(": ").append(String.format("size=%16d", size));
                b.append(", name=").append(c.keyVariableLengthSuffix);
                b.append(System.lineSeparator());
            });
            if (totalEntries > getMaxEntriesPerCategory()) {
                sb.append(" ".repeat(indent + 8)).append("...").append(System.lineSeparator());
            }
        }
    }

    private static void updateCounters(SourceCacheCounters counter, Source source, CacheEventType eventType, long parseTime, String failure) {
        counter.update(source, eventType, parseTime, failure);
        SourceCacheCounters languageCounter = counter.nestedCounters.merge(source.getLanguage(),
                        new SourceCacheCounters(counter.sharingLayerId, counter.cacheType, false, "", source.getLanguage(), source.getLanguage()),
                        (oldValue, newValue) -> oldValue);
        languageCounter.update(source, eventType, parseTime, failure);
        SourceCacheCounters sourceCounter = languageCounter.nestedCounters.merge(getSourceKey(source),
                        new SourceCacheCounters(counter.sharingLayerId, counter.cacheType, true, getSourceHash(source), getTruncatedSourceName(source), source.getName()),
                        (oldValue, newValue) -> oldValue);
        sourceCounter.update(source, eventType, parseTime, failure);
    }

    @Override
    public void onCacheHit(Source source, CallTarget target, CacheType cacheType, long hits) {
        PolyglotSharingLayer sharingLayer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(((RootCallTarget) target).getRootNode());
        SourceCacheCounters sourceCacheCounters = getCacheCounter(sharingLayer.shared.id, cacheType);
        updateCounters(sourceCacheCounters, source, CacheEventType.HIT, -1, null);
    }

    @Override
    public void onCacheMiss(Source source, CallTarget target, CacheType cacheType, long startTime) {
        PolyglotSharingLayer sharingLayer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(((RootCallTarget) target).getRootNode());
        SourceCacheCounters sourceCacheCounters = getCacheCounter(sharingLayer.shared.id, cacheType);
        updateCounters(sourceCacheCounters, source, CacheEventType.MISS, System.currentTimeMillis() - startTime, null);
    }

    @Override
    public void onCacheFail(PolyglotSharingLayer sharingLayer, Source source, CacheType cacheType, long startTime, Throwable throwable) {
        String failureKey = truncateString(throwable.toString(), TracingSourceCacheListener.MAX_EXCEPTION_STRING_LENGTH);
        SourceCacheCounters sourceCacheCounters = getCacheCounter(sharingLayer.shared.id, cacheType);
        updateCounters(sourceCacheCounters, source, CacheEventType.FAIL, System.currentTimeMillis() - startTime, failureKey);
    }

    @Override
    public void onCacheEvict(Source source, CallTarget target, CacheType cacheType, long hits) {
        PolyglotSharingLayer sharingLayer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(((RootCallTarget) target).getRootNode());
        SourceCacheCounters sourceCacheCounters = getCacheCounter(sharingLayer.shared.id, cacheType);
        updateCounters(sourceCacheCounters, source, CacheEventType.EVICT, -1, null);
    }

    private int getMaxEntriesPerCategory() {
        return showAllDetails ? Integer.MAX_VALUE : MAX_ENTRIES_PER_CATEGORY;
    }

    private static Predicate<String> getSourcesFilter(SourceCacheCounters languageCounter, boolean characterBased) {
        return s -> {
            SourceCacheCounters c = languageCounter.nestedCounters.get(s);
            return (characterBased && c.sourceSizeCharacters.getCount() > 0) || (!characterBased && c.sourceSizeBytes.getCount() > 0);
        };
    }

    private static String getSourceKey(Source source) {
        return getSourceHash(source) + " " + getTruncatedSourceName(source);
    }

    private static String getSourceHash(Source source) {
        return String.format("0x%08x", source.hashCode());
    }

    private static String getTruncatedSourceName(Source source) {
        return truncateString(source.getName(), MAX_SOURCE_NAME_LENGTH);
    }

    private record CacheCounterKey(long sharinglayerId, CacheType cacheType) {
    }

    private static final class LongStatistics extends LongSummaryStatistics {
        private String maxId;

        @Override
        public void accept(int value) {
            throw new UnsupportedOperationException();
        }

        public void accept(long value, String id) {
            if (value > getMax()) {
                this.maxId = id;
            }
            super.accept(value);
        }

        @Override
        public void combine(LongSummaryStatistics other) {
            throw new UnsupportedOperationException();
        }
    }

    private enum CacheEventType {
        HIT,
        MISS,
        EVICT,
        FAIL
    }

    private static final class SourceCacheCounters {
        private final long sharingLayerId;
        private final CacheType cacheType;
        private final String key;
        private final String keyFixedLengthPrefix;
        private final String keyVariableLengthSuffix;
        private final String sortString;
        private final Set<String> sources = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final LongStatistics sourceSizeBytes = new LongStatistics();
        private final LongStatistics sourceSizeCharacters = new LongStatistics();
        private final AtomicLong eventCount = new AtomicLong();
        private final AtomicLong hitCountBytes = new AtomicLong();
        private final AtomicLong hitCountCharacters = new AtomicLong();
        private final AtomicLong missCountBytes = new AtomicLong();
        private final AtomicLong missCountCharacters = new AtomicLong();
        private final LongStatistics missParseTimeBytes = new LongStatistics();
        private final LongStatistics missParseTimeCharacters = new LongStatistics();
        private final LongStatistics missParseSizeBytes = new LongStatistics();
        private final LongStatistics missParseSizeCharacters = new LongStatistics();
        private final AtomicLong evictionCountBytes = new AtomicLong();
        private final AtomicLong evictionCountCharacters = new AtomicLong();
        private final LongStatistics failureParseTimeBytes = new LongStatistics();
        private final LongStatistics failureParseTimeCharacters = new LongStatistics();
        private final LongStatistics failureParseSizeBytes = new LongStatistics();
        private final LongStatistics failureParseSizeCharacters = new LongStatistics();
        private final Map<String, Long> failures = new ConcurrentHashMap<>();
        private final Map<String, SourceCacheCounters> nestedCounters;
        private boolean finalized;

        private SourceCacheCounters(long sharingLayerId, CacheType cacheType, boolean leaf, String keyFixed, String keyVariable, String sortString) {
            this.sharingLayerId = sharingLayerId;
            this.cacheType = cacheType;
            if (!leaf) {
                this.nestedCounters = new ConcurrentHashMap<>();
            } else {
                this.nestedCounters = null;
            }
            this.keyFixedLengthPrefix = keyFixed;
            this.keyVariableLengthSuffix = keyVariable;
            if (keyFixed != null && keyVariable != null) {
                this.key = keyFixed + " " + keyVariable;
            } else if (keyFixed != null) {
                this.key = keyFixed;
            } else {
                this.key = keyVariable;
            }
            this.sortString = sortString;
        }

        private void updateSourceSizeStatistics(Source source, String sourceKey, LongStatistics statsCharacters, LongStatistics statsBytes) {
            assert Thread.holdsLock(this);
            if (source.hasCharacters()) {
                statsCharacters.accept(source.getCharacters().length(), sourceKey);
            } else if (source.hasBytes()) {
                statsBytes.accept(source.getBytes().length(), sourceKey);
            } else {
                statsCharacters.accept(0, sourceKey);
            }
        }

        private void updateSourceTimeStatistics(Source source, String sourceKey, LongStatistics statsCharacters, LongStatistics statsBytes, long parseTime) {
            assert Thread.holdsLock(this);
            if (source.hasCharacters()) {
                statsCharacters.accept(parseTime, sourceKey);
            } else {
                statsBytes.accept(parseTime, sourceKey);
            }
        }

        private void incrementCount(Source source, AtomicLong counterCharacters, AtomicLong counterBytes) {
            assert Thread.holdsLock(this);
            if (source.hasCharacters()) {
                counterCharacters.incrementAndGet();
            } else {
                counterBytes.incrementAndGet();
            }
        }

        private synchronized void update(Source source, CacheEventType eventType, long parseTime, String failure) {
            if (finalized) {
                return;
            }
            String sourceKey = getSourceKey(source);
            if (sources.add(sourceKey)) {
                updateSourceSizeStatistics(source, sourceKey, sourceSizeCharacters, sourceSizeBytes);
            }
            switch (eventType) {
                case HIT:
                    incrementCount(source, hitCountCharacters, hitCountBytes);
                    break;
                case MISS:
                    incrementCount(source, missCountCharacters, missCountBytes);
                    updateSourceSizeStatistics(source, sourceKey, missParseSizeCharacters, missParseSizeBytes);
                    updateSourceTimeStatistics(source, sourceKey, missParseTimeCharacters, missParseTimeBytes, parseTime);
                    break;
                case EVICT:
                    incrementCount(source, evictionCountCharacters, evictionCountBytes);
                    break;
                case FAIL:
                    incrementCount(source, missCountCharacters, missCountBytes);
                    updateSourceSizeStatistics(source, sourceKey, failureParseSizeCharacters, failureParseSizeBytes);
                    updateSourceTimeStatistics(source, sourceKey, failureParseTimeCharacters, failureParseTimeBytes, parseTime);
                    this.failures.merge(failure, 1L, Long::sum);
                    break;
            }
            eventCount.incrementAndGet();
        }

        private synchronized void finalizeCounters() {
            finalized = true;
        }
    }
}
