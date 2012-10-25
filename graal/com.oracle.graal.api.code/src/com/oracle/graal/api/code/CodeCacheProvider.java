/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import com.oracle.graal.api.code.RuntimeCall.Descriptor;
import com.oracle.graal.api.meta.*;

/**
 * Encapsulates the main functionality of the runtime for the compiler.
 */
public interface CodeCacheProvider extends MetaAccessProvider {

    /**
     * Adds the given compilation result as an implementation of the given method without making it the default
     * implementation.
     *
     * @param method a method to which the executable code is begin added
     * @param compResult the compilation result to be added
     * @param info the object into which details of the installed code will be written. Ignored if null, otherwise the
     *            info is written to index 0 of this array.
     * @return a reference to the compiled and ready-to-run code
     */
    InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult, CodeInfo[] info);

    /**
     * Returns the size in bytes for locking information on the stack.
     */
    int getSizeOfLockData();

    /**
     * Returns a disassembly of the given installed code.
     *
     * @param code the code that should be disassembled
     * @return a disassembly. This will be of length 0 if the runtime does not support disassembling.
     */
    String disassemble(CodeInfo code, CompilationResult tm);

    /**
     * Gets the register configuration to use when compiling a given method.
     *
     * @param method the top level method of a compilation
     */
    RegisterConfig lookupRegisterConfig(JavaMethod method);

    /**
     * Custom area on the stack of each compiled method that the VM can use for its own purposes.
     *
     * @return the size of the custom area in bytes
     */
    int getCustomStackAreaSize();

    /**
     * Determines if a call instruction in compiled code can assume all allocatable
     * registers are killed by the call.
     */
    boolean callKillsRegisters();

    /**
     * Minimum size of the stack area reserved for outgoing parameters. This area is reserved in all cases, even when
     * the compiled method has no regular call instructions.
     *
     * @return the minimum size of the outgoing parameter area in bytes
     */
    int getMinimumOutgoingSize();

    /**
     * Performs any runtime-specific conversion on the object used to describe the target of a call.
     */
    Object lookupCallTarget(Object target);

    /**
     * Gets the signature and linkage information for a runtime call.
     */
    RuntimeCall lookupRuntimeCall(Descriptor descriptor);

    /**
     * Encodes a deoptimization action and a deoptimization reason in an integer value.
     *
     * @return the encoded value as an integer
     */
    int encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason);
}
