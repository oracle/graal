/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects objects present in the stream. Currently only holds position-based Map of metadata, but
 * created as holder class to support future extensions
 */
final class StreamIndex {
    private static final Logger LOG = Logger.getLogger(StreamIndex.class.getName());

    private final Map<Long, StreamEntry> map = new LinkedHashMap<>();
    private long largestOffset = -1;

    public synchronized StreamEntry addEntry(StreamEntry en) {
        StreamEntry res = map.putIfAbsent(en.getStart(), en);
        largestOffset = Math.max(en.getStart(), largestOffset);
        notifyAll();
        return res == null ? en : res;
    }

    public synchronized StreamEntry get(long position) {
        waitOffset(position);
        return map.get(position);
    }

    public void waitOffset(long offset) {
        synchronized (this) {
            while (offset > largestOffset) {
                try {
                    LOG.log(Level.FINER, "Waiting for indexer to reach offset {0}", offset);
                    wait();
                } catch (InterruptedException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public synchronized void close() {
        LOG.log(Level.FINER, "Index closing, largest entry offset: {0}", largestOffset);
        largestOffset = Long.MAX_VALUE;
        notifyAll();
    }

    public Iterator<StreamEntry> iterator() {
        return Collections.unmodifiableMap(map).values().iterator();
    }
}
