/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.ImplicitExplicitExportTest.Ctx;
import static com.oracle.truffle.api.vm.ImplicitExplicitExportTest.L1;
import static com.oracle.truffle.api.vm.ImplicitExplicitExportTest.L1_ALT;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import static org.junit.Assert.assertSame;

public class EngineTest {
    protected PolyglotEngine.Builder createBuilder() {
        return PolyglotEngine.newBuilder();
    }

    @Test
    public void npeWhenCastingAs() throws Exception {
        PolyglotEngine tvm = createBuilder().build();

        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Language language2 = tvm.getLanguages().get("application/x-test-import-export-2");
        language2.eval(Source.fromText("explicit.value=42", "define 42"));

        PolyglotEngine.Value value = language1.eval(Source.fromText("return=value", "42.value"));
        String res = value.as(String.class);
        assertNotNull(res);
    }

    @Test
    public void checkCachingOfNodes() throws IOException {
        PolyglotEngine vm1 = createBuilder().build();
        PolyglotEngine vm2 = createBuilder().build();

        PolyglotEngine.Language language1 = vm1.getLanguages().get("application/x-test-hash");
        PolyglotEngine.Language language2 = vm2.getLanguages().get("application/x-test-hash");
        PolyglotEngine.Language alt1 = vm1.getLanguages().get("application/x-test-hash-alt");
        PolyglotEngine.Language alt2 = vm2.getLanguages().get("application/x-test-hash-alt");
        final Source sharedSource = Source.fromText("anything", "something");

        Object hashIn1Round1 = language1.eval(sharedSource).get();
        Object hashIn2Round1 = language2.eval(sharedSource).get();
        Object hashIn1Round2 = language1.eval(sharedSource).get();
        Object hashIn2Round2 = language2.eval(sharedSource).get();

        Object altIn1Round1 = alt1.eval(sharedSource).get();
        Object altIn2Round1 = alt2.eval(sharedSource).get();
        Object altIn1Round2 = alt1.eval(sharedSource).get();
        Object altIn2Round2 = alt2.eval(sharedSource).get();

        assertEquals("Two executions in 1st engine share the nodes", hashIn1Round1, hashIn1Round2);
        assertEquals("Two executions in 2nd engine share the nodes", hashIn2Round1, hashIn2Round2);

        assertEquals("Two alternative executions in 1st engine share the nodes", altIn1Round1, altIn1Round2);
        assertEquals("Two alternative executions in 2nd engine share the nodes", altIn2Round1, altIn2Round2);

        assertNotEquals("Two executions in different languages don't share the nodes", hashIn1Round1, altIn1Round1);
        assertNotEquals("Two executions in different languages don't share the nodes", hashIn1Round1, altIn2Round1);
        assertNotEquals("Two executions in different languages don't share the nodes", hashIn2Round2, altIn1Round2);
        assertNotEquals("Two executions in different languages don't share the nodes", hashIn2Round2, altIn2Round2);

        assertNotEquals("Two executions in different engines don't share the nodes", hashIn1Round1, hashIn2Round1);
        assertNotEquals("Two executions in different engines don't share the nodes", hashIn2Round2, hashIn1Round2);
    }

    protected Thread forbiddenThread() {
        return null;
    }

    private interface AccessArray {
        AccessArray dupl();

        List<? extends Number> get(int index);
    }

