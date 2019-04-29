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
package com.oracle.truffle.api.library.test.examples;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ArgumentNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ExpressionNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ExampleRootNode;
import com.oracle.truffle.api.library.test.examples.DynamicDispatchExampleFactory.SampleOperationNodeGen;

/**
 * Sometimes the static Java type system is not flexible enough to specify the exports. For that
 * purpose Truffle libraries support dynamic dispatch. The dynamic dispatch mechanism can be used by
 * exporting the {@link DynamicDispatchLibrary}.
 *
 * @see DynamicDispatchLibrary
 */
@SuppressWarnings("unused")
public class DynamicDispatchExample {

    @ExportLibrary(DynamicDispatchLibrary.class)
    static final class DynamicDispatchObject {

        Class<?> target;

        DynamicDispatchObject(Class<?> target) {
            this.target = target;
        }

        @ExportMessage
        Class<?> dispatch() {
            return target;
        }
    }

    @GenerateLibrary
    abstract static class SampleLibrary extends Library {

        public abstract String message(Object receiver);

    }

    @ExportLibrary(value = SampleLibrary.class, receiverType = DynamicDispatchObject.class)
    abstract static class SampleExport1 {
        @ExportMessage
        static String message(DynamicDispatchObject receiver) {
            return "export1";
        }
    }

    @ExportLibrary(value = SampleLibrary.class, receiverType = DynamicDispatchObject.class)
    abstract static class SampleExport2 {
        @ExportMessage
        static String message(DynamicDispatchObject receiver) {
            return "export2";
        }
    }

    @NodeChild
    abstract static class SampleOperationNode extends ExpressionNode {

        @Specialization(limit = "2")
        String doDefault(Object sample,
                        @CachedLibrary("sample") SampleLibrary samples) {
            return samples.message(sample);
        }
    }

    @Test
    public void runExample() {
        SampleOperationNode read = SampleOperationNodeGen.create(new ArgumentNode(0));
        CallTarget target = Truffle.getRuntime().createCallTarget(new ExampleRootNode(read));

        assertEquals("export1", target.call(new DynamicDispatchObject(SampleExport1.class)));
        assertEquals("export2", target.call(new DynamicDispatchObject(SampleExport2.class)));

        // the target can also be changed dynamically
        DynamicDispatchObject o = new DynamicDispatchObject(SampleExport1.class);
        assertEquals("export1", target.call(o));

        o.target = SampleExport2.class;
        assertEquals("export2", target.call(o));
    }

}
