/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.junit.Assert;
import org.junit.Test;

public class ProcessEnvironmentTest extends AbstractPolyglotTest {

    @Test
    public void testEnvironmentAccessNone() {
        setupEnv(Context.newBuilder().allowEnvironmentAccess(EnvironmentAccess.NONE).build());
        Map<String, String> env = languageEnv.getEnvironment();
        Assert.assertTrue(env.isEmpty());
        try {
            env.put("k1", "v1");
            Assert.fail("Environment map must be unmodifiable.");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        setupEnv(Context.newBuilder().allowEnvironmentAccess(EnvironmentAccess.NONE).environment("k1", "v1").environment("k2", "v2").build());
        env = languageEnv.getEnvironment();
        Map<String, String> expected = new HashMap<>();
        expected.put("k1", "v1");
        expected.put("k2", "v2");
        Assert.assertEquals(expected, env);
        setupEnv(Context.newBuilder().allowEnvironmentAccess(EnvironmentAccess.NONE).environment(expected).build());
        env = languageEnv.getEnvironment();
        Assert.assertEquals(expected, env);
    }

    @Test
    public void testEnvironmentInherit() {
        setupEnv(Context.newBuilder().allowEnvironmentAccess(EnvironmentAccess.INHERIT).build());
        Map<String, String> env = languageEnv.getEnvironment();
        Map<String, String> expected = System.getenv();
        Assert.assertEquals(expected, env);
        try {
            env.put("k1", "v1");
            Assert.fail("Environment map must be unmodifiable.");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        setupEnv(Context.newBuilder().allowEnvironmentAccess(EnvironmentAccess.INHERIT).environment("k1", "v1").environment("k2", "v2").build());
        env = languageEnv.getEnvironment();
        Map<String, String> contextEnv = new HashMap<>();
        contextEnv.put("k1", "v1");
        contextEnv.put("k2", "v2");
        expected = new HashMap<>(System.getenv());
        expected.putAll(contextEnv);
        Assert.assertEquals(expected, env);
        setupEnv(Context.newBuilder().allowEnvironmentAccess(EnvironmentAccess.INHERIT).environment(contextEnv).build());
        env = languageEnv.getEnvironment();
        Assert.assertEquals(expected, env);
    }
}
