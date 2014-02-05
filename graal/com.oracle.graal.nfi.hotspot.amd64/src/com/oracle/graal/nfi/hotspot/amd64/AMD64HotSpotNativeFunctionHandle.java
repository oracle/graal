/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nfi.hotspot.amd64;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nfi.hotspot.amd64.util.*;
import com.oracle.graal.nodes.*;

public class AMD64HotSpotNativeFunctionHandle implements NativeFunctionHandle {

    private final InstalledCode code;
    private final String functionName;

    protected final HotSpotProviders providers;
    protected final Backend backend;

    public AMD64HotSpotNativeFunctionHandle(HotSpotProviders providers, Backend backend, AMD64HotSpotNativeFunctionPointer functionPointer, Class returnType, Class[] argumentTypes) {
        this.providers = providers;
        this.backend = backend;
        this.functionName = functionPointer.getFunctionName();
        StructuredGraph graph = NativeCallStubGraphBuilder.getGraph(providers, functionPointer, returnType, argumentTypes);
        InstallUtil installer = new InstallUtil(providers, backend);
        this.code = installer.install(graph);
    }

    @Override
    public Object call(Object[] args) {
        try {
            return code.execute(args, null, null);
        } catch (InvalidInstalledCodeException e) {
            throw GraalInternalError.shouldNotReachHere("Execution of GNFI Callstub failed: " + functionName);
        }
    }

    public InstalledCode getCallStub() {
        return code;
    }
}
