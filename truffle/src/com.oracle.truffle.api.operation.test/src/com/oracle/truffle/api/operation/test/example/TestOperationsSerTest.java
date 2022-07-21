/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation.test.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNode;
import com.oracle.truffle.api.operation.OperationNodes;

public class TestOperationsSerTest {

    @Test
    public void testSer() {
        byte[] byteArray = createByteArray();
        TestRootNode root = deserialize(byteArray);

        Assert.assertEquals(3L, root.getCallTarget().call());
    }

    private static TestRootNode deserialize(byte[] byteArray) {
        ByteArrayInputStream input = new ByteArrayInputStream(byteArray);

        OperationNodes nodes2 = null;
        try {
            nodes2 = TestOperationsBuilder.deserialize(OperationConfig.DEFAULT, new DataInputStream(input), (ctx, buf2) -> {
                return buf2.readLong();
            });
        } catch (IOException e) {
            assert false;
        }

        OperationNode node = nodes2.getNodes().get(0);
        TestRootNode root = new TestRootNode(node);
        return root;
    }

    private static byte[] createByteArray() {

        OperationNodes nodes = TestOperationsBuilder.create(OperationConfig.DEFAULT, b -> {
            b.beginReturn();
            b.beginAddOperation();
            b.emitConstObject(1L);
            b.emitConstObject(2L);
            b.endAddOperation();
            b.endReturn();

            b.publish();
        });

        boolean[] haveConsts = new boolean[2];

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            nodes.serialize(new DataOutputStream(output), (ctx, buf2, obj) -> {
                if (obj instanceof Long) {
                    haveConsts[(int) (long) obj - 1] = true;
                    buf2.writeLong((long) obj);
                } else {
                    assert false;
                }
            });
        } catch (IOException e) {
            assert false;
        }

        Assert.assertArrayEquals(new boolean[]{true, true}, haveConsts);

        byte[] byteArray = output.toByteArray();
        return byteArray;
    }
}
