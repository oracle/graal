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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameDescriptor.Builder;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.test.example.BoxingOperations.ObjectProducer;

public class BoxingOperationsTest {

    private static final BoxingLanguage LANGUAGE = null;

    private static final int NUM_ITERATIONS = 10_000;

    private static BoxingOperations parse(OperationParser<BoxingOperationsGen.Builder> parser) {
        OperationNodes<BoxingOperations> nodes = BoxingOperationsGen.create(OperationConfig.DEFAULT, parser);
        BoxingOperations node = nodes.getNodes().get(0);
        // System.out.println(node.dump());
        return node;
    }

    private static void testInvalidations(BoxingOperations node, int invalidations, Runnable r) {
        r.run();
        int totalInval = node.totalInvalidations;
        Assert.assertEquals(invalidations, totalInval);
    }

    @Test
    public void testCastsPrimToPrim() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLongOperator();
            b.emitIntProducer();
            b.endLongOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 1, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.INT_AS_LONG_VALUE, callTarget.call());
            }
        });
    }

    // @Test
    public void testCastsRefToPrim() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLongOperator();
            b.emitRefBProducer();
            b.endLongOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 1, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.REF_B_AS_LONG_VALUE, callTarget.call());
            }
        });
    }

    @Test
    public void testCastsPrimToRef() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginStringOperator();
            b.emitBooleanProducer();
            b.endStringOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 1, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.BOOLEAN_AS_STRING_VALUE, callTarget.call());
            }
        });
    }

    @Test
    public void testCastsRefToRef() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginStringOperator();
            b.emitRefAProducer();
            b.endStringOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 1, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.REF_A_AS_STRING_VALUE, callTarget.call());
            }
        });
    }

    @Test
    public void testCastsChangePrim() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLongOperator();
            b.beginObjectProducer();
            b.emitLoadArgument(0);
            b.endObjectProducer();
            b.endLongOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 4, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.INT_AS_LONG_VALUE, callTarget.call(ObjectProducer.PRODUCE_INT));
            }
            // for (int i = 0; i < NUM_ITERATIONS; i++) {
            // Assert.assertEquals(BoxingTypeSystem.REF_B_AS_LONG_VALUE,
            // callTarget.call(ObjectProducer.PRODUCE_REF_B));
            // }
            try {
                callTarget.call(ObjectProducer.PRODUCE_BOOLEAN);
                Assert.fail();
            } catch (UnsupportedSpecializationException e) {
            }
        });
    }

    @Test
    public void testCastsChangeRef() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginStringOperator();
            b.beginObjectProducer();
            b.emitLoadArgument(0);
            b.endObjectProducer();
            b.endStringOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 4, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.BOOLEAN_AS_STRING_VALUE, callTarget.call(ObjectProducer.PRODUCE_BOOLEAN));
            }

            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.REF_A_AS_STRING_VALUE, callTarget.call(ObjectProducer.PRODUCE_REF_A));
            }

            try {
                callTarget.call(ObjectProducer.PRODUCE_INT);
                Assert.fail();
            } catch (UnsupportedSpecializationException e) {
            }
        });
    }

    @Test
    public void testCastsChangeSpecPrim() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLongOperator();
            b.beginSpecializedObjectProducer();
            b.emitLoadArgument(0);
            b.endSpecializedObjectProducer();
            b.endLongOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 7, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.INT_AS_LONG_VALUE, callTarget.call(ObjectProducer.PRODUCE_INT));
            }

            // for (int i = 0; i < NUM_ITERATIONS; i++) {
            // Assert.assertEquals(BoxingTypeSystem.REF_B_AS_LONG_VALUE,
            // callTarget.call(ObjectProducer.PRODUCE_REF_B));
            // }

            try {
                callTarget.call(ObjectProducer.PRODUCE_BOOLEAN);
                Assert.fail();
            } catch (UnsupportedSpecializationException e) {
            }
        });
    }

    @Test
    public void testCastsChangeSpecRef() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginStringOperator();
            b.beginSpecializedObjectProducer();
            b.emitLoadArgument(0);
            b.endSpecializedObjectProducer();
            b.endStringOperator();
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 6, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.BOOLEAN_AS_STRING_VALUE, callTarget.call(ObjectProducer.PRODUCE_BOOLEAN));
            }
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(BoxingTypeSystem.REF_A_AS_STRING_VALUE, callTarget.call(ObjectProducer.PRODUCE_REF_A));
            }
            try {
                callTarget.call(ObjectProducer.PRODUCE_INT);
                Assert.fail();
            } catch (UnsupportedSpecializationException e) {
            }
        });
    }

    @Test
    public void testLBEMultipleLoads() {
        BoxingOperations root = parse(b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginLongOperator();
            b.emitLoadLocal(local);
            b.endLongOperator();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        RootCallTarget callTarget = root.getCallTarget();

        testInvalidations(root, 5, () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                Assert.assertEquals(1L, callTarget.call());
            }
        });
    }
}

class BoxingLanguage extends TruffleLanguage<BoxingContext> {
    @Override
    protected BoxingContext createContext(Env env) {
        return new BoxingContext();
    }

}

