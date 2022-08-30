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

import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.AbstractOperationsTruffleException;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.GenerateOperations.Metadata;
import com.oracle.truffle.api.operation.LocalSetter;
import com.oracle.truffle.api.operation.MetadataKey;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.Variadic;

@GenerateOperations(languageClass = TestLanguage.class, enableYield = true)
@GenerateUncached
@GenerateAOT
@OperationProxy(SomeOperationNode.class)
public abstract class TestOperations extends RootNode implements OperationRootNode {

    protected TestOperations(TruffleLanguage<?> language, FrameDescriptor.Builder frameDescriptor) {
        super(language, frameDescriptor.build());
    }

    protected TestOperations(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Metadata public static final MetadataKey<String> TestData = new MetadataKey<>("default value");

    private static class TestException extends AbstractOperationsTruffleException {
        private static final long serialVersionUID = -9143719084054578413L;

        TestException(String string, Node node, int bci) {
            super(string, node, bci);
        }
    }

    @Operation
    @GenerateAOT
    static final class AddOperation {
        @Specialization
        public static long add(long lhs, long rhs) {
            return lhs + rhs;
        }

        @Specialization
        public static String addStrings(String lhs, String rhs) {
            return lhs + rhs;
        }
    }

    @Operation
    @GenerateAOT
    static final class LessThanOperation {
        @Specialization
        public static boolean lessThan(long lhs, long rhs) {
            return lhs < rhs;
        }
    }

    @Operation
    @GenerateAOT
    static final class VeryComplexOperation {
        @Specialization
        public static long bla(long a1, @Variadic Object[] a2) {
            return a1 + a2.length;
        }
    }

    @Operation
    @GenerateAOT
    static final class ThrowOperation {
        @Specialization
        public static Object perform(@Bind("$bci") int bci, @Bind("this") Node node) {
            throw new TestException("fail", node, bci);
        }
    }

    @Operation
    static final class AlwaysBoxOperation {
        @Specialization
        public static Object perform(Object value) {
            return value;
        }
    }

    @Operation
    static final class AppenderOperation {
        @Specialization
        public static void perform(List<Object> list, Object value) {
            list.add(value);
        }
    }

    @Operation
    static final class TeeLocal {
        @Specialization
        public static long doInt(VirtualFrame frame, long value, LocalSetter setter) {
            setter.setLong(frame, value);
            return value;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object value, LocalSetter setter) {
            setter.setObject(frame, value);
            return value;
        }
    }

    @Operation
    public static final class GlobalCachedReadOp {
        @Specialization(assumptions = "cachedAssumption")
        public static Object doCached(final Association assoc,
                        @Cached(value = "assoc.getAssumption()", allowUncached = true) final Assumption cachedAssumption) {
            return assoc.getValue();
        }
    }

    @Operation
    public static final class NonNodeInstance {
        @Specialization(limit = "3", guards = "i == cachedI")
        public static Integer doCached(Integer i, @Cached("i") Integer cachedI) {
            return cachedI;
        }
    }
}

class TestLanguage extends TruffleLanguage<Object> {
    @Override
    protected Object createContext(Env env) {
        return new Object();
    }

}

class Association {
    public Object getValue() {
        return new Object();
    }

    public Assumption getAssumption() {
        return Assumption.ALWAYS_VALID;
    }
}

@GenerateNodeFactory
abstract class SomeOperationNode extends Node {

    abstract int execute();

    @Specialization
    static int doMagic() {
        return 1337;
    }
}
