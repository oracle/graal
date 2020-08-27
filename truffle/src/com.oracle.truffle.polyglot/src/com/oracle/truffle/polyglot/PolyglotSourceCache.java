/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

final class PolyglotSourceCache {

    private final ConcurrentHashMap<Object, CallTarget> sourceCache;
    private final ReferenceQueue<Source> deadSources = new ReferenceQueue<>();

    PolyglotSourceCache() {
        this.sourceCache = new ConcurrentHashMap<>();
    }

    CallTarget parseCached(PolyglotLanguageContext context, Source source, String[] argumentNames) {
        cleanupStaleEntries();

        CallTarget target;
        if (source.isCached()) {
            Object sourceId = EngineAccessor.SOURCE.getSourceIdentifier(source);
            WeakSourceKey ref = new WeakSourceKey(sourceId, source, argumentNames, deadSources);
            target = sourceCache.get(ref);
            if (target == null) {
                target = parseImpl(context, argumentNames, EngineAccessor.SOURCE.copySource(source));
                CallTarget prev = sourceCache.putIfAbsent(ref, target);
                if (prev != null) {
                    /*
                     * Parsed twice -> discard the one not in the cache.
                     */
                    target = prev;
                }
            }
        } else {
            target = parseImpl(context, argumentNames, source);
        }
        return target;
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

    private void cleanupStaleEntries() {
        WeakSourceKey sourceRef = null;
        while ((sourceRef = (WeakSourceKey) deadSources.poll()) != null) {
            sourceCache.remove(sourceRef);
        }
    }

    private static final class WeakSourceKey extends WeakReference<Source> {

        final Object key;
        private final String[] arguments;

        WeakSourceKey(Object key, Source value, String[] arguments, ReferenceQueue<? super Source> q) {
            super(value, q);
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
            if (obj instanceof WeakSourceKey) {
                WeakSourceKey other = (WeakSourceKey) obj;
                return key.equals(other.key) && Arrays.equals(arguments, other.arguments);
            } else {
                return false;
            }
        }
    }

}
