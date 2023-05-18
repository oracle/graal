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
package com.oracle.truffle.api.operation.test;

import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.AbstractOperationsTruffleException;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.LocalSetter;
import com.oracle.truffle.api.operation.LocalSetterRange;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.ShortCircuitOperation;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.operation.test.GenerateOperationsTestVariants.Variant;

@GenerateOperationsTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateOperations(languageClass = TestOperationsLanguage.class, enableYield = true, enableSerialization = true)),
                @Variant(suffix = "Unsafe", configuration = @GenerateOperations(languageClass = TestOperationsLanguage.class, enableYield = true, enableSerialization = true, allowUnsafe = true)),
                @Variant(suffix = "WithBaseline", configuration = @GenerateOperations(languageClass = TestOperationsLanguage.class, enableYield = true, enableSerialization = true, enableBaselineInterpreter = true)),
                @Variant(suffix = "WithOptimizations", configuration = @GenerateOperations(languageClass = TestOperationsLanguage.class, enableYield = true, enableSerialization = true, decisionsFile = "test_operations_decisions.json")),
                // A typical "production" configuration with all of the bells and whistles.
                @Variant(suffix = "Production", configuration = @GenerateOperations(languageClass = TestOperationsLanguage.class, enableYield = true, enableSerialization = true, allowUnsafe = true, enableBaselineInterpreter = true, decisionsFile = "test_operations_decisions.json"))
})
@GenerateAOT
@OperationProxy(SomeOperationNode.class)
@ShortCircuitOperation(booleanConverter = TestOperations.ToBoolean.class, name = "ScAnd", continueWhen = true)
@ShortCircuitOperation(booleanConverter = TestOperations.ToBoolean.class, name = "ScOr", continueWhen = false)
public abstract class TestOperations extends RootNode implements OperationRootNode {

    protected TestOperations(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    protected String name;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    // Expose the protected cloneUninitialized method for testing.
    public TestOperations doCloneUninitialized() {
        return (TestOperations) cloneUninitialized();
    }

    protected static class TestException extends AbstractOperationsTruffleException {
        private static final long serialVersionUID = -9143719084054578413L;

        public final long value;

        TestException(String string, Node node, int bci, long value) {
            super(string, node, bci);
            this.value = value;
        }
    }

    @Operation
    @GenerateAOT
    static final class AddOperation {
        @Specialization
        public static long addLongs(long lhs, long rhs) {
            return lhs + rhs;
        }

        @Specialization
        @TruffleBoundary
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
        public static Object perform(long value,
                        @Bind("$bci") int bci,
                        @Bind("$root") Node node) {
            throw new TestException("fail", node, bci, value);
        }
    }

    @Operation
    static final class ReadExceptionOperation {
        @Specialization
        public static long perform(TestException ex) {
            return ex.value;
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
        @SuppressWarnings("unchecked")
        @Specialization
        @TruffleBoundary
        public static void perform(List<?> list, Object value) {
            ((List<Object>) list).add(value);
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
    static final class TeeLocalRange {
        @Specialization
        @ExplodeLoop
        public static Object doLong(VirtualFrame frame, long[] value, LocalSetterRange setter) {
            for (int i = 0; i < value.length; i++) {
                setter.setLong(frame, i, value[i]);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        public static Object doGeneric(VirtualFrame frame, Object[] value, LocalSetterRange setter) {
            for (int i = 0; i < value.length; i++) {
                setter.setObject(frame, i, value[i]);
            }
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

    @Operation
    public static final class Invoke {
        @Specialization
        public static Object doInvoke(TestOperations root, @Variadic Object[] args) {
            return root.getCallTarget().call(args);
        }

        @Specialization
        public static Object doInvoke(TestClosure root, @Variadic Object[] args) {
            assert args.length == 0 : "not implemented";
            return root.call();
        }
    }

    @Operation
    public static final class CreateClosure {
        @Specialization
        public static TestClosure materialize(VirtualFrame frame, TestOperations root) {
            return new TestClosure(frame.materialize(), root);
        }
    }

    @Operation
    public static final class VoidOperation {
        @Specialization
        public static void doNothing() {
        }
    }

    @Operation
    public static final class ZeroLocalOperation {
        @Specialization
        public static void doZero(VirtualFrame frame, LocalSetter setter) {
            setter.setInt(frame, 0);
        }
    }

    public static final class ToBoolean {
        @Specialization
        public static boolean doLong(long l) {
            return l != 0;
        }

        @Specialization
        public static boolean doBoolean(boolean b) {
            return b;
        }

        @Specialization
        public static boolean doString(String s) {
            return s != null;
        }
    }
}

class TestClosure {
    private final MaterializedFrame frame;
    private final RootCallTarget root;

    TestClosure(MaterializedFrame frame, TestOperations root) {
        this.frame = frame;
        this.root = root.getCallTarget();
    }

    public Object call() {
        return root.call(frame);
    }
}

class Association {
    public Object getValue() {
        return new Object();
    }

    @NeverDefault
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
