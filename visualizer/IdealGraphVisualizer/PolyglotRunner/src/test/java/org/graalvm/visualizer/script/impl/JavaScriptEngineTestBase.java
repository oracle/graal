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
package org.graalvm.visualizer.script.impl;

import org.graalvm.visualizer.script.PreparedScript;
import org.graalvm.visualizer.script.ScriptDefinition;
import org.graalvm.visualizer.script.ScriptEnvironment;
import org.graalvm.visualizer.script.UserScriptEngine;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author sdedic
 */
public abstract class JavaScriptEngineTestBase {
    UserScriptEngine engine;
    ScriptEnvironment env;

    @Before
    public void setUp() throws Exception {
        engine = createEngine();
        env = ScriptEnvironment.simple();
    }

    protected abstract UserScriptEngine createEngine() throws Exception;

    @Test
    public void testRunScript() {
        if (engine == null) {
            return;
        }
        assertTrue(engine.acceptsLanguage("text/javascript"));
    }

    @Test
    public void testRunScriptBody() throws Exception {
        if (engine == null) {
            return;
        }
        ScriptDefinition def =
                new ScriptDefinition("text/javascript").
                        filename("whatever").
                        code("1");

        PreparedScript prep = engine.prepare(env, def);
        Object v = prep.evaluate(Collections.emptyMap());
        assertEquals(Integer.valueOf(1), v);
    }

    @Test
    @Ignore
    public void testRunScriptBodyWithParameters() throws Exception {
        if (engine == null) {
            return;
        }
        ScriptDefinition def =
                new ScriptDefinition("text/javascript").
                        filename("whatever").
                        code("return a * b").
                        parameterNames(Arrays.asList("a", "b"));

        PreparedScript prep = engine.prepare(env, def);
        Map<String, Object> m = new HashMap<>();
        m.put("a", 2);
        m.put("b", 3);
        Object v = prep.evaluate(m);
        assertEquals((double) Double.valueOf(6), (double) ((Number) v).doubleValue(), 0.1);
    }

    @Test
    @Ignore
    public void testRunScriptBodyWithGlobals() throws Exception {
        if (engine == null) {
            return;
        }
        ScriptDefinition def =
                new ScriptDefinition("text/javascript").
                        filename("whatever").
                        code("a * b").
                        global("a", 2).
                        global("b", 3);

        PreparedScript prep = engine.prepare(env, def);
        Map<String, Object> m = new HashMap<>();
        m.put("a", 2);
        m.put("b", 3);
        Object v = prep.evaluate(Collections.emptyMap());
        assertEquals((double) Double.valueOf(6), (double) ((Number) v).doubleValue(), 0.1);
    }
}
