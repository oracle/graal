/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * A minimal implementation of {@link Signature} for use by libgraal. It does not support any
 * dynamic resolution of types in signatures.
 *
 * @see SnippetResolvedJavaType
 */
public final class SnippetSignature implements Signature {

    private final List<String> parameters = new ArrayList<>();
    private final String returnType;
    private final String originalString;

    @NativeImageReinitialize private static EnumMap<JavaKind, ResolvedJavaType> primitiveTypes = null;

    static synchronized void initPrimitiveKindCache(MetaAccessProvider metaAccess) {
        if (primitiveTypes == null) {
            // Create the cache
            EnumMap<JavaKind, ResolvedJavaType> types = new EnumMap<>(JavaKind.class);
            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive()) {
                    types.put(kind, metaAccess.lookupJavaType(kind.toJavaClass()));
                }
            }
            // Publish it
            primitiveTypes = types;
        }
    }

    public SnippetSignature(String signature) {
        if (signature.length() == 0) {
            throw new IllegalArgumentException("Signature cannot be empty");
        }
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
            if (nextCur != signature.length()) {
                throw new IllegalArgumentException("Extra characters at end of signature: " + signature);
            }
        } else {
            throw new IllegalArgumentException("Signature must start with a '(': " + signature);
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

    @Override
    public JavaKind getParameterKind(int index) {
        return JavaKind.fromTypeString(parameters.get(index));
    }

    private static JavaType getUnresolvedOrPrimitiveType(String name) {
        if (name.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            return primitiveTypes.get(kind);
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
        throw new NoClassDefFoundError("dynamic resolution unsupported: " + parameters.get(index));
    }

    @Override
    public String toMethodDescriptor() {
        return originalString;
    }

    @Override
    public JavaKind getReturnKind() {
        return JavaKind.fromTypeString(returnType);
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        if (accessingClass == null) {
            // Caller doesn't care about resolution context so return an unresolved
            // or primitive type (primitive type resolution is context free)
            return getUnresolvedOrPrimitiveType(returnType);
        }
        throw new NoClassDefFoundError("dynamic resolution unsupported: " + returnType);
    }

    @Override
    public String toString() {
        return "SnippetSignature<" + originalString + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SnippetSignature) {
            SnippetSignature other = (SnippetSignature) obj;
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
