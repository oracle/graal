/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.functionintrinsics;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.vm.ci.meta.JavaKind;

/**
 * Class for calls to pre-defined JavaScript function.
 */
public class JSSystemFunction extends JSGenericFunctionDefinition {

    /**
     * {@link JavaKind}s of the expected function arguments.
     * <p>
     * A {@code null} value means that any type is accepted.
     */
    private final JavaKind[] argKinds;

    private final Stamp stamp;

    public JSSystemFunction(String functionName, JavaKind... argKinds) {
        this(functionName, null, argKinds);
    }

    public JSSystemFunction(String functionName, Stamp stamp, JavaKind... argKinds) {
        super(functionName, argKinds.length, false, null, false);
        this.stamp = stamp == null ? StampFactory.forVoid() : stamp;
        this.argKinds = argKinds;
    }

    public Stamp stamp() {
        return stamp;
    }

    public JavaKind[] getArgKinds() {
        return argKinds;
    }
}
