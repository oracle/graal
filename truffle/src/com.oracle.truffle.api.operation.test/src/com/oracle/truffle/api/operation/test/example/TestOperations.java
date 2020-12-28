package com.oracle.truffle.api.operation.test.example;

import static com.oracle.truffle.api.operation.Operation.Kind.CONDITIONAL;
import static com.oracle.truffle.api.operation.Operation.Kind.LOCAL;
import static com.oracle.truffle.api.operation.Operation.Kind.META;
import static com.oracle.truffle.api.operation.Operation.Kind.NODE;
import static com.oracle.truffle.api.operation.Operation.Kind.PROFILE;
import static com.oracle.truffle.api.operation.Operation.Kind.REPEATING;
import static com.oracle.truffle.api.operation.Operation.Kind.RETURN;
import static com.oracle.truffle.api.operation.Operation.Kind.SOURCE;
import static com.oracle.truffle.api.operation.Operation.Kind.TAG;
import static com.oracle.truffle.api.operation.Operation.Kind.UNWIND;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.Operation.EndInput;
import com.oracle.truffle.api.operation.Operation.LocalInput;
import com.oracle.truffle.api.operation.Operation.OnBuild;
import com.oracle.truffle.api.operation.Operation.OnEnter;
import com.oracle.truffle.api.operation.Operation.OnExecute;
import com.oracle.truffle.api.operation.Operation.StartInput;
import com.oracle.truffle.api.operation.Operation.StaticInput;
import com.oracle.truffle.api.operation.OperationPointer;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateOperations
class TestOperations {

    @Operation(value = LOCAL)
    static class Body {

        @OnEnter
        static int[] locals(VirtualFrame frame, @LocalInput int[] locals,
                        @EndInput int localCount,
                        @EndInput int argumentCount) {
            assert locals == null : "locals alraedy created";
            int[] newLocals = new int[localCount];
            Object[] arguments = frame.getArguments();
            for (int i = 0; i < argumentCount; i++) {
                newLocals[i] = (int) arguments[i];
            }
            return newLocals;
        }
    }

    @Operation(NODE)
    static class ReadLocal {

        @OnExecute
        static int doDefault(@LocalInput int[] locals,
                        @StartInput int localIndex) {
            return locals[localIndex];
        }
    }

    @Operation(NODE)
    static class WriteLocal {

        @OnExecute
        static void doDefault(@LocalInput int[] locals,
                        @StartInput short localIndex,
                        int value) {
            locals[localIndex] = value;
        }
    }

    @Operation(META)
    static class LocalNames {

        @OnExecute
        static String[] localNames(@EndInput String[] localNames) {
            // validate ?
            return localNames;
        }
    }

    @Operation(REPEATING)
    static class While {

        @OnExecute
        static boolean doDefault(boolean condition) {
            return condition;
        }
    }

    @Operation(CONDITIONAL)
    static class If {

        @OnExecute
        static boolean execute(boolean condition) {
            return condition;
        }
    }

    @Operation(UNWIND)
    static class BreakWhile {

        @OnBuild
        static OperationPointer skipLoops(@StaticInput OperationPointer current, @StartInput int skipIndex) {
            int whileIndex = 0;
            while (current.parent()) {
                if (current.get() == TestOperations.While.class) {
                    if (whileIndex == skipIndex) {
                        break;
                    }
                    whileIndex++;
                }
            }
            return current;
        }

    }

    @Operation(RETURN)
    static class Return {

        @OnEnter
        static Object doDefault(Object v) {
            return v;
        }

    }

    @Operation(UNWIND)
    static class ContinueWhile extends TestOperations.BreakWhile {
        // inherited from BreakWhile

        @OnBuild
        static OperationPointer onBuild(@StaticInput OperationPointer current, @StartInput int skipIndex) {
            return BreakWhile.skipLoops(current, skipIndex);
        }
    }

    @Operation(PROFILE)
    static class Condition {
        @Specialization
        static boolean doCondition(boolean input, @Cached("createCountingProfile()") ConditionProfile profile) {
            return profile.profile(input);
        }
    }

    @Operation(SOURCE)
    static class Source {
    }

    @Operation(TAG)
    @ProvidedTags(StatementTag.class)
    static class Statement {
    }

    @Operation(TAG)
    @ProvidedTags(TestOperations.RootTag.class)
    static class RootTag {
    }

    @Operation(TAG)
    @ProvidedTags(RootBodyTag.class)
    static class RootBody {
    }

    @Operation(NODE)
    static class LessThan {

        @OnExecute
        static boolean doDefault(int left, int right) {
            return left < right;
        }
    }

    @Operation(NODE)
    static class GreaterThan {

        @OnExecute
        static boolean doDefault(int left, int right) {
            return left > right;
        }

    }

    @Operation(NODE)
    static class ConstantInt {

        @OnExecute
        static int value(@StartInput int constant) {
            return constant;
        }

    }

    @Operation(NODE)
    static class Add {

        @OnExecute
        static int doAdd(int left, int right) {
            return left + right;
        }

    }

    @Operation(NODE)
    static class ConstantString {

        @OnExecute
        static String value(@StartInput String input) {
            return input;
        }

    }

    @Operation(NODE)
    static class NoOp {
    }

    @FunctionalInterface
    interface SourceOffsetProvider {

        int currentIndex(Object source);

    }

}