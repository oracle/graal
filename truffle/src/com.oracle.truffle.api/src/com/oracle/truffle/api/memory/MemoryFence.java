/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.memory;

import com.oracle.truffle.api.impl.Accessor;

/**
 * Provides memory fence methods for fine-grained control of memory ordering. Their semantics follow
 * the semantics of the same static methods from
 * <a href="https://docs.oracle.com/javase/9/docs/api/java/lang/invoke/VarHandle.html">VarHandle</a>
 * class from Java 9 and later.
 *
 * @since 21.2
 */
public final class MemoryFence {

    private MemoryFence() {
    }

    /**
     * Ensures that loads and stores before the fence will not be reordered with loads and stores
     * after the fence.
     *
     * @since 21.2
     */
    public static void full() {
        MemoryFenceAccessor.jdkServicesAccessor().fullFence();
    }

    /**
     * Ensures that loads before the fence will not be reordered with loads and stores after the
     * fence.
     *
     * @since 21.2
     */
    public static void acquire() {
        MemoryFenceAccessor.jdkServicesAccessor().acquireFence();
    }

    /**
     * Ensures that loads and stores before the fence will not be reordered with stores after the
     * fence.
     *
     * @since 21.2
     */
    public static void release() {
        MemoryFenceAccessor.jdkServicesAccessor().releaseFence();
    }

    /**
     * Ensures that loads before the fence will not be reordered with loads after the fence.
     *
     * @since 21.2
     */
    public static void loadLoad() {
        MemoryFenceAccessor.jdkServicesAccessor().loadLoadFence();
    }

    /**
     * Ensures that stores before the fence will not be reordered with stores after the fence.
     *
     * @since 21.2
     */
    public static void storeStore() {
        MemoryFenceAccessor.jdkServicesAccessor().storeStoreFence();
    }
}

final class MemoryFenceAccessor extends Accessor {

    private static final MemoryFenceAccessor ACCESSOR = new MemoryFenceAccessor();

    private MemoryFenceAccessor() {
    }

    static JDKSupport jdkServicesAccessor() {
        return ACCESSOR.jdkSupport();
    }
}
