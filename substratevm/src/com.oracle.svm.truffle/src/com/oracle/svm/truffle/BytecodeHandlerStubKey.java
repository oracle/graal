/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.util.Objects;

import jdk.graal.compiler.truffle.BytecodeHandlerConfig;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Identifies a registered bytecode handler stub by the handler method, the declaring type that
 * holds the interpreter switch, and the associated {@link BytecodeHandlerConfig}.
 * <p>
 * Keys with a null method identify the interpreter itself.
 */
public final class BytecodeHandlerStubKey {

    private final ResolvedJavaMethod method;
    private final ResolvedJavaType interpreterHolder;
    private final BytecodeHandlerConfig handlerConfig;

    private BytecodeHandlerStubKey(ResolvedJavaMethod method, ResolvedJavaType interpreterHolder, BytecodeHandlerConfig handlerConfig) {
        this.method = method;
        this.interpreterHolder = Objects.requireNonNull(interpreterHolder);
        this.handlerConfig = Objects.requireNonNull(handlerConfig);
    }

    static BytecodeHandlerStubKey create(ResolvedJavaMethod method, ResolvedJavaType interpreterHolder, BytecodeHandlerConfig handlerConfig) {
        return new BytecodeHandlerStubKey(method, interpreterHolder, handlerConfig);
    }

    static BytecodeHandlerStubKey createDefaultHandlerKey(ResolvedJavaType interpreterHolder, BytecodeHandlerConfig handlerConfig) {
        return new BytecodeHandlerStubKey(null, interpreterHolder, handlerConfig);
    }

    ResolvedJavaMethod method() {
        return method;
    }

    ResolvedJavaType interpreterHolder() {
        return interpreterHolder;
    }

    BytecodeHandlerConfig handlerConfig() {
        return handlerConfig;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BytecodeHandlerStubKey other)) {
            return false;
        }
        return Objects.equals(method, other.method) && interpreterHolder.equals(other.interpreterHolder) && handlerConfig.equals(other.handlerConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, interpreterHolder, handlerConfig);
    }
}
