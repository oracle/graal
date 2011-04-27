/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import java.util.*;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * This class collects a number of debugging and exception-related information about
 * an HIR node. Instances of this class can be attached to HIR nodes and contain
 * the {@link com.sun.cri.ci.CiCodePos code position}, the {@link com.sun.c1x.value.FrameState frame state}
 * potential exceptions, and exception handlers.
 *
 * @author Ben L. Titzer
 */
public class Info {

    /**
     * An enumeration of the possible exceptions or stops that an instruction may generate.
     */
    public enum StopType {
        /**
         * This instruction may throw {@link ArrayIndexOutOfBoundsException}.
         */
        java_lang_ArrayOutOfBoundsException,

        /**
         * This instruction may throw {@link NullPointerException}.
         */
        java_lang_NullPointerException,

        /**
         * This instruction may throw {@link ClassCastException}.
         */
        java_lang_ClassCastException,

        /**
         * This instruction may throw {@link ArrayStoreException}.
         */
        java_lang_ArrayStoreException,

        /**
         * This instruction may throw {@link ArithmeticException}.
         */
        java_lang_ArithmeticException,

        /**
         * This instruction may throw {@link NegativeArraySizeException}.
         */
        java_lang_NegativeArraySizeException,

        /**
         * This instruction may throw {@link OutOfMemoryError}.
         */
        java_lang_OutOfMemoryError,

        /**
         * This instruction may throw {@link IncompatibleClassChangeError}.
         */
        java_lang_IncompatibleClassChangeError,

        /**
         * This instruction may cause a safepoint.
         */
        Safepoint,

        /**
         * This instruction may throw any exception or cause a safepoint.
         */
        Unknown;

        public final int mask = 1 << ordinal();

    }

    public final CiCodePos pos;
    public final int id;
    private int stopFlags;
    private FrameState state;
    private List<ExceptionHandler> exceptionHandlers;

    public Info(CiCodePos pos, int id, FrameState javaFrameState) {
        this.pos = pos;
        this.id = id;
        this.state = javaFrameState;
    }

    public FrameState frameState() {
        return state;
    }

    public boolean mayStop() {
        return stopFlags != 0;
    }

    public boolean mayCauseException() {
        return (stopFlags & ~StopType.Safepoint.mask) != 0;
    }

    public void clearStop(StopType stopType) {
        stopFlags &= ~stopType.mask;
    }

    public void setStop(StopType stopType) {
        stopFlags |= stopType.mask;
    }

}
