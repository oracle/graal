/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.java.*;

/**
 * Represents a method signature.
 */
public class HotSpotSignature extends CompilerObject implements Signature {

    private static final long serialVersionUID = -2890917956072366116L;
    private final List<String> parameters = new ArrayList<>();
    private final String returnType;
    private final String originalString;
    private JavaType[] parameterTypes;
    private JavaType returnTypeCache;

    public HotSpotSignature(String signature) {
        assert signature.length() > 0;
        this.originalString = signature;

        if (signature.charAt(0) == '(') {
            int cur = 1;
            while (cur < signature.length() && signature.charAt(cur) != ')') {
                int nextCur = parseSignature(signature, cur);
                parameters.add(signature.substring(cur, nextCur));
                cur = nextCur;
            }

            cur++;
            int nextCur = parseSignature(signature, cur);
            returnType = signature.substring(cur, nextCur);
            assert nextCur == signature.length();
        } else {
            returnType = null;
        }
    }

    public HotSpotSignature(JavaType returnType, JavaType... parameterTypes) {
        this.parameterTypes = parameterTypes.clone();
        this.returnTypeCache = returnType;
        this.returnType = returnType.getName();
        StringBuilder sb = new StringBuilder("(");
        for (JavaType type : parameterTypes) {
            parameters.add(type.getName());
            sb.append(type.getName());
        }
        sb.append(")").append(returnType.getName());
        this.originalString = sb.toString();
        assert new HotSpotSignature(originalString).equals(this);
    }

    private static int parseSignature(String signature, int start) {
        int cur = start;
        char first;
        do {
            first = signature.charAt(cur++);
        } while (first == '[');

        switch (first) {
            case 'L':
                while (signature.charAt(cur) != ';') {
                    cur++;
                }
                cur++;
                break;
            case 'V':
            case 'I':
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'J':
            case 'S':
            case 'Z':
                break;
            default:
                throw new GraalInternalError("Invalid character at index " + cur + " in signature: " + signature);
        }
        return cur;
    }

    @Override
    public int getParameterCount(boolean withReceiver) {
        return parameters.size() + (withReceiver ? 1 : 0);
    }

    @Override
    public Kind getParameterKind(int index) {
        return Kind.fromTypeString(parameters.get(index));
    }

    @Override
    public int getParameterSlots(boolean withReceiver) {
        int argSlots = 0;
        for (int i = 0; i < getParameterCount(false); i++) {
            argSlots += HIRFrameStateBuilder.stackSlots(getParameterKind(i));
        }
        return argSlots + (withReceiver ? 1 : 0);
    }

    private static boolean checkValidCache(JavaType type, ResolvedJavaType accessingClass) {
        if (!(type instanceof ResolvedJavaType)) {
            return false;
        }

        if (type instanceof HotSpotResolvedObjectType) {
            HotSpotResolvedObjectType resolved = (HotSpotResolvedObjectType) type;
            if (accessingClass == null) {
                return resolved.mirror().getClassLoader() == null;
            } else {
                return resolved.mirror().getClassLoader() == ((HotSpotResolvedObjectType) accessingClass).mirror().getClassLoader();
            }
        }

        return true;
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        if (parameterTypes == null) {
            parameterTypes = new JavaType[parameters.size()];
        }
        JavaType type = parameterTypes[index];
        if (!checkValidCache(type, accessingClass)) {
            type = runtime().lookupType(parameters.get(index), (HotSpotResolvedObjectType) accessingClass, false);
            parameterTypes[index] = type;
        }
        return type;
    }

    public String getMethodDescriptor() {
        assert originalString.equals(MetaUtil.signatureToMethodDescriptor(this));
        return originalString;
    }

    @Override
    public Kind getReturnKind() {
        return Kind.fromTypeString(returnType);
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        if (!checkValidCache(returnTypeCache, accessingClass)) {
            returnTypeCache = runtime().lookupType(returnType, (HotSpotResolvedObjectType) accessingClass, false);
        }
        return returnTypeCache;
    }

    @Override
    public String toString() {
        return "HotSpotSignature<" + originalString + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HotSpotSignature) {
            HotSpotSignature other = (HotSpotSignature) obj;
            if (other.originalString.equals(originalString)) {
                assert other.parameters.equals(parameters);
                assert other.returnType.equals(returnType);
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return originalString.hashCode();
    }
}
