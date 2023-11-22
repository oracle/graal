/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.debugging.data;

import java.util.List;

import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.objects.DebugScopeValue;
import org.graalvm.wasm.nodes.WasmDataAccess;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a function in the debug information.
 */
public class DebugFunction extends DebugType {
    private final String name;
    private final DebugLineMap lineMap;
    private final SourceSection sourceSection;
    private final byte[] frameBaseExpression;
    private final List<DebugObject> variables;
    private final List<DebugObject> globals;

    public DebugFunction(String name, DebugLineMap lineMap, SourceSection sourceSection, byte[] frameBaseExpression, List<DebugObject> variables, List<DebugObject> globals) {
        assert lineMap != null : "the source code to bytecode line map of a debug function must not be null";
        assert frameBaseExpression != null : "the expression for calculating the frame base of a debug function must not be null";
        assert variables != null : "the list of variables of a debug function must not be null";
        assert globals != null : "the list of globals of a debug function must not be null";
        this.name = name;
        this.lineMap = lineMap;
        this.sourceSection = sourceSection;
        this.frameBaseExpression = frameBaseExpression;
        this.variables = variables;
        this.globals = globals;
    }

    @Override
    public String asTypeName() {
        return "function";
    }

    @Override
    public int valueLength() {
        return 0;
    }

    /**
     * @return Whether globals are defined or not.
     */
    public boolean hasGlobals() {
        return globals.size() != 0;
    }

    /**
     * @return A scope capturing the global values of the current application.
     */
    public DebugObject globals() {
        return new DebugScopeValue("globals", globals);
    }

    /**
     * @return A scope capturing the local values of the function.
     */
    public DebugObject locals() {
        return new DebugScopeValue("locals", variables);
    }

    /**
     * @param frame the frame
     * @param dataAccess the data access
     * @return The frame base location of the function, or null if the frame base expression is
     *         malformed.
     */
    public DebugLocation frameBaseOrNull(MaterializedFrame frame, WasmDataAccess dataAccess) {
        return DebugLocation.createFrameBaseOrNull(frame, dataAccess, frameBaseExpression);
    }

    /**
     * @return The source section of the function.
     */
    public SourceSection sourceSection() {
        return sourceSection;
    }

    /**
     * @return The line map associated with the function.
     */
    public DebugLineMap lineMap() {
        return lineMap;
    }

    /**
     * @return The name of the function.
     */
    public String name() {
        return name;
    }
}
