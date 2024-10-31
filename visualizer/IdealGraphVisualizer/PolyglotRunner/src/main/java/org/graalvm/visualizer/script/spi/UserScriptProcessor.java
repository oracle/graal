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
package org.graalvm.visualizer.script.spi;

import org.graalvm.visualizer.script.ScriptDefinition;

/**
 * A language may require a specific form of preparation of the user-code script
 * before it is executed. For example Javascript needs to reference named parameters,
 * so that the entire user script is wrapped into a function which names the parameters.
 * <p/>
 * The implementation may add any necessary library inclusions etc. The result will
 * be processed by the script engine.
 * <p>
 * <p/>
 * The processor should be registered against a specific MIME type of the language
 *
 * @author sdedic
 */
public interface UserScriptProcessor {
    /**
     * Provides script code to assign global symbols, based on the ScriptDefinition.
     * The call should set up global environment, if necessary, using values bound in the
     * {@link ScriptDefinition} parameter. The result should be an executable
     * - e.g. a function object for javascript.
     * <p/>
     * The returned code will be executed <b>before</b> the actual user script. Global
     * values will be passed in the order defined by {@link ScriptDefinition#getGlobalNames()}.
     *
     * @return Script code (function object) to be executed.
     */
    public String assignGlobals(ScriptDefinition def);

    /**
     * Allows to transform user code before execution. The implementation may inject
     * pre/post operations around the user code. The returned value is used <b>instead
     * of</b> the original user code.
     * <p/>
     * If the user code should be executed as a function, then the language support must
     * ensure that the full script source could be executed as a function object (in JS)
     * or equivalent, passing parameters in the order of {@link ScriptDefinition#getParamNames()}.
     *
     * @param def  the script definition
     * @param code the user code for decoration / transformation
     * @return replacement code
     */
    public String processUserCode(ScriptDefinition def, String code);
}
