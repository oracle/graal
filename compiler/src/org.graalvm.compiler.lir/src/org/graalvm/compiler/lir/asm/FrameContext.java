/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.asm;

/**
 * Code for managing a method's native frame.
 */
public interface FrameContext {

    /**
     * Emits code common to all entry points of a method. This may include:
     * <ul>
     * <li>setting up the stack frame</li>
     * <li>saving callee-saved registers</li>
     * <li>stack overflow checking</li>
     * <li>adding marks to identify the frame push</li>
     * </ul>
     */
    void enter(CompilationResultBuilder crb);

    /**
     * Emits code to be executed just prior to returning from a method. This may include:
     * <ul>
     * <li>restoring callee-saved registers</li>
     * <li>performing a safepoint</li>
     * <li>destroying the stack frame</li>
     * <li>adding marks to identify the frame pop</li>
     * </ul>
     */
    void leave(CompilationResultBuilder crb);

    /**
     * Allows the frame context to track the point at which a return has been generated. This
     * callback is not intended to actually generate the return instruction itself. A legitimate
     * action in response to this call may include:
     * <ul>
     * <li>adding a mark to identify the end of an epilogue</li>
     * </ul>
     */
    void returned(CompilationResultBuilder crb);

    /**
     * Determines if a frame is set up and torn down by this object.
     */
    boolean hasFrame();
}
