/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

final class PolyglotSourceCache {

    private final Cache strongCache;
    private final Cache weakCache;
    private SourceCacheListener sourceCacheListener;

    PolyglotSourceCache(SourceCacheListener sourceCacheListener) {
        this.sourceCacheListener = sourceCacheListener;
        this.weakCache = new WeakCache();
        this.strongCache = new StrongCache();
    }

    void patch(SourceCacheListener listener) {
        this.sourceCacheListener = listener;
    }

    CallTarget parseCached(PolyglotLanguageContext context, Source source, String[] argumentNames) {
        CallTarget target;
        if (source.isCached()) {
            Cache strong = this.strongCache;
            boolean useStrong = context.getEngine().storeEngine;
            if (useStrong || !strong.isEmpty()) {
                target = strong.lookup(context, source, argumentNames, useStrong);
                if (target != null) {
                    // target found in strong cache
                    return target;
                } else {
                    // fallback to weak cache.
                }
            }
            target = weakCache.lookup(context, source, argumentNames, true);
        } else {
            long parseStart = 0;
            if (sourceCacheListener != null) {
                parseStart = System.currentTimeMillis();
            }
            target = parseImpl(context, argumentNames, source);
            if (sourceCacheListener != null) {
                sourceCacheListener.onCacheMiss(source, target, SourceCacheListener.CacheType.UNCACHED, parseStart);
            }
        }
        return target;
    }

    void listCachedSources(PolyglotImpl polyglot, Collection<Object> source) {
        strongCache.listSources(polyglot, source);
        weakCache.listSources(polyglot, source);
    }

    private static CallTarget parseImpl(PolyglotLanguageContext context, String[] argumentNames, Source source) {
        validateSource(context, source);
        CallTarget parsedTarget = LANGUAGE.parse(context.requireEnv(), source, null, argumentNames);
        if (parsedTarget == null) {
            throw new IllegalStateException(String.format("Parsing resulted in a null CallTarget for %s.", source));
        }
        return parsedTarget;
    }

    private static void validateSource(PolyglotLanguageContext context, Source source) {
        if (!source.hasBytes() && !source.hasCharacters()) {
            throw PolyglotEngineException.illegalArgument(String.format("Error evaluating the source. The source does not specify characters nor bytes."));
        }
        String mimeType = source.getMimeType();
        Set<String> mimeTypes = context.language.cache.getMimeTypes();
        if (mimeType != null && !mimeTypes.contains(mimeType)) {
            throw PolyglotEngineException.illegalArgument(String.format("Error evaluating the source. The language %s does not support MIME type %s. Supported MIME types are %s.",
                            source.getLanguage(), mimeType, mimeTypes));
        }
        String activeMimeType = mimeType;
        if (activeMimeType == null) {
            activeMimeType = context.language.cache.getDefaultMimeType();
        }

        boolean expectCharacters = activeMimeType != null ? context.language.cache.isCharacterMimeType(activeMimeType) : true;
        if (mimeType != null && source.hasCharacters() != expectCharacters) {
            if (source.hasBytes()) {
                throw PolyglotEngineException.illegalArgument(
                                String.format("Error evaluating the source. MIME type '%s' is character based for language '%s' but the source contents are byte based.", mimeType,
                                                source.getLanguage()));
            } else {
                throw PolyglotEngineException.illegalArgument(
                                String.format("Error evaluating the source. MIME type '%s' is byte based for language '%s' but the source contents are character based.", mimeType,
                                                source.getLanguage()));
            }
        }

        if (source.hasCharacters() != expectCharacters) {
            Set<String> binaryMimeTypes = new HashSet<>();
            Set<String> characterMimeTypes = new HashSet<>();
            for (String supportedMimeType : mimeTypes) {
                if (context.language.cache.isCharacterMimeType(supportedMimeType)) {
                    characterMimeTypes.add(supportedMimeType);
                } else {
                    binaryMimeTypes.add(supportedMimeType);
                }
            }
            if (expectCharacters) {
                if (binaryMimeTypes.isEmpty()) {
                    throw PolyglotEngineException.illegalArgument(String.format(
                                    "Error evaluating the source. The language %s only supports character based sources but a binary based source was provided.",
                                    source.getLanguage()));
                } else {
                    throw PolyglotEngineException.illegalArgument(String.format(
                                    "Error evaluating the source. The language %s expects character based sources by default but a binary based source was provided. " +
                                                    "Provide a binary based source instead or specify a MIME type for the source. " +
                                                    "Available MIME types for binary based sources are %s.",
                                    source.getLanguage(), binaryMimeTypes));
                }
            } else {
                if (characterMimeTypes.isEmpty()) {
                    throw PolyglotEngineException.illegalArgument(String.format(
                                    "Error evaluating the source. The language %s only supports binary based sources but a character based source was provided.",
                                    source.getLanguage()));
                } else {
                    throw PolyglotEngineException.illegalArgument(String.format(
                                    "Error evaluating the source. The language %s expects character based sources by default but a binary based source was provided. " +
                                                    "Provide a character based source instead or specify a MIME type for the source. " +
                                                    "Available MIME types for character based sources are %s.",
                                    source.getLanguage(), characterMimeTypes));
                }
            }
        }
    }

    private abstract static class Cache {

        abstract boolean isEmpty();

        abstract CallTarget lookup(PolyglotLanguageContext context, Source source, String[] argumentNames, boolean parse);

        abstract void listSources(PolyglotImpl polyglot, Collection<Object> source);
    }

    static class StrongCacheValue {

        final CallTarget target;
        final AtomicLong hits = new AtomicLong();

        StrongCacheValue(CallTarget target) {
            this.target = target;
        }

    }

    private final class StrongCache extends Cache {

