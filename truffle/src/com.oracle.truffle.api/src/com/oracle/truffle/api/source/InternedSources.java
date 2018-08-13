/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
