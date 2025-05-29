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

package com.oracle.svm.hosted.webimage.wasm.ast.id;

import com.oracle.svm.webimage.NamingConvention;

import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * TODO GR-41720
 * <ul>
 * <li>Give out non-conflicting ids for each type in the current scope.</li>
 * <li>proper name mangling for method names</li>
 * <li>Cache ids in a scope, reset scopes where necessary (e.g. end of a function)</li>
 * </ul>
 */
public class ResolverContext {

    public final NamingConvention namingConvention;

    public ResolverContext(NamingConvention namingConvention) {
        this.namingConvention = namingConvention;
    }

    public static String getLoopLabel(HIRBlock block) {
        assert block.isLoopHeader() : "Block is not a loop header: " + block.toString(Verbosity.Name);
        return "looplabel_" + block.getId();
    }
}