class BoxingContext {
}

class ReferenceTypeA {
}

class ReferenceTypeB {
}

@TypeSystem
@SuppressWarnings("unused")
class BoxingTypeSystem {

    public static final String STRING_VALUE = "<string>";
    public static final String BOOLEAN_AS_STRING_VALUE = "<bool>";
    public static final String REF_A_AS_STRING_VALUE = "<ref-a>";

    public static final long LONG_VALUE = 0xf00;
    public static final long INT_AS_LONG_VALUE = 0xba7;
    public static final long REF_B_AS_LONG_VALUE = 0xb00;

    @ImplicitCast
    static String castString(boolean b) {
        return BOOLEAN_AS_STRING_VALUE;
    }

    @ImplicitCast
    static String castString(ReferenceTypeA a) {
        return REF_A_AS_STRING_VALUE;
    }

    @ImplicitCast
    static long castLong(int i) {
        return INT_AS_LONG_VALUE;
    }

// @ImplicitCast
// static long castLong(ReferenceTypeB b) {
// return REF_B_AS_LONG_VALUE;
// }
}

@GenerateOperations(//
                languageClass = BoxingLanguage.class, //
                boxingEliminationTypes = {boolean.class, int.class, long.class})
@TypeSystemReference(BoxingTypeSystem.class)
@SuppressWarnings("unused")
abstract class BoxingOperations extends RootNode implements OperationRootNode {

    private static final boolean LOG = false;
    int totalInvalidations = 0;

    protected void transferToInterpreterAndInvalidate() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.totalInvalidations++;
        if (LOG) {
            System.err.println("[INVAL] --------------------");
            StackWalker.getInstance().forEach(sf -> {
                System.err.println("   " + sf);
            });
        }
    }

    protected BoxingOperations(TruffleLanguage<?> language, Builder frameDescriptor) {
        super(language, frameDescriptor.build());
    }

    protected BoxingOperations(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class IntProducer {
        @Specialization
        public static int produce() {
            return 1;
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class BooleanProducer {
        @Specialization
        public static boolean produce() {
            return true;
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class RefAProducer {
        @Specialization
        public static ReferenceTypeA produce() {
            return new ReferenceTypeA();
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class RefBProducer {
        @Specialization
        public static ReferenceTypeB produce() {
            return new ReferenceTypeB();
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class ObjectProducer {

        public static final short PRODUCE_INT = 0;
        public static final short PRODUCE_LONG = 1;
        public static final short PRODUCE_STRING = 2;
        public static final short PRODUCE_BOOLEAN = 3;
        public static final short PRODUCE_REF_A = 4;
        public static final short PRODUCE_REF_B = 5;

        @Specialization
        public static Object produce(short type) {
            switch (type) {
                case PRODUCE_INT:
                    return 1;
                case PRODUCE_LONG:
                    return BoxingTypeSystem.LONG_VALUE;
                case PRODUCE_STRING:
                    return BoxingTypeSystem.STRING_VALUE;
                case PRODUCE_BOOLEAN:
                    return true;
                case PRODUCE_REF_A:
                    return new ReferenceTypeA();
                case PRODUCE_REF_B:
                    return new ReferenceTypeB();
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class SpecializedObjectProducer {

        public static final short PRODUCE_INT = 0;
        public static final short PRODUCE_LONG = 1;
        public static final short PRODUCE_STRING = 2;
        public static final short PRODUCE_BOOLEAN = 3;
        public static final short PRODUCE_REF_A = 4;
        public static final short PRODUCE_REF_B = 5;

        @Specialization(guards = {"type == PRODUCE_INT"})
        public static int produceInt(short type) {
            return 1;
        }

        @Specialization(guards = {"type == PRODUCE_LONG"})
        public static long produceLong(short type) {
            return BoxingTypeSystem.LONG_VALUE;
        }

        @Specialization(guards = {"type == PRODUCE_STRING"})
        public static String produceString(short type) {
            return BoxingTypeSystem.STRING_VALUE;
        }

        @Specialization(guards = {"type == PRODUCE_BOOLEAN"})
        public static boolean produceBoolean(short type) {
            return true;
        }

        @Specialization(guards = {"type == PRODUCE_REF_A"})
        public static ReferenceTypeA produceRefA(short type) {
            return new ReferenceTypeA();
        }

        @Specialization(guards = {"type == PRODUCE_REF_B"})
        public static ReferenceTypeB produceRefB(short type) {
            return new ReferenceTypeB();
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class LongOperator {
        @Specialization
        public static long operate(long value) {
            return value;
        }
    }

    @Operation
    @TypeSystemReference(BoxingTypeSystem.class)
    public static final class StringOperator {
        @Specialization
        public static String operate(String value) {
            return value;
        }
    }

    @Operation
    public static final class SecondValueBoxingElim {
        @Specialization
        public static Object doInt(Object a, int b) {
            return null;
        }

        @Specialization
        public static Object doLong(Object a, long b) {
            return null;
        }

        @Specialization
        public static Object doGeneric(Object a, Object b) {
            return null;
        }
    }
}
