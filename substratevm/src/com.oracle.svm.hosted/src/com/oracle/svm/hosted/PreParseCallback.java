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
package com.oracle.svm.hosted;

import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Callback executed at the start of method parsing. It is executed before any bytecode parser
 * invocation plugin, and can as such be used to collect information from the method that the
 * plugins may rely on.
 */
public interface PreParseCallback {

    /**
     * Execute the callback for {@code method}. {@code IntrinsicContext} is null if no intrinsic is
     * currently being processed.
     */
    void execute(ResolvedJavaMethod method, IntrinsicContext intrinsicContext);
}
