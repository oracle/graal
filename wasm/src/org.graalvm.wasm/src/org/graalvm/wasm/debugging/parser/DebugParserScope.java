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
package org.graalvm.wasm.debugging.parser;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.wasm.debugging.data.DebugObject;

/**
 * Representation of a function scope during debug information parsing.
 */
public final class DebugParserScope {
    private final int fileIndex;
    private final String name;
    private final int startLocation;
    private final int endLocation;
    private final List<DebugObject> variables;

    private DebugParserScope(String name, int startLocation, int endLocation, int fileIndex, List<DebugObject> variables) {
        assert variables != null : "the list of variables in a debug parser scope must not be null";
        this.name = name;
        this.fileIndex = fileIndex;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.variables = variables;
    }

    public static DebugParserScope createFunctionScope(int startLocation, int endLocation, int fileIndex) {
        return new DebugParserScope(null, startLocation, endLocation, fileIndex, new ArrayList<>());
    }

    public static DebugParserScope createGlobalScope() {
        return new DebugParserScope(null, -1, -1, -1, new ArrayList<>());
    }

    /**
     * Creates a duplicate of the scope with an updated name. This can be used to enter a namespace.
     * Uses the same references to variables as the outer scope. If a scope with a custom set of
     * variables should be created, see {@link #createFunctionScope(int, int, int)}.
     * 
     * @param newName the new name
     */
    public DebugParserScope with(String newName) {
        return new DebugParserScope(newName, startLocation, endLocation, fileIndex, variables);
    }

    /**
     * Creates a duplicate of the scope with updated data. This can be used to enter a lexical
     * block. Uses the same references to variables as the outer scope. If a scope with a custom set
     * of variables should be created, see {@link #createFunctionScope(int, int, int)}.
     * 
     * @param newName the new name
     * @param newStartLocation the new scope start source code location
     * @param newEndLocation the new scope end source code location
     */
    public DebugParserScope with(String newName, int newStartLocation, int newEndLocation) {
        return new DebugParserScope(newName, newStartLocation, newEndLocation, fileIndex, variables);
    }

    /**
     * @return the name of the current scope.
     */
    public String nameOrNull() {
        return name;
    }

    /**
     * @return the start source code location of the current scope.
     */
    @SuppressWarnings("unused")
    public int startLocation() {
        return startLocation;
    }

    /**
     * @return the end source code location of the current scope.
     */
    public int endLocation() {
        return endLocation;
    }

    /**
     * @return the file index of the current scope.
     */
    public int fileIndex() {
        return fileIndex;
    }

    /**
     * Adds a variable to the current scope.
     * 
     * @param variable the variable
     */
    public void addVariable(DebugObject variable) {
        variables.add(variable);
    }

    /**
     * @return A list of all variables of this scope.
     */
    public List<DebugObject> variables() {
        return this.variables;
    }
}
