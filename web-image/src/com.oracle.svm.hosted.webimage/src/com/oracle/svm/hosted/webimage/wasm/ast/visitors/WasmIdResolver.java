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

package com.oracle.svm.hosted.webimage.wasm.ast.visitors;

import java.util.Collection;
import java.util.Collections;

import com.oracle.svm.hosted.webimage.wasm.ast.id.ResolverContext;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;

/**
 * Resolves all {@link WasmId}s in the module using a {@link ResolverContext}.
 */
public class WasmIdResolver extends WasmVisitor {

    protected final ResolverContext context;

    /**
     * Stores all {@link WasmId}s we expect to find in the module.
     * <p>
     * Finding an unresolved id not in this collection is a bug.
     */
    protected final Collection<WasmId> expectedIds;

    public WasmIdResolver(ResolverContext ctxt, WasmIdFactory idFactory) {
        this.context = ctxt;
        this.expectedIds = Collections.unmodifiableCollection(idFactory.allIds());
    }

    @Override
    protected void visitId(WasmId id) {
        if (id == null) {
            return;
        }

        if (!id.isResolved()) {
            assert expectedIds.contains(id) : "Id was not created in factory: " + id;
            id.resolve(context);
        }

        super.visitId(id);
    }
}
