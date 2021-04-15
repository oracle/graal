/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

public class MethodEntry extends MemberEntry {
    TypeEntry[] paramTypes;
    String[] paramNames;

    public MethodEntry(FileEntry fileEntry, String methodName, ClassEntry ownerType, TypeEntry valueType, TypeEntry[] paramTypes, String[] paramNames, int modifiers) {
        super(fileEntry, methodName, ownerType, valueType, modifiers);
        assert ((paramTypes == null && paramNames == null) ||
                        (paramTypes != null && paramNames != null && paramTypes.length == paramNames.length));
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
    }

    public String methodName() {
        return memberName;
    }

    @Override
    public ClassEntry ownerType() {
        assert ownerType instanceof ClassEntry;
        return (ClassEntry) ownerType;
    }

    public int getParamCount() {
        return (paramTypes == null ? 0 : paramTypes.length);
    }

    public TypeEntry getParamType(int idx) {
        assert paramTypes != null;
        assert idx < paramTypes.length;
        return paramTypes[idx];
    }

    public String getParamTypeName(int idx) {
        assert paramTypes != null;
        assert idx < paramTypes.length;
        assert paramTypes[idx] != null;
        return paramTypes[idx].getTypeName();
    }

    public String getParamName(int idx) {
        assert paramNames != null;
        assert idx < paramNames.length;
        /* N.b. param names may be null. */
        return paramNames[idx];
    }

    public boolean match(String methodName, String paramSignature, String returnTypeName) {
        if (!methodName.equals(this.memberName)) {
            return false;
        }
        if (!returnTypeName.equals(valueType.getTypeName())) {
            return false;
        }
        int paramCount = getParamCount();
        if (paramCount == 0) {
            return paramSignature.trim().length() == 0;
        }
        String[] paramTypeNames = paramSignature.split((","));
        if (paramCount != paramTypeNames.length) {
            return false;
        }
        for (int i = 0; i < paramCount; i++) {
            if (!paramTypeNames[i].trim().equals(getParamTypeName(i))) {
                return false;
            }
        }
        return true;
    }
}
