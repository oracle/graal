/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

/**
 * Values that can be used for the option
 * {@link com.oracle.svm.core.SubstrateOptions.ConcealedOptions#ReferenceHandlerMode}.
 */
public abstract class ReferenceHandlerMode {
    /**
     * Disables automatic reference handling. If reference handling is needed, then it must be done
     * manually by calling {@link Heap#doReferenceHandling()}.
     */
    public static final int ExecuteManually = 0;

    /** Uses a dedicated thread to do the reference handling. */
    public static final int UseDedicatedThread = 1;

    /**
     * Deprecated, will be removed as soon as possible (see GR-36676): does the reference handling
     * in the regular Java threads (e.g., at the end of the allocation slow-path).
     */
    public static final int UseRegularJavaThreads = 2;
}
