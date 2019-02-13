/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.function.CEntryPointOptions;

/**
 * An object of this class represents a chunk of static global data that is located outside the heap
 * and can be accessed directly {@linkplain PointerBase by address}. No
 * {@linkplain CEntryPointOptions#prologue()} Java execution context} is required.
 */
public abstract class CGlobalData<T extends PointerBase> {
    /**
     * @return at runtime, a pointer to the data represented by this object.
     */
    public final T get() {
        throw new IllegalStateException("Cannot access address of global data during native image generation");
    }
}
