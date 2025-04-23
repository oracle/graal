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

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

/**
 * I/O interface for {@link #ScanningModelBuilder}, which allows to open supplemental channels for a
 * given byte range. Subchalles can be created although the channel is still reading data.
 */
public interface CachedContent extends ReadableByteChannel {
    /**
     * Creates a readable channel for a region of data. Throws IOException if the underlying channel
     * closes.
     *
     * @param start offset of data start
     * @param end   offset of data end, exclusive
     * @return opened channel.
     * @throws IOException if the underlying channel/ buffer fails.
     */
    public ReadableByteChannel subChannel(long start, long end) throws IOException;

    /**
     * Resets the cache up to the read offset. The cache will be no longer required
     * (and able to serve) data from earlier offsets in the stream.
     *
     * @param lastReadOffset first offset which will survive
     * @return true, if the stream became permanently empty (is closed and truncated to zero).
     */
    public boolean resetCache(long lastReadOffset);

    /*
     * Identifies the content. The ID is used to form unique identifiers of lazy created
     * objects in the stream, and may be eventually persisted in caches.
     *
     * @return id of the cached content
     */
    public String id();
}
