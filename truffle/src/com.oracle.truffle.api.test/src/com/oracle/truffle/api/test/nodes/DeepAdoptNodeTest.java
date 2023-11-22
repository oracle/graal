/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.nodes;

import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.nodes.Node;

public class DeepAdoptNodeTest {

    static class DeepNode extends Node {
        @Child DeepNode child;

        DeepNode(int depth) {
            if (depth > 0) {
                child = new DeepNode(depth - 1);
            }
        }

    }

    @Test
    public void testAdoptionNearStackLimit() {
        // "warm up": we are going to invoke adoption near stack limit.
        // So, we want to ensure that all the needed classes are loaded.
        // We do not want the test to fail due to ExceptionInInitializerError
        // from the initialization of the truffle runtime.
        adoptAndCheck(new DeepNode(10));

        // perform the actual test
        exhaustStackAndAdopt(new DeepNode(100));
    }

    private static void exhaustStackAndAdopt(DeepNode node) {
        try {
            exhaustStackAndAdopt(node);
        } catch (StackOverflowError error) {
            adoptAndCheck(node);
        }
    }

    private static void adoptAndCheck(DeepNode node) {
        try {
            node.adoptChildren();

            // All nodes should be adopted on success
            DeepNode parent = node;
            while (parent.child != null) {
                DeepNode child = parent.child;
                assertSame(parent, child.getParent());
                parent = child;
            }
        } catch (StackOverflowError err) {
            // Nodes not adopted due to StackOverflowError should
            // not be hidden under nodes adopted correctly
            // i.e. nothing should be adopted in our case
            DeepNode child = node.child;
            while (child != null) {
                // Not using assertNull(child.getParent()) intentionally.
                // We are close to stack limit and assertNull() is too fancy to fit
                assert child.getParent() == null;
                child = child.child;
            }
            // Rethrow the error to ensure that adoptAndCheck()
            // is invoked again (with more stack space) until
            // it succeeds finally.
            throw err;
        }
    }

}
