/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.debug.test.TestIteratorObject;

/**
 * Test of iterators.
 */
public class InspectIteratorTest extends AbstractFunctionValueTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check

    @Test
    public void testIteratorChildren() throws Exception {
        List<Integer> list = Arrays.asList(1, 2, 3);
        Future<?> run = runWith(new TestIteratorObject(false, true, list));

        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"subtype\":\"iterator\",\"description\":\"Object TestIteratorObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"3\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        // Have no special properties of its own, a "next" function is provided among object properties, when available.
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[],\"internalProperties\":[]},\"id\":6}\n"));

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        run.get();
        tester.finish();
    }

    // @formatter:on
    // CheckStyle: resume line length check
}
