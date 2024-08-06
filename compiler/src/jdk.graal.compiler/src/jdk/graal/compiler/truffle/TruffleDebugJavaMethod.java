/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Enables a Truffle compilable to masquerade as a {@link JavaMethod} for use as a context value in
 * debug scopes.
 */
public final class TruffleDebugJavaMethod implements JavaMethod {
    private final TruffleCompilable compilable;
    private final TruffleCompilationTask task;

    private static final int NUMBER_OF_TIERS = 3;
    private static final TruffleJavaType[] TIERS;

    static {
        TIERS = new TruffleJavaType[NUMBER_OF_TIERS];
        for (int i = 0; i < NUMBER_OF_TIERS; i++) {
            TIERS[i] = new TruffleJavaType(i);
        }
    }

    private static class TruffleJavaType implements JavaType {

        private final String name;

        final Signature signature = new Signature() {

            @Override
            public JavaType getReturnType(ResolvedJavaType accessingClass) {
                return TruffleJavaType.this;
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

        TruffleJavaType(int tier) {
            this.name = "LTruffleIR/Tier" + tier + ";";
        }

        @Override
        public String getName() {
            return name;
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
            if (obj instanceof TruffleJavaType t) {
                return this.getName().equals(t.getName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

    }

    public TruffleDebugJavaMethod(TruffleCompilationTask task, TruffleCompilable compilable) {
        this.compilable = compilable;
        this.task = task;
    }

    public TruffleCompilable getCompilable() {
        return compilable;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TruffleDebugJavaMethod other) {
            return compilable.equals(other.compilable);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return compilable.hashCode();
    }

    @Override
    public Signature getSignature() {
        return TIERS[task.tier()].signature;
    }

    @Override
    public String getName() {
        return (compilable.getName() + "").replace('.', '_').replace(' ', '_');
    }

    @Override
    public TruffleJavaType getDeclaringClass() {
        return TIERS[task.tier()];
    }

    @Override
    public String toString() {
        return format("Truffle<%n(%p)>");
    }
}
