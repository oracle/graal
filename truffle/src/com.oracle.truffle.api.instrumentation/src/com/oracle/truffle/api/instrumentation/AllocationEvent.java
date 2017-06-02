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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * An event representing an allocation of a guest language value.
 *
 * @since 0.27
 */
public final class AllocationEvent {

    private final LanguageInfo language;
    private final Object value;
    private final long sizeChange;

    AllocationEvent(LanguageInfo language, Object value, long sizeChange) {
        this.language = language;
        this.value = value;
        this.sizeChange = sizeChange;
    }

    /**
     * Returns the language performing the allocation.
     *
     * @since 0.27
     */
    public LanguageInfo getLanguage() {
        return language;
    }

    /**
     * Returns a size of the allocated value, or the size difference caused by re-allocation, in
     * bytes. When called from
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)} an
     * estimated size change caused by the upcoming allocation is provided. When called from
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent)}
     * a corrected size change can is provided, which might differ from the one reported in
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)
     * onEnter}. When the size change is unknown, {@link AllocationReporter#SIZE_UNKNOWN} is
     * returned. The size change of a re-allocation can be also negative.
     *
     * @since 0.27
     */
    public long getSizeChange() {
        return sizeChange;
    }

    /**
     * Returns the value which is a subject of allocation. When called from
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)}
     * the returned value is either <code>null</code> when a new one is to be allocated, or non-
     * <code>null</code> when the value is to be re-allocated. When called from
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent)}
     * it is always non-<code>null</code> and it is either the newly allocated value, or the same
     * instance of the re-allocated value as was provided in the preceding call to
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)}.
     *
     * @since 0.27
     */
    public Object getValue() {
        return value;
    }
}
