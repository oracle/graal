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

import javax.script.ScriptException;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Represents a snippet prepared for execution, perhaps compiled. {@link PreparedUserScript} can be evaluated
 * with passing parameter values.
 */
public interface PreparedScript {
    /**
     * @return original code passed to {@link UserScriptEngine#prepare}.
     */
    public String getOriginalCode();

    /**
     * @return code which will be executed, including any wrappers generated around {@link #getOriginalCode}.
     */
    public String getExecutableCode();

    /**
     * Returns names of parameters.
     *
     * @return
     */
    public Iterable<String> parameterNames();

    /**
     * Executes the snippet. The caller may attempt to cancel the snippet's execution by calling {@link #cancel} and passing
     * the same handle. Each handle should be unique
     *
     * @param parameters actual parameter values
     * @return computed value
     * @throws ScriptException
     */
    public Object evaluate(Map<String, Object> parameters) throws ScriptException;

    /**
     * Request cancel of script execution, if possible. If request is accepted, the script terminates immediately, or at some later time, throwing
     * {@link CancellationException}.
     *
     * @return true, if the request was accepted (i.e. script is running).
     */
    public boolean cancel();

    public UserScriptEngine getEngine();

    public ScriptEnvironment getEnvironment();
}
