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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ArgumentNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ExpressionNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ExampleRootNode;
import com.oracle.truffle.api.library.test.examples.TaintTrackingExampleFactory.UnknownOperationNodeGen;

/**
 * A valid strategy to implement dynamic taint tracking is to wrap values that are tainted. In this
 * example we use Truffle Libraries to wrap any values and add taints while preserving their
 * exported libraries. All messages sent to the {@link TaintWrapper} will be forwarded to the
 * delegate value. The essential feature is that the libraries exported by the delegated value don't
 * need to be known. They will be forwarded by implementing {@link ReflectionLibrary} in the
 * wrapper.
 *
 * @see ReflectionLibrary
 */
@SuppressWarnings("static-method")
public class TaintTrackingExample {

    /**
     * Library we use to propagate identify taints.
     */
    @GenerateLibrary
    @SuppressWarnings("unused")
    abstract static class TaintLibrary extends Library {

        public boolean isTainted(Object receiver) {
            // values that don't implement taint library will always return false.
            return false;
        }

    }

    /**
     * Intercepts calls to the receiver and any messages by implementing ReflectionLibrary.
     */
    @ExportLibrary(TaintLibrary.class)
    @ExportLibrary(ReflectionLibrary.class)
    static class TaintWrapper {

        final Object delegate;

        TaintWrapper(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        final boolean isTainted() {
            return true;
        }

        @ExportMessage
        final Object send(Message message, Object[] args,
                        @CachedLibrary("this.delegate") ReflectionLibrary reflection,
                        @CachedLibrary(limit = "1") TaintLibrary results) throws Exception {
            Object result = reflection.send(delegate, message, args);
            // any operation on a tainted value must result in tainted value
            if (!results.isTainted(result)) {
                result = new TaintWrapper(result);
            }
            return result;
        }
    }

    /**
     * We don't need to know which library it is. But we specify one for the example.
     */
    @GenerateLibrary
    abstract static class TaintTestLibrary extends Library {

        public abstract Object sampleOperation(Object receiver);

    }

    /**
     * We also don't need to know which value we are tainting.
     */
    @ExportLibrary(TaintTestLibrary.class)
    static final class UnknownValue {

        @ExportMessage
        Object sampleOperation() {
            return "42";
        }
    }

    /**
     * Sample node that performs a sample operation on the sample value.
     */
    @NodeChild
    abstract static class UnknownOperationNode extends ExpressionNode {

        @Specialization(limit = "2")
        Object doDefault(Object sample,
                        @CachedLibrary("sample") TaintTestLibrary samples) {
            return samples.sampleOperation(sample);
        }
    }

    @Test
    public void runExample() {
        TaintLibrary taintLibrary = LibraryFactory.resolve(TaintLibrary.class).getUncached();
        UnknownOperationNode sample = UnknownOperationNodeGen.create(new ArgumentNode(0));
        CallTarget target = Truffle.getRuntime().createCallTarget(new ExampleRootNode(sample));

        Object value = new UnknownValue();

        // using a not tainted value will just perform the operation the result is not tainted
        assertFalse(taintLibrary.isTainted(target.call(value)));

        // lets taint the value and retry the operation
        value = new TaintWrapper(value);

        // without knowing the concrete operation performed the result is now also tainted
        assertTrue(taintLibrary.isTainted(target.call(value)));
    }

}
