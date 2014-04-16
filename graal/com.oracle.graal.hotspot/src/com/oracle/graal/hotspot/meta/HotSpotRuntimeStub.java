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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.stubs.*;

/**
 * Implementation of {@link InstalledCode} for code installed as a RuntimeStub.
 */
public class HotSpotRuntimeStub extends HotSpotInstalledCode {

    private static final long serialVersionUID = -6388648408298441748L;

    private final Stub stub;

    public HotSpotRuntimeStub(Stub stub) {
        this.stub = stub;
    }

    public ResolvedJavaMethod getMethod() {
        return null;
    }

    public boolean isValid() {
        return true;
    }

    public void invalidate() {
    }

    @Override
    public String toString() {
        return String.format("InstalledRuntimeStub[stub=%s, codeBlob=0x%x]", stub, getCodeBlob());
    }

    public Object executeVarargs(Object... args) throws InvalidInstalledCodeException {
        throw new GraalInternalError("Cannot call stub %s", stub);
    }
}
