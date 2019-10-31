/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.processor;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;

/**
 * Verify errors emitted by the processor.
 */
public class TruffleProcessorTest {

    abstract class MyNode extends Node {
        @ExpectError("@Child field cannot be final") @Child final MyNode first;

        MyNode(MyNode n) {
            this.first = n;
        }
    }

    abstract class CorrectChildType extends Node {
        @Child MyNode first;
    }

    abstract class CorrectChildType2 extends Node {
        @Child NodeInterface first;
    }

    abstract class CorrectChildrenType extends Node {
        @Children MyNode[] first;
    }

    abstract class CorrectChildrenType2 extends Node {
        @Children NodeInterface[] first;
    }

    abstract class IncorrectChildType extends Node {
        @ExpectError("@Child field must implement NodeInterface") @Child String first;
    }

    abstract class IncorrectChildType2 extends Node {
        @ExpectError("@Child field must implement NodeInterface") @Child Object first;
    }

    class IncorrectChildOwner {
        @ExpectError("@Child field is allowed only in Node sub-class") @Node.Child MyNode first;
    }

    abstract class IncorrectChildrenType extends Node {
        @ExpectError("@Children field must be an array of NodeInerface sub-types") @Children String[] first;
    }

    abstract class IncorrectChildrenType2 extends Node {
        @ExpectError("@Children field must be an array of NodeInerface sub-types") @Children Object[] first;
    }

    class IncorrectChildrenOwner {
        @ExpectError("@Children field is allowed only in Node sub-class") @Node.Children MyNode[] first;
    }
}
