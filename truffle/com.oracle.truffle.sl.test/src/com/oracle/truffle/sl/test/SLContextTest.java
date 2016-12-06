/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLContext;

@SuppressWarnings("deprecation")
public class SLContextTest {

    private static final Object UNASSIGNED = new Object();

    private Throwable ex;
    protected PolyglotEngine engine;
    protected final ByteArrayOutputStream out = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Before
    public void before() {
        engine = PolyglotEngine.newBuilder().setOut(out).setErr(err).build();
    }

    @After
    public void dispose() {
        if (engine != null) {
            engine.dispose();
        }
    }

    private static Source createInteropComputation() {
        return Source.newBuilder("function test() {\n" +
                        "}\n" +
                        "function interopFunction(notifyHandler) {\n" +
                        "  executing = true;\n" +
                        "  while (executing == true || executing) {\n" +
                        "    executing = notifyHandler.isExecuting;\n" +
                        "  }\n" +
                        "  return executing;\n" +
                        "}\n").name("interopComputation.sl").mimeType(SLLanguage.MIME_TYPE).build();
    }

    @Test
    public void testInteropKeepsContextAround() throws Throwable {
        final Source interopComp = createInteropComputation();

        engine.eval(interopComp);

        final CheckContextHandler nh = new CheckContextHandler();
        Value value = engine.findGlobalSymbol("interopFunction").execute(nh);

        Boolean n = value.as(Boolean.class);
        assertNotNull(n);
        assertTrue("Interop computation OK", !n.booleanValue());

        assertNotNull("Some context found", nh.context);
    }

    private static class CheckContextHandler implements TruffleObject {

        private final CheckContextResolution resolution = new CheckContextResolution(this);
        private final ForeignAccess access = ForeignAccess.create(null, resolution);
        SLContext context;

        @Override
        public ForeignAccess getForeignAccess() {
            return access;
        }
    }

    private static class CheckContextResolution implements ForeignAccess.Factory18, Factory {

        private final CheckContextHandler nh;

        CheckContextResolution(CheckContextHandler nh) {
            this.nh = nh;
        }

        @Override
        public CallTarget accessIsNull() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessIsExecutable() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessIsBoxed() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessHasSize() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessGetSize() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessUnbox() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessRead() {
            return Truffle.getRuntime().createCallTarget(new ExecNotifyReadNode(nh));
        }

        @Override
        public CallTarget accessWrite() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessExecute(int i) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessInvoke(int i) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessNew(int i) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CallTarget accessMessage(Message msg) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean canHandle(TruffleObject to) {
            return (to instanceof CheckContextHandler);
        }

        @Override
        public CallTarget accessKeys() {
            return null;
        }

    }

    private static class ExecNotifyReadNode extends RootNode {

        private final CheckContextHandler nh;

        ExecNotifyReadNode(CheckContextHandler nh) {
            super(SLLanguage.class, null, null);
            this.nh = nh;
        }

        @Override
        public Object execute(VirtualFrame vf) {
            nh.context = SLLanguage.INSTANCE.findContext();
            return false;
        }
    }
}
