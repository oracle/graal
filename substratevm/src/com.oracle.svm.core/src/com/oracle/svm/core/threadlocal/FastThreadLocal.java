/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.threadlocal;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

/**
 * Base class for fast thread local variables. See {@link FastThreadLocalFactory} for details and
 * restrictions of VM thread local variables.
 */
public abstract class FastThreadLocal {
    class FastThreadLocalLocationIdentity extends LocationIdentity {
        @Override
        public boolean isImmutable() {
            return false;
        }

        @Override
        public String toString() {
            return "FastThreadLocal:" + name;
        }

    }

    private final LocationIdentity locationIdentity;
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    FastThreadLocal(String name) {
        this.locationIdentity = new FastThreadLocalLocationIdentity();
        this.name = name;
    }

    /**
     * Returns the {@link LocationIdentity} used for memory accesses performed by the {@code get}
     * and {@code set} methods of the subclasses.
     */
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    private int maxOffset = Integer.MAX_VALUE;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private boolean allowFloatingReads = false;

    /**
     * Useful value for {@link #setMaxOffset}: The thread local variable is in the first cache line
     * of the memory block. This allows grouping of the most frequently accessed variables.
     *
     * We are not using a real cache line size, but instead assume that 64 bytes is the common
     * minimum size on all platforms.
     */
    public static final int FIRST_CACHE_LINE = 63;

    /**
     * Useful value for {@link #setMaxOffset}: The thread local variable has an offset that can be
     * expressed as a signed 8-bit value. Some architectures, e.g. AMD64, need fewer bytes to encode
     * such offsets.
     */
    public static final int BYTE_OFFSET = 127;

    /**
     * Sets the maximum offset of this thread local variable in the memory block reserved for each
     * thread. This can be used for performance and footprint optimization, to group frequently
     * accessed values closely together with low offsets that can be encoded more efficiently in
     * machine code.
     */
    @SuppressWarnings("unchecked")
    @Platforms(Platform.HOSTED_ONLY.class)
    public <T extends FastThreadLocal> T setMaxOffset(int maxOffset) {
        this.maxOffset = maxOffset;
        return (T) this;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getMaxOffset() {
        return maxOffset;
    }

    /**
     * Sets whether non-volatile fast thread local reads can be read using floating semantics in the
     * compiler graph. If this is set to <code>false</code> (default) then reads will always remain
     * fixed in the graph and do not move. If this is set to <code>true</code> then reads can float
     * according to the location identity of the fast thread local.
     */
    @SuppressWarnings("unchecked")
    @Platforms(Platform.HOSTED_ONLY.class)
    public <T extends FastThreadLocal> T setAllowFloatingReads(boolean allow) {
        this.allowFloatingReads = allow;
        return (T) this;
    }

    /**
     * Returns <code>true</code> if the floating reads is enabled, else <code>false</code>.
     *
     * @see #setAllowFloatingReads(boolean)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean getAllowFloatingReads() {
        return this.allowFloatingReads;
    }

    public String getName() {
        return name;
    }

}
