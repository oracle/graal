/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debuginfo;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.graalvm.compiler.debug.DebugContext;

/**
 * Interfaces used to allow a native image to communicate details of types, code and data to the
 * underlying object file so that the latter can insert appropriate debug info.
 */
public interface DebugInfoProvider {
    /**
     * Access details of a specific type.
     */
    interface DebugTypeInfo {
    }

    /**
     * Access details of a specific compiled method.
     */
    interface DebugCodeInfo {
        void debugContext(Consumer<DebugContext> action);

        /**
         * @return the name of the file containing a compiled method excluding any path.
         */
        String fileName();

        /**
         * @return a relative path to the file containing a compiled method derived from its package
         *         name or null if the method is in the empty package.
         */
        Path filePath();

        /**
         * @return a relative path to the source cache containing the sources of a compiled method
         *         or {@code null} if sources are not available.
         */
        Path cachePath();

        /**
         * @return the fully qualified name of the class owning the compiled method.
         */
        String className();

        /**
         * @return the name of the compiled method including signature.
         */
        String methodName();

        /**
         * @return the lowest address containing code generated for the method represented as an
         *         offset into the code segment.
         */
        int addressLo();

        /**
         * @return the first address above the code generated for the method represented as an
         *         offset into the code segment.
         */
        int addressHi();

        /**
         * @return the starting line number for the method.
         */
        int line();

        /**
         * @return a stream of records detailing line numbers and addresses within the compiled
         *         method.
         */
        Stream<DebugLineInfo> lineInfoProvider();

        /**
         * @return a string identifying the method parameters.
         */
        String paramNames();

        /**
         * @return a string identifying the method return type.
         */
        String returnTypeName();

        /**
         * @return the size of the method frame between prologue and epilogue.
         */
        int getFrameSize();

        /**
         * @return a list of positions at which the stack is extended to a full frame or torn down
         *         to an empty frame
         */
        List<DebugFrameSizeChange> getFrameSizeChanges();

        /**
         * @return true if this method has been compiled in as a deoptimization target
         */
        boolean isDeoptTarget();
    }

    /**
     * Access details of a specific heap object.
     */
    interface DebugDataInfo {
    }

    /**
     * Access details of code generated for a specific outer or inlined method at a given line
     * number.
     */
    interface DebugLineInfo {
        /**
         * @return the name of the file containing the outer or inlined method excluding any path.
         */
        String fileName();

        /**
         * @return a relative path to the file containing the outer or inlined method derived from
         *         its package name or null if the method is in the empty package.
         */
        Path filePath();

        /**
         * @return a relative path to the source cache containing the sources of a compiled method
         *         or {@code null} if sources are not available.
         */
        Path cachePath();

        /**
         * @return the fully qualified name of the class owning the outer or inlined method.
         */
        String className();

        /**
         * @return the name of the outer or inlined method including signature.
         */
        String methodName();

        /**
         * @return the lowest address containing code generated for an outer or inlined code segment
         *         reported at this line represented as an offset into the code segment.
         */
        int addressLo();

        /**
         * @return the first address above the code generated for an outer or inlined code segment
         *         reported at this line represented as an offset into the code segment.
         */
        int addressHi();

        /**
         * @return the line number for the outer or inlined segment.
         */
        int line();
    }

    interface DebugFrameSizeChange {
        enum Type {
            EXTEND,
            CONTRACT
        }

        int getOffset();

        DebugFrameSizeChange.Type getType();
    }

    @SuppressWarnings("unused")
    Stream<DebugTypeInfo> typeInfoProvider();

    Stream<DebugCodeInfo> codeInfoProvider();

    @SuppressWarnings("unused")
    Stream<DebugDataInfo> dataInfoProvider();
}
