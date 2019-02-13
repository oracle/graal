/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
    private final long oldSize;
    private final long newSize;

    AllocationEvent(LanguageInfo language, Object value, long oldSize, long newSize) {
        this.language = language;
        this.value = value;
        this.oldSize = oldSize;
        this.newSize = newSize;
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
     * Returns an old size of the value prior to the allocation, in bytes. Returns <code>0</code>
     * when a new value is to be allocated, or the size of the value prior to the re-allocation.
     * When the old size is unknown, {@link AllocationReporter#SIZE_UNKNOWN} is returned.
     *
     * @since 0.27
     */
    public long getOldSize() {
        return oldSize;
    }

    /**
     * Returns a size of the allocated value in bytes. When called from
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)} an
     * estimated size of the allocated value is provided. When called from
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent)}
     * a corrected size can be provided, which might differ from the one reported in
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)
     * onEnter}. When the allocated size is unknown, {@link AllocationReporter#SIZE_UNKNOWN} is
     * returned. The change in memory consumption caused by the allocation is
     * <code>{@link #getNewSize()} - {@link #getOldSize()}</code> when both old size and new size
     * are known. The change can be either positive or negative.
     *
     * @since 0.27
     */
    public long getNewSize() {
        return newSize;
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
