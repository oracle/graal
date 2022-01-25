/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

/**
 * List of all possible {@link JfrBuffer} types.
 */
public enum JfrBufferType {
    /**
     * A thread-local native buffer, see {@link JfrThreadLocal}.
     */
    THREAD_LOCAL_NATIVE,
    /**
     * A thread-local java buffer, see {@link JfrThreadLocal}.
     */
    THREAD_LOCAL_JAVA,
    /**
     * A global JFR buffer, see {@link JfrGlobalMemory}.
     */
    GLOBAL_MEMORY,
    /**
     * Other buffers that live in the C heap and that can be resized (i.e., reallocated) if
     * necessary. This type is for example used for the epoch-based global buffers in
     * {@link JfrThreadRepository}.
     */
    C_HEAP
}
