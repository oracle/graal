/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.api.meta.MetaUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.truffle.api.*;

/**
 * Enables a Truffle compilable to masquerade as a {@link JavaMethod} for use as a context value in
 * {@linkplain Debug#scope(String, Object...) debug scopes}.
 */
public class TruffleDebugJavaMethod implements JavaMethod {
    private final RootCallTarget compilable;

    private static final JavaType declaringClass = new JavaType() {

        public String getName() {
            return "LTruffle;";
        }

        public JavaType getComponentType() {
            return null;
        }

        public JavaType getArrayClass() {
            throw new UnsupportedOperationException();
        }

        public Kind getKind() {
            return Kind.Object;
        }

        public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object obj) {
            return obj.getClass() == getClass();
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }
    };

    private static final Signature signature = new Signature() {

        public JavaType getReturnType(ResolvedJavaType accessingClass) {
            return declaringClass;
        }

        public Kind getReturnKind() {
            return declaringClass.getKind();
        }

        public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
            throw new IndexOutOfBoundsException();
        }

        public int getParameterSlots(boolean withReceiver) {
            return 0;
        }

        public Kind getParameterKind(int index) {
            throw new IndexOutOfBoundsException();
        }

        public int getParameterCount(boolean receiver) {
            return 0;
        }
    };

    public TruffleDebugJavaMethod(RootCallTarget compilable) {
        this.compilable = compilable;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TruffleDebugJavaMethod) {
            TruffleDebugJavaMethod other = (TruffleDebugJavaMethod) obj;
            return other.compilable.equals(compilable);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return compilable.hashCode();
    }

    public Signature getSignature() {
        return signature;
    }

    public String getName() {
        return compilable.toString().replace('.', '_').replace(' ', '_');
    }

    public JavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String toString() {
        return format("Truffle<%n(%p)>", this);
    }
}
