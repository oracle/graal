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
package com.oracle.max.graal.compiler.observer;

import com.oracle.max.graal.compiler.*;

/**
 * Interface for classes that observe events of an {@link ObservableCompiler}.
 */
public interface CompilationObserver {

    /**
     * Called when compilation of a method has started. This is always the first event raised for a particular
     * {@link GraalCompilation}.
     *
     * @param compilation Current state of the compilation.
     */
    void compilationStarted(GraalCompilation compilation);

    /**
     * Called when an event has occurred, for example that a particular phase in the compilation has been entered.
     *
     * @param event Information associated with the event and current state of the compilation.
     */
    void compilationEvent(CompilationEvent event);

    /**
     * Called when compilation of a method has completed (successfully or not). This is always the last event raised for
     * a particular {@link GraalCompilation}.
     *
     * @param compilation Current state of the compilation.
     */
    void compilationFinished(GraalCompilation compilation);

}
