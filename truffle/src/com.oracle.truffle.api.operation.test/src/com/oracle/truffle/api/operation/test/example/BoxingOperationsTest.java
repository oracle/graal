package com.oracle.truffle.api.operation.test.example;

import java.util.function.Consumer;

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
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.test.example.BoxingOperations.ObjectProducer;

public class BoxingOperationsTest {

    // TODO all of these tests should somehow check that e&s is not called more times
    // than it needs to

    private static RootCallTarget parse(Consumer<BoxingOperationsBuilder> parser) {
        return BoxingOperationsBuilder.create(OperationConfig.DEFAULT, parser) //
                        .getNodes().get(0) //
                        .createRootNode(null, "test") //
                        .getCallTarget();
    }

    @Test
    public void testCastsPrimToPrim() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginLongOperator();
            b.emitIntProducer();
            b.endLongOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.INT_AS_LONG_VALUE, root.call());
        }
    }

    @Test
    public void testCastsRefToPrim() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginLongOperator();
            b.emitRefBProducer();
            b.endLongOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.REF_B_AS_LONG_VALUE, root.call());
        }
    }

    @Test
    public void testCastsPrimToRef() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginStringOperator();
            b.emitBooleanProducer();
            b.endStringOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.BOOLEAN_AS_STRING_VALUE, root.call());
        }
    }

    @Test
    public void testCastsRefToRef() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginStringOperator();
            b.emitRefAProducer();
            b.endStringOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.REF_A_AS_STRING_VALUE, root.call());
        }
    }

    @Test
    public void testCastsChangePrim() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginLongOperator();
            b.beginObjectProducer();
            b.emitLoadArgument(0);
            b.endObjectProducer();
            b.endLongOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.INT_AS_LONG_VALUE, root.call(ObjectProducer.PRODUCE_INT));
        }

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.REF_B_AS_LONG_VALUE, root.call(ObjectProducer.PRODUCE_REF_B));
        }

        try {
            root.call(ObjectProducer.PRODUCE_BOOLEAN);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @Test
    public void testCastsChangeRef() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginStringOperator();
            b.beginObjectProducer();
            b.emitLoadArgument(0);
            b.endObjectProducer();
            b.endStringOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.BOOLEAN_AS_STRING_VALUE, root.call(ObjectProducer.PRODUCE_BOOLEAN));
        }

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.REF_A_AS_STRING_VALUE, root.call(ObjectProducer.PRODUCE_REF_A));
        }

        try {
            root.call(ObjectProducer.PRODUCE_INT);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @Test
    public void testCastsChangeSpecPrim() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginLongOperator();
            b.beginSpecializedObjectProducer();
            b.emitLoadArgument(0);
            b.endSpecializedObjectProducer();
            b.endLongOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.INT_AS_LONG_VALUE, root.call(ObjectProducer.PRODUCE_INT));
        }

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.REF_B_AS_LONG_VALUE, root.call(ObjectProducer.PRODUCE_REF_B));
        }

        try {
            root.call(ObjectProducer.PRODUCE_BOOLEAN);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @Test
    public void testCastsChangeSpecRef() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginStringOperator();
            b.beginSpecializedObjectProducer();
            b.emitLoadArgument(0);
            b.endSpecializedObjectProducer();
            b.endStringOperator();
            b.endReturn();

            b.publish();
        });

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.BOOLEAN_AS_STRING_VALUE, root.call(ObjectProducer.PRODUCE_BOOLEAN));
        }

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(BoxingTypeSystem.REF_A_AS_STRING_VALUE, root.call(ObjectProducer.PRODUCE_REF_A));
        }

        try {
            root.call(ObjectProducer.PRODUCE_INT);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
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

    @ImplicitCast
    static long castLong(ReferenceTypeB b) {
        return REF_B_AS_LONG_VALUE;
    }
}

@GenerateOperations(boxingEliminationTypes = {boolean.class, int.class, long.class})
@SuppressWarnings("unused")
final class BoxingOperations {

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
}
