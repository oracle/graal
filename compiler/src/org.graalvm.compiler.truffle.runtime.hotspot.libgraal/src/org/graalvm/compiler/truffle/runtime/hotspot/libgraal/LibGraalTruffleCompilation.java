/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilation;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;

final class LibGraalTruffleCompilation extends LibGraalObject implements TruffleCompilation {

    private final LibGraalHotSpotTruffleCompiler owner;
    private volatile CompilableTruffleAST cachedCompilableTruffleAST;
    private volatile String cachedId;
    private final LibGraalScope scope;

    LibGraalTruffleCompilation(LibGraalHotSpotTruffleCompiler owner, long handle, LibGraalScope scope) {
        super(handle);
        this.owner = owner;
        this.scope = scope;
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        CompilableTruffleAST compilable = cachedCompilableTruffleAST;
        if (compilable == null) {
            compilable = TruffleToLibGraalCalls.getTruffleCompilationTruffleAST(getIsolateThread(), getHandle());
            cachedCompilableTruffleAST = compilable;
        }
        return compilable;
    }

    @Override
    public void close() {
        try {
            owner.closeCompilation(this);
            cachedCompilableTruffleAST = null;
        } finally {
            TruffleToLibGraalCalls.closeCompilation(getIsolateThread(), getHandle());
            scope.close();
        }
    }

    String getId() {
        String id = cachedId;
        if (id == null) {
            id = TruffleToLibGraalCalls.getTruffleCompilationId(getIsolateThread(), getHandle());
            cachedId = id;
        }
        return id;
    }
}
