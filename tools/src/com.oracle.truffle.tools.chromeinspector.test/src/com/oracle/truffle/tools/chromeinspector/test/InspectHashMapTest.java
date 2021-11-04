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

import java.util.concurrent.Future;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.debug.test.TestHashObject;

/**
 * Test of maps.
 */
public class InspectHashMapTest extends AbstractFunctionValueTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check

    private void checkHashArray() throws Exception {
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"subtype\":\"map\",\"description\":\"Object TestHashObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"3\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[],\"internalProperties\":[{\"name\":\"[[Entries]]\",\"value\":{\"subtype\":\"array\",\"description\":\"Object TestIteratorObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"4\"}}]},\"id\":6}\n"));
        tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"4\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"0\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{10 => 100}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"5\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"1\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{20 => 200}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"6\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"2\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{30 => 300}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"7\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"3\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{40 => 400}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"8\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"4\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{50 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"9\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"5\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{60 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"10\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"6\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{70 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"11\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"7\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{80 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"12\"},\"configurable\":true,\"writable\":false}]" +
                                    "},\"id\":7}\n"));
    }

    @Test
    public void testHashMapChildren() throws Exception {
        Future<?> run = runWith(new TestHashObject(TestHashObject.createTestMap(), false));
        checkHashArray();

        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"10\",\"type\":\"number\",\"value\":10,\"objectId\":\"13\"},\"writable\":true}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"100\",\"type\":\"number\",\"value\":100,\"objectId\":\"14\"},\"writable\":true}]},\"id\":10}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"6\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"20\",\"type\":\"number\",\"value\":20,\"objectId\":\"15\"},\"writable\":true}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"200\",\"type\":\"number\",\"value\":200,\"objectId\":\"16\"},\"writable\":false}]},\"id\":11}\n"));
        tester.sendMessage("{\"id\":12,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"9\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"50\",\"type\":\"number\",\"value\":50,\"objectId\":\"17\"},\"writable\":true}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"<not readable>\",\"type\":\"object\",\"objectId\":\"18\"},\"writable\":true}]},\"id\":12}\n"));
        tester.sendMessage("{\"id\":13,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"12\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"80\",\"type\":\"number\",\"value\":80,\"objectId\":\"19\"},\"writable\":false}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"<not readable>\",\"type\":\"object\",\"objectId\":\"20\"},\"writable\":false}]},\"id\":13}\n"));

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        run.get();
        tester.finish();
    }

    @Test
    public void testHashMapSet() throws Exception {
        Future<?> run = runWith(new TestHashObject(TestHashObject.createTestMap(), true));
        checkHashArray();

        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"10\",\"type\":\"number\",\"value\":10,\"objectId\":\"13\"},\"writable\":true}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"100\",\"type\":\"number\",\"value\":100,\"objectId\":\"14\"},\"writable\":true}]},\"id\":10}\n"));
        // Set value of 10 to 42:
        tester.sendMessage("{\"id\":11,\"method\":\"Runtime.callFunctionOn\",\"params\":{\"objectId\":\"5\",\"functionDeclaration\":\"function(a, b) { this[a] = b; }\",\"arguments\":[{\"value\":\"value\"},{\"value\":42}],\"silent\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{}},\"id\":11}\n"));
        // Verify the new value:
        tester.sendMessage("{\"id\":12,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"10\",\"type\":\"number\",\"value\":10,\"objectId\":\"15\"},\"writable\":true}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"42\",\"type\":\"number\",\"value\":42,\"objectId\":\"16\"},\"writable\":true}]},\"id\":12}\n"));

        // Set key to 24:
        tester.sendMessage("{\"id\":13,\"method\":\"Runtime.callFunctionOn\",\"params\":{\"objectId\":\"5\",\"functionDeclaration\":\"function(a, b) { this[a] = b; }\",\"arguments\":[{\"value\":\"key\"},{\"value\":24}],\"silent\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{}},\"id\":13}\n"));
        // Verify the new value:
        tester.sendMessage("{\"id\":14,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[],\"internalProperties\":[{\"name\":\"[[Entries]]\",\"value\":{\"subtype\":\"array\",\"description\":\"Object TestIteratorObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"17\"}}]},\"id\":14}\n"));
        tester.sendMessage("{\"id\":15,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"17\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"0\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{20 => 200}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"18\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"1\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{30 => 300}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"19\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"2\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{40 => 400}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"20\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"3\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{50 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"21\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"4\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{60 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"22\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"5\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{70 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"23\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"6\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{80 => <not readable>}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"24\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"7\",\"value\":{\"subtype\":\"internal#entry\",\"description\":\"{24 => 42}\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"25\"},\"configurable\":true,\"writable\":false}]" +
                                    "},\"id\":15}\n"));
        tester.sendMessage("{\"id\":16,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"25\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"name\":\"key\",\"value\":{\"description\":\"24\",\"type\":\"number\",\"value\":24,\"objectId\":\"26\"},\"writable\":true}," +
                                                 "{\"name\":\"value\",\"value\":{\"description\":\"42\",\"type\":\"number\",\"value\":42,\"objectId\":\"27\"},\"writable\":true}]},\"id\":16}\n"));

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
