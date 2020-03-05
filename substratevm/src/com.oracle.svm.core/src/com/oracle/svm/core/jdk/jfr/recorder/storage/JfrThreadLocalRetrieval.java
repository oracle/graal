/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.storage;

import java.util.Iterator;

// Do not use retrieval for memory spaces that are not the thread local memory space
public class JfrThreadLocalRetrieval extends JfrMemorySpaceRetrieval {
    public static JfrBuffer get(int size, JfrMemorySpace<?, ?> space, Thread thread) {
        Iterator<JfrBuffer> iter = space.free().iterator();
        if (iter.hasNext()) {
            JfrBuffer item = iter.next();
            // Assumptions made against thread local memory space implementation details
            assert (!item.isRetired());
            assert (item.identity() == -1);
            assert (item.getFreeSize() >= size);

            item.acquire(thread);
            space.free().remove(item);
            return item;
        }
        return null;
    }
}
