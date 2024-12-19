/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class CheckedReader {

    private final SymbolicRefs symbolicRefs;

    public CheckedReader(SymbolicRefs symbolicRefs) {
        this.symbolicRefs = symbolicRefs;
    }

    public ResolvedJavaType readTypeRef(Packet.Reader input) throws JDWPException {
        return readTypeRef(input, false);
    }

    public long readTypeRefId(Packet.Reader input, boolean allowNull) throws JDWPException {
        long typeRefId = input.readLong();
        if (typeRefId == 0L) {
            if (!allowNull) {
                throw JDWPException.raise(ErrorCode.INVALID_CLASS);
            }
            return 0L;
        }
        symbolicRefs.toResolvedJavaType(typeRefId); // may throw JDWPException
        return typeRefId;
    }

    public long readMethodRefId(Packet.Reader input, boolean allowNull) throws JDWPException {
        long methodRefId = input.readLong();
        if (methodRefId == 0L) {
            if (!allowNull) {
                throw JDWPException.raise(ErrorCode.INVALID_METHODID);
            }
            return 0L;
        }
        symbolicRefs.toResolvedJavaMethod(methodRefId); // may throw JDWPException
        return methodRefId;
    }

    public long readFieldRefId(Packet.Reader input, boolean allowNull) throws JDWPException {
        long fieldRefId = input.readLong();
        if (fieldRefId == 0L) {
            if (!allowNull) {
                throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
            }
            return 0L;
        }
        symbolicRefs.toResolvedJavaField(fieldRefId); // may throw JDWPException
        return fieldRefId;
    }

    public ResolvedJavaType readTypeRef(Packet.Reader input, boolean allowNull) throws JDWPException {
        long typeRefId = readTypeRefId(input, allowNull);
        if (typeRefId == 0L && allowNull) {
            return null;
        }
        return symbolicRefs.toResolvedJavaType(typeRefId);
    }

    public ResolvedJavaMethod readMethodRef(Packet.Reader input) throws JDWPException {
        return readMethodRef(input, false);
    }

    public ResolvedJavaMethod readMethodRef(Packet.Reader input, boolean allowNull) throws JDWPException {
        long methodRefId = readMethodRefId(input, allowNull);
        if (methodRefId == 0L) {
            return null;
        }
        return symbolicRefs.toResolvedJavaMethod(methodRefId);
    }

    public ResolvedJavaField readFieldRef(Packet.Reader input, boolean allowNull) throws JDWPException {
        long fieldRefId = readTypeRefId(input, allowNull);
        if (fieldRefId == 0L) {
            return null;
        }
        return symbolicRefs.toResolvedJavaField(fieldRefId);
    }

    public ResolvedJavaField readFieldRef(Packet.Reader input) throws JDWPException {
        return readFieldRef(input, false);
    }
}
