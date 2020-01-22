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
import java.util.List;

// class defining interfaces used to allow a native image
// to communicate details of types, code and data to
// the underlying object file so that the object file
// can insert appropriate debug info
public interface DebugInfoProvider {
    // access details of a specific type
    interface DebugTypeInfo {
    }

    // access details of a specific compiled method
    interface DebugCodeInfo {
        String fileName();
        String className();
        String methodName();
        int addressLo();
        int addressHi();
        int line();
        DebugLineInfoProvider lineInfoProvider();
        String paramNames();
        String returnTypeName();
        int getFrameSize();
        List<DebugFrameSizeChange> getFrameSizeChanges();
    }

    // access details of a specific heap object
    interface DebugDataInfo {
    }

    // access details of a specific outer or inlined method at a given line number
    interface DebugLineInfo {
        String fileName();
        String className();
        String methodName();
        int addressLo();
        int addressHi();
        int line();
    }

    interface DebugFrameSizeChange {
        enum  Type {EXTEND, CONTRACT};
        int getOffset();
        DebugFrameSizeChange.Type getType();
    }

    // convenience interface defining iterator type
    interface DebugTypeInfoProvider extends Iterable<DebugTypeInfo> {
    }

    // convenience interface defining iterator type
    interface DebugCodeInfoProvider extends Iterable<DebugCodeInfo> {
    }

    // convenience interface defining iterator type
    interface DebugLineInfoProvider extends Iterable<DebugLineInfo>{
    }

    // convenience interface defining iterator type
    interface DebugDataInfoProvider extends Iterable<DebugDataInfo> {
    }

    DebugTypeInfoProvider typeInfoProvider();
    DebugCodeInfoProvider codeInfoProvider();
    DebugDataInfoProvider dataInfoProvider();
}
