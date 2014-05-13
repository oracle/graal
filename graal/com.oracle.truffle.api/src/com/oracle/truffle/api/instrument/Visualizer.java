/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Visualization services for the benefit of instrumentation-based tools, possibly specialized for
 * each guest language and possibly specialized for relevant information from the underlying Truffle
 * implementation.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 */
public interface Visualizer {

    /**
     * Gets a printer for Truffle ASTs, possibly specialized to be helpful for a specific guest
     * language implementation.
     */
    ASTPrinter getASTPrinter();

    /**
     * A short description of a source location in terms of source + line number.
     */
    String displaySourceLocation(Node node);

    /**
     * Describes the name of the method containing a node.
     */
    String displayMethodName(Node node);

    /**
     * The name of the method.
     */
    String displayCallTargetName(CallTarget callTarget);

    /**
     * Converts a value in the guest language to a display string.
     */
    String displayValue(Object value);

    /**
     * Converts a slot identifier in the guest language to a display string.
     */
    String displayIdentifier(FrameSlot slot);

}
