/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Enables a Truffle compilable to masquerade as a {@link JavaMethod} for use as a context value in
 * debug scopes.
 */
public class TruffleDebugJavaMethod implements JavaMethod {
    private final CompilableTruffleAST compilable;

    private static final JavaType declaringClass = new JavaType() {

        @Override
        public String getName() {
            return "LTruffleGraal;";
        }

        @Override
        public JavaType getComponentType() {
            return null;
        }

        @Override
        public JavaType getArrayClass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaKind getJavaKind() {
            return JavaKind.Object;
        }

        @Override
        public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TruffleDebugJavaMethod;
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }
    };

    private static final Signature signature = new Signature() {

        @Override
        public JavaType getReturnType(ResolvedJavaType accessingClass) {
            return declaringClass;
        }

        @Override
        public int getParameterCount(boolean receiver) {
            return 0;
        }

        @Override
        public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
            throw new IndexOutOfBoundsException();
        }
    };

    public TruffleDebugJavaMethod(CompilableTruffleAST compilable) {
        this.compilable = compilable;
    }

    public CompilableTruffleAST getCompilable() {
        return compilable;
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

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public String getName() {
        return (compilable.toString() + "").replace('.', '_').replace(' ', '_');
    }

    @Override
    public JavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String toString() {
        return format("Truffle<%n(%p)>");
    }
}
