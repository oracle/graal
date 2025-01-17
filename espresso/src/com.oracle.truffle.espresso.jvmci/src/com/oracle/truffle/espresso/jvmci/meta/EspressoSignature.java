/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Implementation of {@link Signature} that caches espresso types. Mostly a copy of
 * {@link jdk.vm.ci.hotspot.HotSpotSignature}.
 */
public final class EspressoSignature implements Signature {
    private final List<String> parameters = new ArrayList<>();
    private final String returnType;
    private final String rawSignature;
    private EspressoResolvedJavaType[] parameterTypesCache;
    private EspressoResolvedJavaType returnTypeCache;

    public EspressoSignature(String rawSignature) {
        if (rawSignature.isEmpty()) {
            throw new IllegalArgumentException("Signature cannot be empty");
        }
        this.rawSignature = rawSignature;
        if (rawSignature.charAt(0) == '(') {
            int cur = 1;
            while (cur < rawSignature.length() && rawSignature.charAt(cur) != ')') {
                int nextCur = parseSignature(rawSignature, cur);
                parameters.add(rawSignature.substring(cur, nextCur));
                cur = nextCur;
            }

            cur++;
            int nextCur = parseSignature(rawSignature, cur);
            returnType = rawSignature.substring(cur, nextCur);
            if (nextCur != rawSignature.length()) {
                throw new IllegalArgumentException("Extra characters at end of signature: " + rawSignature);
            }
        } else {
            throw new IllegalArgumentException("Signature must start with a '(': " + rawSignature);
        }
    }

    private static int parseSignature(String signature, int start) {
        try {
            int cur = start;
            char first;
            do {
                first = signature.charAt(cur);
                cur++;
            } while (first == '[');

            switch (first) {
                case 'L':
                    while (signature.charAt(cur) != ';') {
                        if (signature.charAt(cur) == '.') {
                            throw new IllegalArgumentException("Class name in signature contains '.' at index " + cur + ": " + signature);
                        }
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
                    throw new IllegalArgumentException("Invalid character '" + signature.charAt(cur - 1) + "' at index " + (cur - 1) + " in signature: " + signature);
            }
            return cur;
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Truncated signature: " + signature);
        }
    }

    @Override
    public int getParameterCount(boolean withReceiver) {
        return parameters.size() + (withReceiver ? 1 : 0);
    }

    private static boolean checkValidCache(ResolvedJavaType type, ResolvedJavaType accessingClass) {
        assert accessingClass != null;
        if (type == null) {
            return false;
        } else if (type instanceof EspressoResolvedObjectType) {
            return ((EspressoResolvedObjectType) type).isDefinitelyResolvedWithRespectTo(accessingClass);
        }
        return true;
    }

    private static JavaType getUnresolvedOrPrimitiveType(String name) {
        if (name.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            return EspressoResolvedPrimitiveType.forKind(kind);
        }
        return UnresolvedJavaType.create(name);
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        if (accessingClass == null) {
            // Caller doesn't care about resolution context so return an unresolved
            // or primitive type (primitive type resolution is context free)
            return getUnresolvedOrPrimitiveType(parameters.get(index));
        }
        if (parameterTypesCache == null) {
            parameterTypesCache = new EspressoResolvedJavaType[parameters.size()];
        }

        EspressoResolvedJavaType type = parameterTypesCache[index];
        if (!checkValidCache(type, accessingClass)) {
            JavaType result = lookupType(parameters.get(index), (EspressoResolvedInstanceType) accessingClass);
            if (result instanceof EspressoResolvedJavaType) {
                type = (EspressoResolvedJavaType) result;
                parameterTypesCache[index] = type;
            } else {
                assert result != null;
                return result;
            }
        }
        assert type != null;
        return type;
    }

    @Override
    public JavaKind getParameterKind(int index) {
        return JavaKind.fromTypeString(parameters.get(index));
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        if (accessingClass == null) {
            // Caller doesn't care about resolution context so return an unresolved
            // or primitive type (primitive type resolution is context free)
            return getUnresolvedOrPrimitiveType(returnType);
        }
        if (!checkValidCache(returnTypeCache, accessingClass)) {
            JavaType result = lookupType(returnType, (EspressoResolvedInstanceType) accessingClass);
            if (result instanceof EspressoResolvedJavaType) {
                returnTypeCache = (EspressoResolvedJavaType) result;
            } else {
                return result;
            }
        }
        return returnTypeCache;
    }

    private static JavaType lookupType(String descriptor, EspressoResolvedInstanceType accessingClass) {
        if (descriptor.length() == 1) {
            JavaKind kind = JavaKind.fromTypeString(descriptor);
            if (kind.isPrimitive()) {
                return EspressoResolvedPrimitiveType.forKind(kind);
            }
        }
        return runtime().lookupType(descriptor, accessingClass, false);
    }

    @Override
    public JavaKind getReturnKind() {
        return JavaKind.fromTypeString(returnType);
    }

    @Override
    public String toMethodDescriptor() {
        assert rawSignature.equals(Signature.super.toMethodDescriptor()) : rawSignature + " != " + Signature.super.toMethodDescriptor();
        return rawSignature;
    }

    @Override
    public String toString() {
        return "EspressoSignature<" + rawSignature + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EspressoSignature) {
            EspressoSignature other = (EspressoSignature) obj;
            if (other.rawSignature.equals(rawSignature)) {
                assert other.parameters.equals(parameters);
                assert other.returnType.equals(returnType);
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return rawSignature.hashCode();
    }
}
