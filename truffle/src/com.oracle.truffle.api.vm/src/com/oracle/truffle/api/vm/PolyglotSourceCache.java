/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

final class PolyglotSourceCache {

    private final ConcurrentHashMap<Object, CallTarget> sourceCache;
    private final ReferenceQueue<Source> deadSources = new ReferenceQueue<>();
    private final List<SourceReference> weakReferences = new LinkedList<>();

    PolyglotSourceCache() {
        this.sourceCache = new ConcurrentHashMap<>();
    }

    CallTarget parseCached(PolyglotLanguageContext context, Source source, String[] argumentNames) throws AssertionError {
        cleanupStaleEntries();

        CallTarget target;
        if (source.isCached()) {
            Object sourceId = VMAccessor.SOURCE.getSourceIdentifier(source);
            if (argumentNames != null && argumentNames.length > 0) {
                sourceId = new ArgumentSourceId(sourceId, argumentNames);
            }

            target = sourceCache.get(sourceId);
            if (target == null) {
                target = sourceCache.computeIfAbsent(sourceId, new Function<Object, CallTarget>() {
                    @Override
                    public CallTarget apply(Object o) {
                        /*
                         * We need to capture every source as weak reference to get notified when
                         * sources are collected. We also need to ensure that weak references
                         * instances are not collected before the source reference hence the list of
                         * weak references is needed here.
                         */
                        synchronized (weakReferences) {
                            weakReferences.add(new SourceReference(o, source, deadSources));
                        }

                        /*
                         * We pass in for parsing only a copy of the original source. This allows us
                         * to keep a strong reference to CallTarget while keeping the source
                         * collectible. If the source is collected then the deadSources queue will
                         * be updated and the call target entry will be removed to clean the cache.
                         */
                        Source weakSource = VMAccessor.SOURCE.copySource(source);
                        return parseImpl(context, argumentNames, weakSource);
                    }
                });
            }
        } else {
            target = parseImpl(context, argumentNames, source);
        }
        return target;
    }

    private static CallTarget parseImpl(PolyglotLanguageContext context, String[] argumentNames, Source source) throws AssertionError {
        CallTarget parsedTarget = LANGUAGE.parse(context.requireEnv(), source, null, argumentNames);
        if (parsedTarget == null) {
            throw new AssertionError(String.format("Parsing resulted in a null CallTarget for %s.", source));
        }
        return parsedTarget;
    }

    private void cleanupStaleEntries() {
        List<SourceReference> references = null;
        SourceReference sourceRef = null;
        while ((sourceRef = (SourceReference) deadSources.poll()) != null) {
            sourceCache.remove(sourceRef.key);
            if (references == null) {
                references = new ArrayList<>();
            }
            references.add(sourceRef);
        }
        if (references != null) {
            synchronized (weakReferences) {
                weakReferences.removeAll(references);
            }
        }
    }

    private static final class SourceReference extends WeakReference<Source> {

        final Object key;

        SourceReference(Object key, Source value, ReferenceQueue<? super Source> q) {
            super(value, q);
            this.key = key;
        }

    }

    private static class ArgumentSourceId {

        private final Object sourceId;
        private final String[] arguments;

        ArgumentSourceId(Object sourceId, String[] arguments) {
            this.sourceId = sourceId;
            this.arguments = arguments;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((sourceId == null) ? 0 : sourceId.hashCode());
            result = prime * result + Arrays.hashCode(arguments);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ArgumentSourceId)) {
                return false;
            }
            ArgumentSourceId other = (ArgumentSourceId) obj;
            return sourceId.equals(other.sourceId) &&
                            Arrays.equals(arguments, other.arguments);
        }

    }

}
