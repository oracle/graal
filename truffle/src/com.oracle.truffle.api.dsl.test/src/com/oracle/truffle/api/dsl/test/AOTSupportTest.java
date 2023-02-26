/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.AOTSupport;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTAutoLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTManualLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTManualLibrarySingleLimitNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.AOTRecursiveErrorNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.NoSpecializationTestNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.RecursiveNodeGen;
import com.oracle.truffle.api.dsl.test.AOTSupportTestFactory.TestNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ByteValueProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.DoubleValueProfile;
import com.oracle.truffle.api.profiles.FloatValueProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.api.profiles.LongValueProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/**
 * Note that this test is also used in AOTSupportCompilationTest.
 */
@SuppressWarnings("deprecation")
public class AOTSupportTest extends AbstractPolyglotTest {

    public static final String LANGUAGE_ID = "AOTSupportTest_TestLanguage";

    public AOTSupportTest() {
        enterContext = false;
    }

    @Test
    public void testNoSpecializations() {
        assertEquals(42, NoSpecializationTestNodeGen.create().execute(42));
    }

    @GenerateAOT
    @Introspectable
    @GenerateUncached
    abstract static class NoSpecializationTestNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object doDefault(Object arg) {
            return arg;
        }

    }

    public static class TestRootNode extends RootNode {

        @Child BaseNode node;

        final TestLanguage language;

        private final boolean hasReceiver;
        // deliberately not compilation final to not fold all the code
        private Object receiver;

        public TestRootNode(TestLanguage language, BaseNode node, Object receiver) {
            super(language);
            this.language = language;
            this.node = node;
            this.receiver = receiver;
            this.hasReceiver = receiver != null;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int sum = 0;
            for (int i = 0; i < TestNode.AOT_SPECIALIZATIONS; i++) {
                if (hasReceiver) {
                    sum += node.execute(receiver, i);
                } else {
                    sum += node.execute(i);
                }
            }
            return sum;
        }

        @Override
        protected ExecutionSignature prepareForAOT() {
            AOTSupport.prepareForAOT(this);
            return ExecutionSignature.create(Integer.class, new Class<?>[0]);
        }

        @Override
        public String getName() {
            return "TestRoot_" + node.getClass().getSimpleName();
        }

        @Override
        public String toString() {
            return getName();
        }

    }

    @Test
    public void testNoLock() {
        TestRootNode root = setup(TestNodeGen.create(false));
        assertFails(() -> ((GenerateAOT.Provider) root.node).prepareForAOT(root.language, root), AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("AST lock"));
        });
    }

    @Test
    public void testNode() {
        assertInterpreterExecution(TestNodeGen.create(false), null);
        assertInterpreterExecution(AOTAutoLibraryNodeGen.create(), new AOTInitializable());
        assertInterpreterExecution(AOTManualLibraryNodeGen.create(), new AOTInitializable());
        assertInterpreterExecution(AOTManualLibraryNodeGen.create(), new AOTInitializable());
        assertInterpreterExecution(AOTManualLibrarySingleLimitNodeGen.create(), new AOTInitializable());

        assertInterpreterExecution(AOTAutoLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertInterpreterExecution(AOTManualLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertInterpreterExecution(AOTManualLibraryNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));
        assertInterpreterExecution(AOTManualLibrarySingleLimitNodeGen.create(), new AOTDynamicDispatch(AOTDynamicDispatchTarget.class));

    }

    private void assertInterpreterExecution(BaseNode baseNode, Object receiver) {
        TestRootNode root = setup(baseNode);
        AOTSupport.prepareForAOT(root);
        if (receiver == null) {
            assertEquals(TestNode.AOT_SPECIALIZATIONS, countActiveSpecializations(root));
        } else {
            assertEquals(1, countActiveSpecializations(root));
        }

        context.enter();
        try {
            for (int i = 0; i < TestNode.AOT_SPECIALIZATIONS; i++) {
                if (receiver != null) {
                    root.node.execute(new AOTInitializable(), i);
                } else {
                    root.node.execute(i);
                }

                if (receiver == null) {
                    assertEquals(i + 1, countActiveSpecializations(root));
                } else {
                    assertEquals(1, countActiveSpecializations(root));
                }
            }

            // test excluded message with library
            if (receiver != null) {
                root.node.execute(new AOTInitializable(), 11);
            }

        } finally {
            context.leave();
        }
    }

    private TestRootNode setup(BaseNode node) {
        setupEnv();
        context.initialize(LANGUAGE_ID);
        context.enter();
        TestRootNode root = new TestRootNode(TestLanguage.getCurrentLanguage(), node, null);
        root.getCallTarget();
        context.leave();
        return root;
    }

    private static int countActiveSpecializations(TestRootNode root) {
        return (int) Introspection.getSpecializations(root.node).stream().filter(SpecializationInfo::isActive).count();
    }

    @TypeSystem
    public static class AOTTypeSystem {

        @ImplicitCast
        public static long castInt(int v) {
            return v;
        }

    }

    @Introspectable
    public abstract static class BaseNode extends Node {

        public final int execute(Object arg0) {
            return execute(arg0, null);
        }

        public abstract int execute(Object arg0, Object arg1);

    }

    @GenerateAOT
    @Introspectable
    @TypeSystemReference(AOTTypeSystem.class)
    @SuppressWarnings("unused")
    public abstract static class TestNode extends BaseNode {

        final boolean recursive;

        TestNode(boolean recursive) {
            this.recursive = recursive;
        }

        @Specialization(guards = "arg == 0")
        int basic(int arg) {
            return arg;
        }

        @Specialization(guards = "arg == 1")
        int basicCached(int arg, @Cached("1") int one,
                        @Cached("2") int two,
                        @Cached("3") int three,
                        @Cached("4") int four) {
            return arg;
        }

        @Specialization(guards = "arg == 2")
        int nodeCachedSingle(int arg,
                        @Cached("1") int one,
                        @Cached("2") int two,
                        @Cached("3") int three,
                        @Cached("4") int four,
                        @Cached NoSpecializationTestNode node) {
            return arg;
        }

        @Specialization(guards = "arg == 3")
        int nodeCachedMulti(int arg, @Cached("1") int one,
                        @Cached("2") int two,
                        @Cached("3") int three,
                        @Cached("4") int four,
                        @Cached NoSpecializationTestNode node) {
            return arg;
        }

        @Specialization(guards = "arg == 4")
        int languageReferenceLookup1(int arg) {
            return arg;
        }

        @Specialization(guards = "arg == 5")
        int languageReferenceLookup2(int arg) {
            return arg;
        }

        @Specialization(guards = "arg == 6", assumptions = "createAssumption()")
        int assumptionUsage(int arg) {
            return arg;
        }

        static Assumption createAssumption() {
            return Truffle.getRuntime().createAssumption();
        }

        @Specialization(guards = {"arg == 7", "cachedArg == 6"})
        int implicitCast(long arg, @Cached("6") int cachedArg) {
            return (int) arg;
        }

        @Specialization(guards = {"arg == 8", "arg == cachedArg"})
        int ignoredCache(int arg, @Cached("arg") int cachedArg) {
            return arg;
        }

        @Specialization(guards = "arg == 8", replaces = "ignoredCache")
        int genericCache(int arg) {
            return arg;
        }

        @Specialization(guards = {"arg == 9", "!recursive"})
        int recursiveCache(int arg, @Cached("create(true)") TestNode recursiveNode) {
            return recursiveNode.execute(arg);
        }

        @Specialization(guards = {"arg == 9", "recursive"})
        int noRecursiveCache(int arg) {
            return arg;
        }

        @Specialization(guards = {"arg == 10"})
        int profiles(int arg, @Cached BranchProfile branch,
                        @Cached("createBinaryProfile()") ConditionProfile binaryCondition,
                        @Cached("createCountingProfile()") ConditionProfile countingCondition,
                        @Cached("createCountingProfile()") LoopConditionProfile loopCondition,
                        @Cached("createIdentityProfile()") ByteValueProfile byteValue,
                        @Cached("createIdentityProfile()") IntValueProfile intValue,
                        @Cached("createIdentityProfile()") LongValueProfile longValue,
                        @Cached("createRawIdentityProfile()") FloatValueProfile floatValue,
                        @Cached("createRawIdentityProfile()") DoubleValueProfile doubleValue,
                        @Cached("createEqualityProfile()") PrimitiveValueProfile primitiveValue,
                        @Cached("createClassProfile()") ValueProfile classValue,
                        @Cached("createIdentityProfile()") ValueProfile identityValue) {

            branch.enter();
            binaryCondition.profile(true);
            binaryCondition.profile(false);
            countingCondition.profile(true);
            countingCondition.profile(false);
            loopCondition.profile(true);
            loopCondition.profile(false);

            byteValue.profile((byte) 1);
            byteValue.profile((byte) 2);
            intValue.profile(1);
            intValue.profile(2);
            longValue.profile(1);
            longValue.profile(2);
            floatValue.profile(1);
            floatValue.profile(2);
            doubleValue.profile(1);
            doubleValue.profile(2);

            primitiveValue.profile(true);
            primitiveValue.profile(false);
            primitiveValue.profile((byte) 1);
            primitiveValue.profile((byte) 2);
            primitiveValue.profile((short) 1);
            primitiveValue.profile((short) 2);
            primitiveValue.profile((char) 1);
            primitiveValue.profile((char) 2);
            primitiveValue.profile(1);
            primitiveValue.profile(2);
            primitiveValue.profile(1L);
            primitiveValue.profile(2L);
            primitiveValue.profile(1f);
            primitiveValue.profile(2f);
            primitiveValue.profile(1d);
            primitiveValue.profile(2d);
            primitiveValue.profile(Integer.valueOf(1));
            primitiveValue.profile(Integer.valueOf(2));

            classValue.profile(Integer.class);
            identityValue.profile(this);

            return arg;
        }

        public static final int AOT_SPECIALIZATIONS = 11;

    }

    @GenerateAOT
    @GenerateLibrary
    @DefaultExport(DefaultAOTExport.class)
    public abstract static class AOTTestLibrary extends Library {

        public abstract int m0(Object receiver, Object arg);

        public abstract int m1(Object receiver);

    }

    @ExportLibrary(value = AOTTestLibrary.class, receiverType = DefaultAOTReceiver.class, useForAOT = true, useForAOTPriority = 0)
    @SuppressWarnings("unused")
    public static final class DefaultAOTExport {
        @ExportMessage
        static int m0(DefaultAOTReceiver receiver, Object arg) {
            return 42;
        }

        @ExportMessage
        static int m1(DefaultAOTReceiver receiver) {
            return 43;
        }

    }

    static final class DefaultAOTReceiver {

    }

    @GenerateAOT
    @GenerateLibrary
    public abstract static class OtherAOTTestLibrary extends Library {

        public abstract int m2(Object receiver);

    }

    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @ExportLibrary(value = OtherAOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @SuppressWarnings("unused")
    public static final class AOTInitializable {

        @ExportMessage
        static boolean accepts(AOTInitializable receiver) {
            return true;
        }

        @ExportMessage
        static int m1(AOTInitializable receiver, @Cached("42") int cachedValue) {
            return cachedValue;
        }

        @ExportMessage
        static int m2(AOTInitializable receiver) {
            return 42;
        }

        @ExportMessage
        public abstract static class M0 {

            @Specialization(guards = "arg == 0")
            static int basic(AOTInitializable receiver, int arg) {
                return arg;
            }

            @Specialization(guards = "arg == 1")
            static int basicCached(AOTInitializable receiver, int arg,
                            @Exclusive @Cached("1") int one,
                            @Exclusive @Cached("2") int two,
                            @Exclusive @Cached("3") int three,
                            @Exclusive @Cached("4") int four) {
                return arg;
            }

            @Specialization(guards = "arg == 2")
            static int nodeCachedSingle(AOTInitializable receiver, int arg,
                            @Exclusive @Cached NoSpecializationTestNode node) {
                return arg;
            }

            @Specialization(guards = "arg == 3")
            static int nodeCachedMulti(AOTInitializable receiver, int arg,
                            @Exclusive @Cached("1") int one,
                            @Exclusive @Cached("2") int two,
                            @Exclusive @Cached("3") int three,
                            @Exclusive @Cached("4") int four,
                            @Exclusive @Cached NoSpecializationTestNode node) {
                return arg;
            }

            @Specialization(guards = "arg == 4")
            static int languageReferenceLookup1(AOTInitializable receiver, int arg) {
                return arg;
            }

            @Specialization(guards = "arg == 5")
            static int languageReferenceLookup2(AOTInitializable receiver, int arg) {
                return arg;
            }

            @Specialization(guards = "arg == 6", assumptions = "createAssumption()")
            static int assumptionUsage(AOTInitializable receiver, int arg) {
                return arg;
            }

            static Assumption createAssumption() {
                return Truffle.getRuntime().createAssumption();
            }

            @Specialization(guards = {"arg == 7", "arg == cachedArg"})
            static int ignoredCache(AOTInitializable receiver, int arg, @Cached("arg") int cachedArg) {
                return arg;
            }

            @Specialization(guards = "arg == 7", replaces = "ignoredCache")
            static int genericCache(AOTInitializable receiver, int arg) {
                return arg;
            }

            @Specialization(guards = {"arg == 8"})
            static int profiles(AOTInitializable receiver, int arg, @Cached BranchProfile branch,
                            @Cached("createBinaryProfile()") ConditionProfile binaryCondition,
                            @Cached("createCountingProfile()") ConditionProfile countingCondition,
                            @Cached("createCountingProfile()") LoopConditionProfile loopCondition,
                            @Cached("createIdentityProfile()") ByteValueProfile byteValue,
                            @Cached("createIdentityProfile()") IntValueProfile intValue,
                            @Cached("createIdentityProfile()") LongValueProfile longValue,
                            @Cached("createRawIdentityProfile()") FloatValueProfile floatValue,
                            @Cached("createRawIdentityProfile()") DoubleValueProfile doubleValue,
                            @Cached("createEqualityProfile()") PrimitiveValueProfile primitiveValue,
                            @Cached("createClassProfile()") ValueProfile classValue,
                            @Cached("createIdentityProfile()") ValueProfile identityValue) {

                branch.enter();
                binaryCondition.profile(true);
                binaryCondition.profile(false);
                countingCondition.profile(true);
                countingCondition.profile(false);
                loopCondition.profile(true);
                loopCondition.profile(false);

                byteValue.profile((byte) 1);
                byteValue.profile((byte) 2);
                intValue.profile(1);
                intValue.profile(2);
                longValue.profile(1);
                longValue.profile(2);
                floatValue.profile(1);
                floatValue.profile(2);
                doubleValue.profile(1);
                doubleValue.profile(2);

                primitiveValue.profile(true);
                primitiveValue.profile(false);
                primitiveValue.profile((byte) 1);
                primitiveValue.profile((byte) 2);
                primitiveValue.profile((short) 1);
                primitiveValue.profile((short) 2);
                primitiveValue.profile((char) 1);
                primitiveValue.profile((char) 2);
                primitiveValue.profile(1);
                primitiveValue.profile(2);
                primitiveValue.profile(1L);
                primitiveValue.profile(2L);
                primitiveValue.profile(1f);
                primitiveValue.profile(2f);
                primitiveValue.profile(1d);
                primitiveValue.profile(2d);
                primitiveValue.profile(Integer.valueOf(1));
                primitiveValue.profile(Integer.valueOf(2));

                classValue.profile(Integer.class);
                identityValue.profile(receiver);

                return arg;
            }

            @Specialization(guards = {"arg == 9"})
            static int nop1(AOTInitializable receiver, int arg, @CachedLibrary("receiver") AOTTestLibrary library) {
                return arg;
            }

            @Specialization(guards = {"arg == 10"})
            static int nop2(AOTInitializable receiver, int arg) {
                return arg;
            }

            @GenerateAOT.Exclude
            @Specialization(guards = {"arg == 11"})
            @TruffleBoundary
            static int excludedCache(AOTInitializable receiver, int arg, @CachedLibrary("receiver") InteropLibrary library) {
                assertNotNull(library);
                return 42;
            }

        }

    }

    @Test
    public void testRecursionError() {
        TestRootNode root = setup(AOTRecursiveErrorNodeGen.create());
        AbstractPolyglotTest.assertFails(() -> AOTSupport.prepareForAOT(root), AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("<-recursion-detected->"));
        });
    }

    @SuppressWarnings("unused")
    @GenerateAOT
    public abstract static class AOTRecursiveErrorNode extends BaseNode {

        @Specialization
        int doDefault(Object receiver, int arg1, @Cached AOTIndirectRecursiveErrorNode cachedValue) {
            return arg1;
        }

    }

    @SuppressWarnings("unused")
    @GenerateAOT
    public abstract static class AOTDirectRecursionError extends BaseNode {

        @Specialization
        int doDefault(Object receiver, int arg1,
                        @ExpectError("Failed to generate code for @GenerateAOT: Recursive AOT preparation detected. %")//
                        @Cached AOTDirectRecursionError cachedValue) {
            return arg1;
        }

    }

    @SuppressWarnings("unused")
    @GenerateAOT
    public abstract static class AOTIndirectRecursiveErrorNode extends BaseNode {

        @Specialization
        int doDefault(Object receiver, int arg1, @Cached AOTRecursiveErrorNode asdf) {
            return arg1;
        }

    }

    @GenerateAOT
    public abstract static class AOTAutoLibraryNode extends BaseNode {

        @Specialization
        int doDefault(Object receiver, int arg1, @CachedLibrary(limit = "3") AOTTestLibrary lib) {
            return lib.m0(receiver, arg1);
        }

    }

    @GenerateAOT
    public abstract static class AOTManualLibraryNode extends BaseNode {

        @Specialization(limit = "3")
        int doDefault(Object receiver, int arg1, @CachedLibrary("receiver") AOTTestLibrary lib) {
            return lib.m0(receiver, arg1);
        }

    }

    @GenerateAOT
    public abstract static class AOTManualLibrarySingleLimitNode extends BaseNode {

        @Specialization(limit = "1")
        int doDefault(Object receiver, int arg1, @CachedLibrary("receiver") AOTTestLibrary lib) {
            return lib.m0(receiver, arg1);
        }

    }

    @ExportLibrary(value = DynamicDispatchLibrary.class, useForAOT = true)
    public static final class AOTDynamicDispatch {

        private final Class<?> dispatchTarget;

        public AOTDynamicDispatch(Class<?> dispatchTarget) {
            this.dispatchTarget = dispatchTarget;
        }

        @ExportMessage
        Class<?> dispatch() {
            return dispatchTarget;
        }

    }

    @ExportLibrary(value = AOTTestLibrary.class, receiverType = AOTDynamicDispatch.class, useForAOT = true, useForAOTPriority = 1)
    @SuppressWarnings("unused")
    public static final class AOTDynamicDispatchTarget {

        @ExportMessage
        static int m0(AOTDynamicDispatch receiver, Object arg) {
            return (int) arg;
        }

        @ExportMessage
        static int m1(AOTDynamicDispatch receiver) {
            return 0;
        }

    }

    @Registration(id = LANGUAGE_ID, name = LANGUAGE_ID)
    public static class TestLanguage extends TruffleLanguage<Env> {

        Object value = 42;

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        public Object getValue() {
            return value;
        }

        public static TestLanguage getCurrentLanguage() {
            return getCurrentLanguage(TestLanguage.class);
        }

    }

    @GenerateAOT
    abstract static class ErrorDynamicParameterBoundCache extends Node {

        abstract Object execute(Object arg);

        @Specialization
        @SuppressWarnings("unused")
        int basicCached(int arg,
                        @ExpectError("Failed to generate code for @GenerateAOT: Cached values in specializations included for AOT must not bind dynamic values. " + //
                                        "Such caches are only allowed to bind static values, values read from the node or values from the current language instance using a language reference. Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @GenerateAOT.Exclude if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" +
                                        " - Avoid binding dynamic parameters in the cache initializer expression. %n" + //
                                        " - If a cached library is used add the @GenerateAOT annotation to the library class to enable AOT for the library.")//
                        @Cached("arg") int cachedArg) {
            return arg;
        }
    }

    @GenerateAOT
    abstract static class ErrorDynamicLibraryNoGuardBound extends Node {

        abstract Object execute(Object arg);

        @Specialization(limit = "1")
        @SuppressWarnings("unused")
        int basicCached(Object arg,
                        @ExpectError("Failed to generate code for @GenerateAOT: Cached values in specializations included for AOT must not bind dynamic values. " + //
                                        "Such caches are only allowed to bind static values, values read from the node or values from the current language instance using a language reference. Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @GenerateAOT.Exclude if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" +
                                        " - Avoid binding dynamic parameters in the cache initializer expression. %n" + //
                                        " - If a cached library is used add the @GenerateAOT annotation to the library class to enable AOT for the library.")//
                        @CachedLibrary("arg") InteropLibrary lib) {
            try {
                return lib.asInt(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateAOT
    abstract static class ErrorDynamicLibraryWithGuardBound extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "lib.fitsInInt(arg)", limit = "1")
        @SuppressWarnings("unused")
        int basicCached(Object arg,
                        @ExpectError("Failed to generate code for @GenerateAOT: Cached values in specializations included for AOT must not bind dynamic values. " + //
                                        "Such caches are only allowed to bind static values, values read from the node or values from the current language instance using a language reference. Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @GenerateAOT.Exclude if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" +
                                        " - Avoid binding dynamic parameters in the cache initializer expression. %n" + //
                                        " - If a cached library is used add the @GenerateAOT annotation to the library class to enable AOT for the library.")//
                        @CachedLibrary("arg") InteropLibrary lib) {
            try {
                return lib.asInt(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateAOT
    abstract static class ErrorDynamicDispatchedLibrary extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "lib.fitsInInt(arg)")
        @SuppressWarnings("unused")
        int basicCached(Object arg,
                        @ExpectError("Failed to generate code for @GenerateAOT: @CachedLibrary with automatic dispatch cannot be prepared for AOT.Resolve this problem by either: %n" + //
                                        " - Exclude this specialization from AOT with @GenerateAOT.Exclude if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" + //
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" + //
                                        " - Remove the cached parameter value. %n" + //
                                        " - Define a cached library initializer expression for manual dispatch. %n" + //
                                        " - Add the @GenerateAOT annotation to the InteropLibrary library class to enable AOT for the library.")//
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return lib.asInt(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    abstract static class WithoutAOTSupportDSLNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        @SuppressWarnings("unused")
        int s0(int arg) {
            return arg;
        }
    }

    @GenerateAOT
    abstract static class ErrorCachedDSLNodeWithNoAOT extends Node {

        abstract Object execute(Object arg);

        @Specialization
        @SuppressWarnings("unused")
        int basicCached(Object arg,
                        @ExpectError("Failed to generate code for @GenerateAOT: Referenced node type cannot be initialized for AOT.Resolve this problem by either: %n" +
                                        " - Exclude this specialization from AOT with @GenerateAOT.Exclude if it is acceptable to deoptimize for this specialization in AOT compiled code. %n" +
                                        " - Configure the specialization to be replaced with a more generic specialization. %n" +
                                        " - Remove the cached parameter value. %n" +
                                        " - Add the @GenerateAOT annotation to node type 'WithoutAOTSupportDSLNode' or one of its super types.") //
                        @Cached WithoutAOTSupportDSLNode dslNode) {
            return (int) dslNode.execute(arg);
        }
    }

    @GenerateLibrary
    public abstract static class NoAOTTestLibrary extends Library {

        public abstract int m0(Object receiver);

    }

    @ExpectError("The useForAOT property needs to be declared for exports of libraries annotated with @GenerateAOT. " + //
                    "Declare the useForAOT property to resolve this problem.")
    @ExportLibrary(value = AOTTestLibrary.class)
    public static class ErrorNoDeclaration {

        @ExportMessage
        final void m0() {
        }

    }

    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @SuppressWarnings("unused")
    public static final class ErrorMergedLibrary {

        final Object delegate = null;

        @SuppressWarnings("static-method")
        @ExportMessage
        int m0(Object arg0,
                        @ExpectError("Merged libraries are not supported in combination with AOT preparation. Resolve this problem by either: %n" +
                                        " - Setting @ExportLibrary(..., useForAOT=false) to disable AOT preparation for this export. %n" +
                                        " - Using a dispatched library without receiver expression. %n" +
                                        " - Adding the @GenerateAOT.Exclude annotation to the specialization or exported method.")//
                        @CachedLibrary("this.delegate") AOTTestLibrary lib) {
            return 42;
        }

        @ExportMessage
        static class M1 {

            @SuppressWarnings("static-method")
            @Specialization
            static int doDefault(ErrorMergedLibrary receiver,
                            @ExpectError("Merged libraries are not supported in combination with AOT preparation. Resolve this problem by either: %n" +
                                            " - Setting @ExportLibrary(..., useForAOT=false) to disable AOT preparation for this export. %n" +
                                            " - Using a dispatched library without receiver expression. %n" +
                                            " - Adding the @GenerateAOT.Exclude annotation to the specialization or exported method.")//
                            @CachedLibrary("receiver") InteropLibrary lib) {
                return 42;
            }
        }

    }

    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @SuppressWarnings("unused")
    public static final class ErrorMergedLibraryExclude {

        final Object delegate = null;

        @SuppressWarnings("static-method")
        @ExportMessage
        @GenerateAOT.Exclude
        int m0(Object arg0,
                        @CachedLibrary("this.delegate") AOTTestLibrary lib) {
            return 42;
        }

        @ExportMessage
        static class M1 {

            @SuppressWarnings("static-method")
            @Specialization
            @GenerateAOT.Exclude
            static int doDefault(ErrorMergedLibraryExclude receiver,
                            @CachedLibrary("receiver") InteropLibrary lib) {
                return 42;
            }
        }

    }

    @ExpectError("If useForAOT is set to true the receiver type must be a final. " + //
                    "The compiled code would otherwise cause performance warnings. " + //
                    "Add the final modifier to the receiver class or set useForAOT to false to resolve this.")
    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    public static class ErrorNonFinal {

        @ExportMessage
        void m0() {
        }

    }

    @ExpectError("The exported library does not support AOT. " +
                    "Add the @GenerateAOT annotation to the library class com.oracle.truffle.api.dsl.test.AOTSupportTest.NoAOTTestLibrary to resolve this.")
    @ExportLibrary(value = NoAOTTestLibrary.class, useForAOT = true)
    public static final class ErrorNoAOTTestExport {

        @ExportMessage
        void m0() {
        }

    }

    @GenerateAOT
    abstract static class ErrorLibraryAutoDispatchWithoutAOT extends BaseNode {

        @Specialization
        int doDefault(Object receiver,
                        @ExpectError("Failed to generate code for @GenerateAOT: @CachedLibrary with automatic dispatch%")//
                        @CachedLibrary(limit = "3") NoAOTTestLibrary lib) {
            return lib.m0(receiver);
        }

    }

    @GenerateAOT
    abstract static class ErrorLibraryManualDispatchWithoutAOT extends BaseNode {

        @Specialization(limit = "3")
        int doDefault(Object receiver,
                        @ExpectError("Failed to generate code for @GenerateAOT: Cached values in specializations included for AOT must not bind dynamic values%")//
                        @CachedLibrary("receiver") NoAOTTestLibrary lib) {
            return lib.m0(receiver);
        }

    }

    @ExpectError("The useForAOTPriority property must also be set for libraries used for AOT. See @ExportLibrary(useForAOTPriority=...) for details.")
    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true)
    public static final class ErrorNoExportPriority {

        @ExportMessage
        void m0() {
        }

    }

    @SuppressWarnings("unused")
    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    public static final class ErrorAcceptsExcluded {

        @ExpectError("Cannot use with @GenerateAOT.Exclude with the accepts message. The accepts message must always be usable for AOT.")
        @ExportMessage
        @GenerateAOT.Exclude
        static boolean accepts(ErrorAcceptsExcluded receiver) {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        int m0(Object arg) {
            return 0;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        int m1() {
            return 0;
        }

    }

    @SuppressWarnings("unused")
    @ExportLibrary(value = AOTTestLibrary.class, useForAOT = true, useForAOTPriority = 0)
    @ExportLibrary(value = OtherAOTTestLibrary.class, useForAOT = false)
    public static final class ErrorMultiExport implements TruffleObject {

        @SuppressWarnings("static-method")
        @ExportMessage(library = AOTTestLibrary.class, name = "m1")
        @ExportMessage(library = OtherAOTTestLibrary.class, name = "m2")
        int multiExport(@Cached("42") int cachedValue) {
            return 0;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        int m0(Object arg) {
            return 0;
        }

    }

    @GenerateAOT
    @GenerateUncached
    public abstract static class RecursiveNode extends BaseNode {

        @Specialization(limit = "3")
        @SuppressWarnings("unused")
        int doI32DerefHandle(Object arg, @CachedLibrary("arg") RecursiveLibrary lib) {
            return 42;
        }

    }

    @GenerateLibrary
    @GenerateAOT
    public abstract static class RecursiveLibrary extends Library {

        public abstract void m0(Object receiver);

    }

    @ExportLibrary(value = RecursiveLibrary.class, useForAOT = true, useForAOTPriority = 1)
    public static final class RecursiveExportingClass {

        @SuppressWarnings("unused")
        @ExportMessage
        void m0(@Cached RecursiveNode lib) {
        }
    }

    @Test
    public void testRecursionError2() {
        TestRootNode root = setup(RecursiveNodeGen.create());
        AbstractPolyglotTest.assertFails(() -> AOTSupport.prepareForAOT(root), AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("<-recursion-detected->"));
        });
    }

}
