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
import java.util.Arrays;
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
            Object sourceId = VMAccessor.SOURCE.getSourceIdentifier(source);
            if (argumentNames != null && argumentNames.length > 0) {
                sourceId = new ArgumentSourceId(sourceId, argumentNames);
            }

            target = sourceCache.get(sourceId);
            if (target == null) {
                /*
                 * We use a weak reference as key to get notified when sources are collected.
                 */
                SourceWeakReference ref = new SourceWeakReference(sourceId, source, deadSources);
                Source weakSource = VMAccessor.SOURCE.copySource(source);
                target = parseImpl(context, argumentNames, weakSource);
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
        CallTarget parsedTarget = LANGUAGE.parse(context.requireEnv(), source, null, argumentNames);
        if (parsedTarget == null) {
            throw new IllegalStateException(String.format("Parsing resulted in a null CallTarget for %s.", source));
        }
        return parsedTarget;
    }

    private void cleanupStaleEntries() {
        SourceWeakReference sourceRef = null;
        while ((sourceRef = (SourceWeakReference) deadSources.poll()) != null) {
            sourceCache.remove(sourceRef);
        }
    }

    private static final class SourceWeakReference extends WeakReference<Source> {

        final Object key;

        SourceWeakReference(Object key, Source value, ReferenceQueue<? super Source> q) {
            super(value, q);
            this.key = key;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            Object other;
            if (!(obj instanceof SourceWeakReference)) {
                other = obj;
            } else {
                other = ((SourceWeakReference) obj).key;
            }
            return key.equals(other);
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
