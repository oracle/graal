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

import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.spi.editor.mimelookup.MimeLocation;

import javax.script.ScriptException;
import java.util.Set;

/**
 * Represents an engine capable of executing user scripts. Engines should be registered in {@link MimeLookup} for the
 * MIME type(s) of the supported languages.
 *
 * @author sdedic
 */
@MimeLocation(subfolderName = "scriptEngine")
public interface UserScriptEngine {
    /**
     * Checks if a language is supported. Can be also used to check if the necessary libraries are available.
     *
     * @param mime mime type of the language
     * @return
     */
    public boolean acceptsLanguage(String mime);

    /**
     * @return Name of the engine, for display or diagnostic purposes.
     */
    public String name();

    /**
     * Returns a set of supported languages, in form of MIME types for the language sources.
     *
     * @return supported languages
     */
    public Set<String> supportedLanguages();

    /**
     * Prepares (compiles ?) a snippet, possibly raising compilation-time errors. If successful, returns a {@link PreparedScript}
     * which can be executed repeatedly. Depending on implementation, the script code may need to be wrapped so that
     * parameters are correctly passed to it. Parameter names must be specified when preparing a snippet.
     * <p/>
     * Note that when "environment" is used to provide basis for a script, "globalSymbols" may be ignored by the engine,
     * as the environment is already set up.
     *
     * @return snippet prepared for execution
     * @throws ScriptException
     */
    public PreparedScript prepare(ScriptEnvironment env, ScriptDefinition def) throws ScriptException;
}
