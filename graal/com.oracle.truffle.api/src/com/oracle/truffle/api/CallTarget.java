/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import com.oracle.truffle.api.frame.*;

/**
 * Represents the target of a call.
 */
public abstract class CallTarget {

    /**
     * Calls this target as a root method and without arguments.
     * 
     * @return the return result of the call
     */
    public final Object call() {
        return call(null, Arguments.EMPTY_ARGUMENTS);
    }

    /**
     * Calls this target with a caller frame and no arguments.
     * 
     * @param caller the caller frame
     * @return the return result of the call
     */
    public final Object call(PackedFrame caller) {
        return call(caller, Arguments.EMPTY_ARGUMENTS);
    }

    /**
     * Calls this target as a root method passing arguments.
     * 
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     */
    public final Object call(Arguments arguments) {
        return call(null, arguments);
    }

    /**
     * Calls this target passing a caller frame and arguments.
     * 
     * @param caller the caller frame
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     */
    public abstract Object call(PackedFrame caller, Arguments arguments);
}
