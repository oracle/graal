/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

final class InternedSources {

    private final ConcurrentHashMap<SourceImpl.Key, WeakSourceRef> table = new ConcurrentHashMap<>();
    private final ReferenceQueue<SourceImpl> deadReferences = new ReferenceQueue<>();

    Source intern(SourceImpl.Key key) {
        cleanupStaleEntries();

        if (!key.cached) {
            return key.toSourceNotInterned();
        }

        WeakSourceRef sourceRef = table.get(key);
        SourceImpl source = sourceRef != null ? sourceRef.get() : null;
        if (source == null) {
            while (true) {
                source = key.toSourceInterned();
                sourceRef = new WeakSourceRef(source, deadReferences);
                WeakSourceRef oldSourceRef = table.putIfAbsent(key, sourceRef);
                if (oldSourceRef != null) {
                    SourceImpl otherSource = oldSourceRef.get();
                    if (otherSource == null) {
                        // other thread put the same key but got collected
                        boolean replaced = table.replace(key, oldSourceRef, sourceRef);
                        if (replaced) {
                            // succeeded to replace the existing collected weak ref
                            return source;
                        } else {
                            // other thread put the same key in the meantime -> restart
                            continue;
                        }
                    } else {
                        // other thread put the same key but is not collected
                        assert otherSource != source;
                        return otherSource;
                    }
                }
                assert source != null;
                break;
            }
        }
        return source;
    }

    void resetNativeImageState() {
        table.clear();
    }

    private void cleanupStaleEntries() {
        WeakSourceRef sourceRef = null;
        while ((sourceRef = (WeakSourceRef) deadReferences.poll()) != null) {
            table.remove(sourceRef.key, sourceRef);
        }
    }

    private static class WeakSourceRef extends WeakReference<SourceImpl> {

        final SourceImpl.Key key;

        WeakSourceRef(SourceImpl referent, ReferenceQueue<SourceImpl> q) {
            super(referent, q);
            this.key = referent.toKey();
        }

    }

}
