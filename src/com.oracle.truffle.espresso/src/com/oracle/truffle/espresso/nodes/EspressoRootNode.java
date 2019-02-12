/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * The root of all executable bits in Espresso, includes everything that can be called a "method" in
 * Java. Regular (concrete) Java methods, native methods and intrinsics/substitutions.
 */
public abstract class EspressoRootNode extends RootNode implements ContextAccess {
    // TODO(peterssen): This could be ObjectKlass bar array methods. But those methods could be
    // redirected e.g. arrays .clone method to Object.clone.
    // private final /* ObjectKlass */ Klass declaringKlass;
    private final Method method;

    public final Method getMethod() {
        return method;
    }

    protected EspressoRootNode(Method method) {
        super(method.getEspressoLanguage());
        this.method = method;
    }

    protected EspressoRootNode(Method method, FrameDescriptor frameDescriptor) {
        super(method.getEspressoLanguage(), frameDescriptor);
        this.method = method;
    }

    @Override
    public EspressoContext getContext() {
        return method.getContext();
    }

    @Override
    public String getName() {
        return getMethod().getDeclaringKlass().getType() + "." + getMethod().getName() + getMethod().getRawSignature();
    }
}