    @Test
    public void wrappedAsArray() throws Exception {
        Object[][] matrix = {{1, 2, 3}};

        PolyglotEngine tvm = createBuilder().globalSymbol("arr", new ArrayTruffleObject(matrix, forbiddenThread())).build();
        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        AccessArray access = language1.eval(Source.fromText("return=arr", "get the array")).as(AccessArray.class);
        assertNotNull("Array converted to list", access);
        access = access.dupl();
        List<? extends Number> list = access.get(0);
        assertEquals("Size 3", 3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
        Integer[] arr = list.toArray(new Integer[0]);
        assertEquals("Three items in array", 3, arr.length);
        assertEquals(1, arr[0].intValue());
        assertEquals(2, arr[1].intValue());
        assertEquals(3, arr[2].intValue());
    }

    @Test
    public void engineConfigBasicAccess() throws IOException {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-1", "cmd-line-args", new String[]{"1", "2"});
        builder.config("application/x-test-import-export-2", "hello", "world");
        PolyglotEngine vm = builder.build();

        PolyglotEngine.Language language1 = vm.getLanguages().get("application/x-test-import-export-1");

        assertNotNull("Lang1 found", language1);

        Ctx ctx1 = language1.getGlobalObject().as(Ctx.class);
        String[] args = (String[]) ctx1.env.getConfig().get("cmd-line-args");
        assertNotNull("Founds args", args);

        assertEquals("1", args[0]);
        assertEquals("2", args[1]);

        assertNull("Can't see settings for other language", ctx1.env.getConfig().get("hello"));

        PolyglotEngine.Language language2 = vm.getLanguages().get("application/x-test-import-export-2");
        assertNotNull("Lang2 found", language2);

        Ctx ctx2 = language2.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
        assertNull("Cannot find args", ctx2.env.getConfig().get("cmd-line-args"));
    }

    @Test
    public void engineConfigShouldBeReadOnly() throws IOException {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-1", "cmd-line-args", new String[]{"1", "2"});
        builder.config("application/x-test-import-export-2", "hello", "world");
        PolyglotEngine vm = builder.build();
        PolyglotEngine.Language language1 = vm.getLanguages().get("application/x-test-import-export-1");
        Ctx ctx1 = language1.getGlobalObject().as(Ctx.class);

        // make sure configuration is read-only
        try {
            ctx1.env.getConfig().put("hi", "there!");
            fail("The map should be readonly");
        } catch (UnsupportedOperationException ex) {
            // OK
        }
    }

    @Test
    public void secondValueWins() throws IOException {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-2", "hello", "truffle");
        builder.config("application/x-test-import-export-2", "hello", "world");
        PolyglotEngine vm = builder.build();

        PolyglotEngine.Language language2 = vm.getLanguages().get("application/x-test-import-export-2");
        Ctx ctx2 = language2.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void secondValueWins2() throws IOException {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-2", "hello", "world");
        builder.config("application/x-test-import-export-2", "hello", "truffle");
        PolyglotEngine vm = builder.build();

        PolyglotEngine.Language language2 = vm.getLanguages().get("application/x-test-import-export-2");
        Ctx ctx2 = language2.getGlobalObject().as(Ctx.class);
        assertEquals("truffle", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void altValueWins() throws IOException {
        Builder builder = createBuilder();
        builder.config(L1, "hello", "truffle");
        builder.config(L1_ALT, "hello", "world");
        PolyglotEngine vm = builder.build();

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void altValueWins2() throws IOException {
        Builder builder = createBuilder();
        builder.config(L1_ALT, "hello", "truffle");
        builder.config(L1, "hello", "world");
        PolyglotEngine vm = builder.build();

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void configIsNeverNull() throws IOException {
        Builder builder = createBuilder();
        PolyglotEngine vm = builder.build();

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        assertNull(ctx2.env.getConfig().get("hello"));
    }

    static class YourLang {
        public static final String MIME_TYPE = L1;
    }

    @Test
    public void exampleOfConfiguration() throws IOException {
        // @formatter:off
        // BEGIN: config.specify
        String[] args = {"--kernel", "Kernel.som", "--instrument", "dyn-metrics"};
        Builder builder = PolyglotEngine.newBuilder();
        builder.config(YourLang.MIME_TYPE, "CMD_ARGS", args);
        PolyglotEngine vm = builder.build();
        // END: config.specify
        // @formatter:on

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        String[] read = (String[]) ctx2.env.getConfig().get("CMD_ARGS");

        assertSame("The same array as specified is returned", args, read);
    }
}
