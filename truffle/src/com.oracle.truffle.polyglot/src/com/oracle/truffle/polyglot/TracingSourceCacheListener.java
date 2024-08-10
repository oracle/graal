/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;

final class TracingSourceCacheListener implements SourceCacheListener {

    private static final int MAX_SOURCE_NAME_LENGTH = 50;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 100;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.of("UTC"));
    // @formatter:off
    private static final String COMMON_PARAMS   =             "|Engine %-2d|Layer %-2d|CallTarget %-5d|Lang %-10s|Policy %-9s|%-8s|UTC %s";
    private static final String SOURCE_FORMAT = "0x%08x %-" + MAX_SOURCE_NAME_LENGTH + "s";
    private static final String HIT_FORMAT                = "source-cache-hit   " + SOURCE_FORMAT + "|Hits %12d"        + COMMON_PARAMS;
    private static final String MISS_FORMAT               = "source-cache-miss  " + SOURCE_FORMAT + "|ParseTime %4d ms" + COMMON_PARAMS;
    private static final String FAIL_FORMAT               = "source-cache-fail  " + SOURCE_FORMAT + "|ParseTime %4d ms" + COMMON_PARAMS + "|Error %s";
    private static final String EVICT_FORMAT              = "source-cache-evict " + SOURCE_FORMAT + "|Hits %12d"        + COMMON_PARAMS;
    // @formatter:on

    private static void log(PolyglotSharingLayer sharingLayer, String logFormat, Object... params) {
        sharingLayer.engine.getEngineLogger().log(Level.INFO, String.format(logFormat, params));
    }

    private static String truncateString(String s, int maxLength) {
        if (s == null || s.length() <= maxLength) {
            return s;
        } else {
            return s.substring(0, maxLength);
        }
    }

    private final boolean traceSourceCacheDetails;

    private TracingSourceCacheListener(boolean traceSourceCacheDetails) {
        this.traceSourceCacheDetails = traceSourceCacheDetails;
    }

    static TracingSourceCacheListener createOrNull(PolyglotEngineImpl engine) {
        boolean traceSourceCacheDetails = engine.engineOptionValues.get(PolyglotEngineOptions.TraceSourceCacheDetails);
        boolean traceSourceCache = traceSourceCacheDetails || engine.engineOptionValues.get(PolyglotEngineOptions.TraceSourceCache);
        if (traceSourceCache) {
            return new TracingSourceCacheListener(traceSourceCacheDetails);
        } else {
            return null;
        }
    }

    @Override
    public void onCacheHit(Source source, CallTarget target, CacheType cacheType, long hits) {
        if (traceSourceCacheDetails && target instanceof RootCallTarget) {
            PolyglotSharingLayer sharingLayer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(((RootCallTarget) target).getRootNode());
            log(sharingLayer, HIT_FORMAT,
                            source.hashCode(),
                            truncateString(source.getName(), MAX_SOURCE_NAME_LENGTH),
                            hits,
                            sharingLayer.engine.engineId,
                            sharingLayer.shared.id,
                            EngineAccessor.RUNTIME.getCallTargetId(target),
                            source.getLanguage(),
                            sharingLayer.getContextPolicy().name(),
                            cacheType,
                            TIME_FORMATTER.format(ZonedDateTime.now()));
        }
    }

    @Override
    public void onCacheMiss(Source source, CallTarget target, CacheType cacheType, long startTime) {
        if ((traceSourceCacheDetails || cacheType != CacheType.UNCACHED) && target instanceof RootCallTarget) {
            PolyglotSharingLayer sharingLayer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(((RootCallTarget) target).getRootNode());
            log(sharingLayer, MISS_FORMAT,
                            source.hashCode(),
                            truncateString(source.getName(), MAX_SOURCE_NAME_LENGTH),
                            System.currentTimeMillis() - startTime,
                            sharingLayer.engine.engineId,
                            sharingLayer.shared.id,
                            EngineAccessor.RUNTIME.getCallTargetId(target),
                            source.getLanguage(),
                            sharingLayer.getContextPolicy().name(),
                            cacheType,
                            TIME_FORMATTER.format(ZonedDateTime.now()));
        }
    }

    @Override
    public void onCacheFail(PolyglotSharingLayer sharingLayer, Source source, CacheType cacheType, long startTime, Throwable throwable) {
        log(sharingLayer, FAIL_FORMAT,
                        source.hashCode(),
                        truncateString(source.getName(), MAX_SOURCE_NAME_LENGTH),
                        System.currentTimeMillis() - startTime,
                        sharingLayer.engine.engineId,
                        sharingLayer.shared.id,
                        0,
                        source.getLanguage(),
                        sharingLayer.getContextPolicy().name(),
                        cacheType,
                        TIME_FORMATTER.format(ZonedDateTime.now()),
                        truncateString(throwable.getMessage(), MAX_ERROR_MESSAGE_LENGTH));
    }

    @Override
    public void onCacheEvict(Source source, CallTarget target, CacheType cacheType, long hits) {
        if (target instanceof RootCallTarget) {
            PolyglotSharingLayer sharingLayer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(((RootCallTarget) target).getRootNode());
            log(sharingLayer, EVICT_FORMAT,
                            source.hashCode(),
                            truncateString(source.getName(), MAX_SOURCE_NAME_LENGTH),
                            hits,
                            sharingLayer.engine.engineId,
                            sharingLayer.shared.id,
                            EngineAccessor.RUNTIME.getCallTargetId(target),
                            source.getLanguage(),
                            sharingLayer.getContextPolicy().name(),
                            cacheType,
                            TIME_FORMATTER.format(ZonedDateTime.now()));
        }
    }
}
