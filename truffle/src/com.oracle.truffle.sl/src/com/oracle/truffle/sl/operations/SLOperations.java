package com.oracle.truffle.sl.operations;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.Special;
import com.oracle.truffle.api.operation.Special.SpecialKind;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLException;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.SLTypesGen;
import com.oracle.truffle.sl.nodes.controlflow.SLDebuggerNode;
import com.oracle.truffle.sl.nodes.expression.SLAddNode;
import com.oracle.truffle.sl.nodes.expression.SLDivNode;
import com.oracle.truffle.sl.nodes.expression.SLEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLLessOrEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLLessThanNode;
import com.oracle.truffle.sl.nodes.expression.SLLogicalNotNode;
import com.oracle.truffle.sl.nodes.expression.SLMulNode;
import com.oracle.truffle.sl.nodes.expression.SLReadPropertyNode;
import com.oracle.truffle.sl.nodes.expression.SLSubNode;
import com.oracle.truffle.sl.nodes.expression.SLWritePropertyNode;
import com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode;
import com.oracle.truffle.sl.nodes.util.SLUnboxNode;
import com.oracle.truffle.sl.parser.operations.SLOperationsVisitor;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLStrings;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateOperations
public class SLOperations {

    public static void parse(SLLanguage language, Source source, SLOperationsBuilder builder) {
        Map<TruffleString, RootCallTarget> targets = SLOperationsVisitor.parseSL(language, source, builder);

        // create the RootNode

        builder.setInternal(); // root node is internal
        builder.beginReturn();
        builder.beginSLEvalRootOperation();
        targets.forEach((name, call) -> {
            builder.emitConstObject(name);
            builder.emitConstObject(call);
        });
        builder.endSLEvalRootOperation();
        builder.endReturn();

        builder.build();
    }

    @Operation
    public static class SLEvalRootOperation {
        @Specialization
        public static Object perform(
                        @Variadic Object[] children,
                        @Special(SpecialKind.NODE) Node node,
                        @Special(SpecialKind.ARGUMENTS) Object[] arguments) {
            // This should get lazily executed

            Map<TruffleString, RootCallTarget> functions = new HashMap<>();
            CallTarget main = null;

            for (int i = 0; i < children.length; i += 2) {
                TruffleString name = (TruffleString) children[i];
                RootCallTarget target = (RootCallTarget) children[i + 1];
                if (name.equals(SLStrings.MAIN)) {
                    main = target;
                }
                functions.put(name, target);
            }

            SLContext.get(node).getFunctionRegistry().register(functions);

            if (main != null) {
                return main.call(arguments);
            } else {
                return SLNull.SINGLETON;
            }
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLAddOperation {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long addLong(long left, long right, @Special(SpecialKind.NODE) Node node) {
            return Math.addExact(left, right);
        }

        @Specialization(replaces = "addLong")
        @TruffleBoundary
        public static SLBigNumber add(SLBigNumber left, SLBigNumber right, @Special(SpecialKind.NODE) Node node) {
            return new SLBigNumber(left.getValue().add(right.getValue()));
        }

        @Specialization(guards = "isString(left, right)")
        @TruffleBoundary
        public static TruffleString add(Object left, Object right,
                        @Special(SpecialKind.NODE) Node node,
                        @Cached SLToTruffleStringNode toTruffleStringNodeLeft,
                        @Cached SLToTruffleStringNode toTruffleStringNodeRight,
                        @Cached TruffleString.ConcatNode concatNode) {
            return concatNode.execute(toTruffleStringNodeLeft.execute(left), toTruffleStringNodeRight.execute(right), SLLanguage.STRING_ENCODING, true);
        }

        public static boolean isString(Object a, Object b) {
            return a instanceof TruffleString || b instanceof TruffleString;
        }

        @Fallback
        public static Object typeError(Object left, Object right, @Special(SpecialKind.NODE) Node node) {
            throw SLException.typeError(node, left, right);
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLDivOperation {

        @Specialization(rewriteOn = ArithmeticException.class)
        public static long divLong(long left, long right, @Special(SpecialKind.NODE) Node node) throws ArithmeticException {
            long result = left / right;
            /*
             * The division overflows if left is Long.MIN_VALUE and right is -1.
             */
            if ((left & right & result) < 0) {
                throw new ArithmeticException("long overflow");
            }
            return result;
        }

        @Specialization(replaces = "divLong")
        @TruffleBoundary
        public static SLBigNumber div(SLBigNumber left, SLBigNumber right, @Special(SpecialKind.NODE) Node node) {
            return new SLBigNumber(left.getValue().divide(right.getValue()));
        }

        @Fallback
        public static Object typeError(Object left, Object right, @Special(SpecialKind.NODE) Node node) {
            throw SLException.typeError(node, left, right);
        }
    }

    @Operation(proxyNode = SLEqualNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLEqualOperation {
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLFunctionLiteralOperation {
        @Specialization
        public static Object execute(TruffleString functionName, @Special(SpecialKind.NODE) Node node) {
            Object res = SLContext.get(node).getFunctionRegistry().lookup(functionName, true);
            return res;
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLInvokeOperation {
        @Specialization
        public static Object execute(Object function, @Variadic Object[] argumentValues, @Special(SpecialKind.NODE) Node node, @CachedLibrary(limit = "3") InteropLibrary library) {
            try {
                return library.execute(function, argumentValues);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                /* Execute was not successful. */
                throw SLUndefinedNameException.undefinedFunction(node, function);
            }
        }
    }

    @Operation(proxyNode = SLLessOrEqualNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLLessOrEqualOperation {
    }

    @Operation(proxyNode = SLLessThanNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLLessThanOperation {
    }

    @Operation(proxyNode = SLLogicalNotNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLLogicalNotOperation {
    }

    @Operation(proxyNode = SLMulNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLMulOperation {
    }

    @Operation(proxyNode = SLReadPropertyNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLReadPropertyOperation {
    }

    @Operation(proxyNode = SLSubNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLSubOperation {
    }

    @Operation(proxyNode = SLWritePropertyNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLWritePropertyOperation {
    }

    @Operation(proxyNode = SLUnboxNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLUnboxOperation {
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLConvertToBoolean {
        @Specialization
        public static boolean perform(Object obj, @Special(SpecialKind.NODE) Node node) {
            try {
                return SLTypesGen.expectBoolean(obj);
            } catch (UnexpectedResultException e) {
                throw SLException.typeError(node, e.getResult());
            }
        }
    }
}