        private final ConcurrentHashMap<SourceKey, StrongCacheValue> sourceCache = new ConcurrentHashMap<>();

        @Override
        CallTarget lookup(PolyglotLanguageContext context, Source source, String[] argumentNames, boolean parse) {
            SourceKey key = new SourceKey(source, argumentNames);
            StrongCacheValue value = sourceCache.get(key);
            if (value == null) {
                if (parse) {
                    long parseStart = 0;
                    if (sourceCacheListener != null) {
                        parseStart = System.currentTimeMillis();
                    }
                    try {
                        value = new StrongCacheValue(parseImpl(context, argumentNames, source));
                        StrongCacheValue prevValue = sourceCache.putIfAbsent(key, value);
                        if (prevValue != null) {
                            value = prevValue;
                        }
                        if (sourceCacheListener != null) {
                            sourceCacheListener.onCacheMiss(source, value.target, SourceCacheListener.CacheType.STRONG, parseStart);
                        }
                    } catch (Throwable t) {
                        if (sourceCacheListener != null) {
                            sourceCacheListener.onCacheFail(context.context.layer, source, SourceCacheListener.CacheType.STRONG, parseStart, t);
                        }
                        throw t;
                    }
                } else {
                    return null;
                }
            } else {
                value.hits.incrementAndGet();
                if (sourceCacheListener != null) {
                    sourceCacheListener.onCacheHit(source, value.target, SourceCacheListener.CacheType.STRONG, value.hits.get());
                }
            }
            return value.target;
        }

        @Override
        boolean isEmpty() {
            return sourceCache.isEmpty();
        }

        @Override
        void listSources(PolyglotImpl polyglot, Collection<Object> sources) {
            for (SourceKey key : sourceCache.keySet()) {
                sources.add(PolyglotImpl.getOrCreatePolyglotSource(polyglot, (Source) key.key));
            }
        }

    }

    private final class WeakCache extends Cache {

        private final ConcurrentHashMap<WeakSourceKey, WeakCacheValue> sourceCache = new ConcurrentHashMap<>();
        private final ReferenceQueue<Source> deadSources = new ReferenceQueue<>();

        @Override
        CallTarget lookup(PolyglotLanguageContext context, Source source, String[] argumentNames, boolean parse) {
            cleanupStaleEntries();
            Object sourceId = EngineAccessor.SOURCE.getSourceIdentifier(source);
            Source sourceValue = EngineAccessor.SOURCE.copySource(source);
            WeakSourceKey ref = new WeakSourceKey(new SourceKey(sourceId, argumentNames), source, deadSources);
            WeakCacheValue value = sourceCache.get(ref);
            if (value == null) {
                if (parse) {
                    long parseStart = 0;
                    if (sourceCacheListener != null) {
                        parseStart = System.currentTimeMillis();
                    }
                    try {
                        value = new WeakCacheValue(parseImpl(context, argumentNames, sourceValue), sourceValue);
                        WeakCacheValue prev = sourceCache.putIfAbsent(ref, value);
                        if (prev != null) {
                            /*
                             * Parsed twice -> discard the one not in the cache.
                             */
                            value = prev;
                        }
                        if (sourceCacheListener != null) {
                            sourceCacheListener.onCacheMiss(source, value.target, SourceCacheListener.CacheType.WEAK, parseStart);
                        }
                    } catch (Throwable t) {
                        if (sourceCacheListener != null) {
                            sourceCacheListener.onCacheFail(context.context.layer, source, SourceCacheListener.CacheType.WEAK, parseStart, t);
                        }
                        throw t;
                    }
                } else {
                    return null;
                }
            } else {
                value.hits.incrementAndGet();
                if (sourceCacheListener != null) {
                    sourceCacheListener.onCacheHit(source, value.target, SourceCacheListener.CacheType.WEAK, value.hits.get());
                }
            }
            return value.target;
        }

        @Override
        boolean isEmpty() {
            return sourceCache.isEmpty();
        }

        @Override
        void listSources(PolyglotImpl polyglot, Collection<Object> sources) {
            cleanupStaleEntries();
            for (WeakCacheValue value : sourceCache.values()) {
                sources.add(PolyglotImpl.getOrCreatePolyglotSource(polyglot, value.source));
            }
        }

        private void cleanupStaleEntries() {
            WeakSourceKey sourceRef;
            while ((sourceRef = (WeakSourceKey) deadSources.poll()) != null) {
                WeakCacheValue value = sourceCache.remove(sourceRef);
                if (value != null && sourceCacheListener != null) {
                    sourceCacheListener.onCacheEvict(value.source, value.target, SourceCacheListener.CacheType.WEAK, value.hits.get());
                }
            }
        }

    }

    static class WeakCacheValue {

        final CallTarget target;
        final Source source;
        final AtomicLong hits = new AtomicLong();

        WeakCacheValue(CallTarget target, Source source) {
            this.target = target;
            this.source = source;
        }

    }

    private static final class SourceKey {

        private final Object key;
        private final String[] arguments;

        SourceKey(Object key, String[] arguments) {
            this.key = key;
            this.arguments = arguments != null && arguments.length == 0 ? null : arguments;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + key.hashCode();
            result = prime * result + Arrays.hashCode(arguments);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SourceKey) {
                SourceKey other = (SourceKey) obj;
                return key.equals(other.key) && Arrays.equals(arguments, other.arguments);
            } else {
                return false;
            }
        }

    }

    private static final class WeakSourceKey extends WeakReference<Source> {

        final SourceKey key;

        WeakSourceKey(SourceKey key, Source value, ReferenceQueue<? super Source> q) {
            super(value, q);
            this.key = key;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof WeakSourceKey) {
                WeakSourceKey other = (WeakSourceKey) obj;
                return key.equals(other.key);
            } else {
                return false;
            }
        }
    }

}
