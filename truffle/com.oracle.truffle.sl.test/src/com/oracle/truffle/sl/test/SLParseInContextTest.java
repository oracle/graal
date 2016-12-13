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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ParsingRequest;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.SLLanguage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class SLParseInContextTest {
    private Method parseMethod;
    private Constructor<ParsingRequest> createRequest;
    private PolyglotEngine vm;
    private Method executionStarted;

    @Before
    public void prepareMethods() throws Exception {
        vm = PolyglotEngine.newBuilder().build();
        vm.eval(Source.newBuilder("function x() {}").mimeType("application/x-sl").name("empty.sl").build());

        parseMethod = SLLanguage.class.getDeclaredMethod("parse", ParsingRequest.class);
        createRequest = ParsingRequest.class.getDeclaredConstructor(
                        Object.class, TruffleLanguage.class, Source.class, Node.class, MaterializedFrame.class, String[].class);
        executionStarted = vm.getClass().getDeclaredMethod("executionStarted");

        parseMethod.setAccessible(true);
        createRequest.setAccessible(true);
        executionStarted.setAccessible(true);
    }

    @Test
    public void parseAPlusB() throws Exception {

        Source aPlusB = Source.newBuilder("a + b").mimeType("application/x-sl").name("plus.sl").build();

        ParsingRequest request = createParsingRequest(aPlusB);

        assertNull("No frame", request.getFrame());
        assertNull("No node", request.getNode());
        assertEquals("Right source", aPlusB, request.getSource());

        CallTarget executeAPlusB = parse(request);

        Object fourtyTwo = executeAPlusB.call(30L, 12L);
        assertTrue("Result is a number: " + fourtyTwo, fourtyTwo instanceof Number);
        assertEquals(42, ((Number) fourtyTwo).intValue());
    }

    private CallTarget parse(ParsingRequest request) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        executionStarted.invoke(vm);
        CallTarget executeAPlusB = (CallTarget) parseMethod.invoke(SLLanguage.INSTANCE, request);
        return executeAPlusB;
    }

    private ParsingRequest createParsingRequest(Source aPlusB) throws InstantiationException, IllegalArgumentException, InvocationTargetException, IllegalAccessException {
        ParsingRequest request = createRequest.newInstance(vm, SLLanguage.INSTANCE, aPlusB, null, null, new String[]{"a", "b"});
        return request;
    }
}
