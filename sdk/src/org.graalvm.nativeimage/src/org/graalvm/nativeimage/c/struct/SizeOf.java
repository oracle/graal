/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.struct;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.SizeOfSupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * Contains static methods that provide access to the size of <b>dereferenced</b> SystemJava pointer
 * types (i.e. the size of the data structure pointed-to by SystemJava pointer). Note that this
 * semantic differs from the sizeof-operator defined by the C programming language.
 * 
 * @since 1.0
 */
public final class SizeOf {

    private SizeOf() {
    }

    /**
     * Returns the size of the data structure pointed to by SystemJava pointer types. The class must
     * be annotated with {@link CStruct}, {@link CPointerTo}, or {@link RawStructure}.
     *
     * @since 1.0
     */
    public static int get(Class<? extends PointerBase> clazz) {
        return ImageSingletons.lookup(SizeOfSupport.class).sizeof(clazz);
    }

    /**
     * Returns the {@link #get size} cast to {@link UnsignedWord}.
     *
     * @since 1.0
     */
    public static UnsignedWord unsigned(Class<? extends PointerBase> clazz) {
        return WordFactory.unsigned(get(clazz));
    }
}
