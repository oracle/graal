/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.binary.constants;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Constants that denote the resolution state of the global variable.
 *
 * The resolution state tracks the state of the global variables that were either imported,
 * or initialized with another imported global variable.
 */
public enum GlobalResolution {
    /**
     * The global variable was declared inside the module.
     * It can be used in the running program.
     */
    DECLARED(true, false),

    /**
     * The global variable was imported from another module, and resolved.
     * It can be used in the running program.
     */
    IMPORTED(true, true),

    /**
     * The global variable was declared with an initializer
     * that points to an imported global variable.
     * It cannot be used until becoming resolved.
     */
    UNRESOLVED_GET(false, false),

    /**
     * The global variable was imported, but not yet resolved.
     * It cannot be used until becoming resolved.
     */
    UNRESOLVED_IMPORT(false, true);

    public static final GlobalResolution[] VALUES = GlobalResolution.values();

    @CompilationFinal private final boolean isResolved;
    @CompilationFinal private final boolean isImported;

    GlobalResolution(boolean isResolved, boolean isImported) {
        this.isResolved = isResolved;
        this.isImported = isImported;
    }

    public boolean isResolved() {
        return isResolved;
    }

    public boolean isImported() {
        return isImported;
    }
}
