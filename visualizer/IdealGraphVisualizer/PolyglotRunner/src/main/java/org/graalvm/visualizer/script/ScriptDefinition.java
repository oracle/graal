/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.script;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a script to be executed.
 *
 * @author sdedic
 */
public final class ScriptDefinition {
    private final List<String> paramNames = new ArrayList<>();
    private PrintWriter output;
    private PrintWriter error;
    private String scriptFilename;
    private String code;
    private final Map<String, Object> globals = new LinkedHashMap<>();
    private final String mimeType;
    private String executeFunction;
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * Constructs a definition. Specifies MIME type for the language
     *
     * @param mimeType mime type of the script.
     */
    public ScriptDefinition(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Sets up parameter names, in a particular order. Parameter names are
     * built up by {@link #addParameter}, but can be reordered by this call.
     *
     * @param n parameter names
     * @return builder instance
     */
    public ScriptDefinition parameterNames(List<String> n) {
        this.paramNames.removeAll(n);
        this.paramNames.addAll(n);
        return this;
    }

    /**
     * Sets the script's executable code
     *
     * @param s the code
     * @return builder instance
     */
    public ScriptDefinition code(String s) {
        this.code = s;
        return this;
    }

    /**
     * Adds global symbols. The script engine will try to define and assign
     * the symbols before running the script.
     *
     * @param globals
     * @return builder instance
     */
    public ScriptDefinition globals(Map<String, Object> globals) {
        this.globals.putAll(globals);
        return this;
    }

    /**
     * Redirect output of the script.
     *
     * @param output output stream
     * @return builder instance
     */
    public ScriptDefinition output(PrintWriter output) {
        this.output = output;
        return this;
    }

    /**
     * Redirect errors of the script
     *
     * @param error error stream
     * @return builder instance
     */
    public ScriptDefinition error(PrintWriter error) {
        this.error = error;
        return this;
    }

    public ScriptDefinition filename(String scriptFilename) {
        this.scriptFilename = scriptFilename;
        return this;
    }

    public ScriptDefinition setCode(String code) {
        this.code = code;
        return this;
    }

    public ScriptDefinition global(String name, Object value) {
        globals.put(name, value);
        return this;
    }

    public ScriptDefinition setParameter(String name, Object value) {
        if (!paramNames.contains(name)) {
            paramNames.add(name);
        }
        parameters.put(name, value);
        return this;
    }

    public List<String> getParamNames() {
        return Collections.unmodifiableList(paramNames);
    }

    /**
     * Returns global names, in some defined order.
     * The order does not change if a global name is added or value is rewritten.
     * Use to generate positional list of global values, i.e. for parameters of a function
     * that initializes the globals.
     *
     * @return ordered global names.
     */
    public List<String> getGlobalNames() {
        return Collections.unmodifiableList(new ArrayList<>(globals.keySet()));
    }

    public PrintWriter getOutput() {
        return output;
    }

    public PrintWriter getError() {
        return error;
    }

    public String getScriptFilename() {
        return scriptFilename;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Specifies name of user function to be executed
     *
     * @param functionName function name, possibly fully qualified
     * @return this instance
     */
    public ScriptDefinition executeFunction(String functionName) {
        executeFunction = functionName;
        return this;
    }

    public boolean isExecuteFunction() {
        return executeFunction != null;
    }

    public String getFunctionName() {
        return executeFunction;
    }

    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * Makes an independent copy of the definition.
     *
     * @return copy of this definition.
     */
    public ScriptDefinition copy() {
        ScriptDefinition res =
                new ScriptDefinition(mimeType).
                        parameterNames(paramNames).
                        code(code).
                        globals(globals).
                        output(output).
                        error(error).
                        filename(scriptFilename).
                        executeFunction(executeFunction);
        for (String s : paramNames) {
            if (parameters.containsKey(s)) {
                res.setParameter(s, parameters.get(s));
            }
        }
        return res;
    }
}
