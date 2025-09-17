/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;

/**
 * A holder and factory for compiler-interface proxies during a recorded/replayed compilation.
 */
public interface CompilationProxies {
    /**
     * Proxifies an instance of a registered compiler-interface class. In general, the returned
     * proxy either records or replays the results of method invocations based on the argument
     * values. The registered classes are declared by {@link CompilerInterfaceDeclarations}
     * ({@link #getDeclarations()}). The behavior of the proxy is also defined by
     * {@link CompilerInterfaceDeclarations}.
     *
     * @param input the instance for which a proxy should be created
     * @return the proxy object
     */
    CompilationProxy proxify(Object input);

    /**
     * Gets the compiler interface declarations. This class defines for which classes proxies should
     * be created and the method invocation behavior of these proxies.
     *
     * @return compiler interface declarations
     */
    CompilerInterfaceDeclarations getDeclarations();

    /**
     * Enters the context of a snippet compilation.
     *
     * @return a debug closeable object representing the snippet context
     */
    DebugCloseable enterSnippetContext();

    /**
     * Gets the target platform (the host platform during recording and the compilation target
     * during replay).
     */
    Platform targetPlatform();

    /**
     * Temporarily sets a debug context.
     *
     * @param debugContext the debug context to enter
     * @return a debug closeable object representing the debug context
     */
    DebugCloseable withDebugContext(DebugContext debugContext);

    /**
     * Enters the context of a method compilation.
     *
     * @return a scope for the context
     */
    DebugCloseable enterCompilationContext();
}
