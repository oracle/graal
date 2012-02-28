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
package com.oracle.max.graal.hotspot.ri;

import java.lang.reflect.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.Compiler;

/**
 * Implementation of RiCompiledMethod for HotSpot. Stores a reference to the nmethod which contains the compiled code.
 */
public class HotSpotCompiledMethod extends CompilerObject implements RiCompiledMethod {

    private static final long serialVersionUID = 156632908220561612L;

    private final RiResolvedMethod method;
    private long nmethod;

    public HotSpotCompiledMethod(Compiler compiler, RiResolvedMethod method) {
        super(compiler);
        this.method = method;
    }

    @Override
    public RiResolvedMethod method() {
        return method;
    }

    @Override
    public boolean isValid() {
        return nmethod != 0;
    }

    @Override
    public String toString() {
        return "compiled method " + method + " @" + nmethod;
    }

    @Override
    public Object execute(Object arg1, Object arg2, Object arg3) {
        assert method.signature().argumentCount(!Modifier.isStatic(method.accessFlags())) == 3;
        assert method.signature().argumentKindAt(0, false) == CiKind.Object;
        assert method.signature().argumentKindAt(1, false) == CiKind.Object;
        assert !Modifier.isStatic(method.accessFlags()) || method.signature().argumentKindAt(2, false) == CiKind.Object;
        return compiler.getVMEntries().executeCompiledMethod(this, arg1, arg2, arg3);
    }
}
