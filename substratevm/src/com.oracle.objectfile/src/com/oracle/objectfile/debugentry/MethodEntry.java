/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

public class MethodEntry extends MemberEntry implements Comparable<MethodEntry> {
    final TypeEntry[] paramTypes;
    final String[] paramNames;
    final boolean isDeoptTarget;

    public MethodEntry(FileEntry fileEntry, String methodName, ClassEntry ownerType, TypeEntry valueType, TypeEntry[] paramTypes, String[] paramNames, int modifiers, boolean isDeoptTarget) {
        super(fileEntry, methodName, ownerType, valueType, modifiers);
        assert ((paramTypes == null && paramNames == null) ||
                        (paramTypes != null && paramNames != null && paramTypes.length == paramNames.length));
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
        this.isDeoptTarget = isDeoptTarget;
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

    public int compareTo(String methodName, String paramSignature, String returnTypeName) {
        int nameComparison = memberName.compareTo(methodName);
        if (nameComparison != 0) {
            return nameComparison;
        }
        int typeComparison = valueType.getTypeName().compareTo(returnTypeName);
        if (typeComparison != 0) {
            return typeComparison;
        }
        String[] paramTypeNames = paramSignature.split((","));
        int length;
        if (paramSignature.trim().length() == 0) {
            length = 0;
        } else {
            length = paramTypeNames.length;
        }
        int paramCountComparison = getParamCount() - length;
        if (paramCountComparison != 0) {
            return paramCountComparison;
        }
        for (int i = 0; i < getParamCount(); i++) {
            int paraComparison = getParamTypeName(i).compareTo(paramTypeNames[i].trim());
            if (paraComparison != 0) {
                return paraComparison;
            }
        }
        return 0;
    }

    public boolean isDeoptTarget() {
        return isDeoptTarget;
    }

    @Override
    public int compareTo(MethodEntry other) {
        assert other != null;
        int nameComparison = methodName().compareTo(other.methodName());
        if (nameComparison != 0) {
            return nameComparison;
        }
        int typeComparison = valueType.getTypeName().compareTo(other.valueType.getTypeName());
        if (typeComparison != 0) {
            return typeComparison;
        }
        int paramCountComparison = getParamCount() - other.getParamCount();
        if (paramCountComparison != 0) {
            return paramCountComparison;
        }
        for (int i = 0; i < getParamCount(); i++) {
            int paramComparison = getParamTypeName(i).compareTo(other.getParamTypeName(i));
            if (paramComparison != 0) {
                return paramComparison;
            }
        }
        return 0;
    }
}
