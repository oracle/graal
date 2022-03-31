package com.oracle.truffle.sl.operations;

import java.util.Collections;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLException;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.SLTypesGen;
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
import com.oracle.truffle.sl.nodes.util.SLUnboxNode;
import com.oracle.truffle.sl.parser.operations.SLOperationsVisitor;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLStrings;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateOperations
@TypeSystemReference(SLTypes.class)
@OperationProxy(SLAddNode.class)
@OperationProxy(SLDivNode.class)
@OperationProxy(SLEqualNode.class)
@OperationProxy(SLLessOrEqualNode.class)
@OperationProxy(SLLessThanNode.class)
@OperationProxy(SLLogicalNotNode.class)
@OperationProxy(SLMulNode.class)
@OperationProxy(SLReadPropertyNode.class)
@OperationProxy(SLSubNode.class)
@OperationProxy(SLWritePropertyNode.class)
@OperationProxy(SLUnboxNode.class)
public class SLOperations {

    public static void parse(SLLanguage language, Source source, SLOperationsBuilder builder) {
        Map<TruffleString, RootCallTarget> targets = SLOperationsVisitor.parseSL(language, source, builder);

        // create the RootNode

        builder.setInternal(); // root node is internal
        builder.beginReturn();
        builder.beginSLEvalRootOperation();
        builder.emitConstObject(Collections.unmodifiableMap(targets));
        builder.endSLEvalRootOperation();
        builder.endReturn();

        builder.build();
    }

    @Operation
    public static class SLEvalRootOperation {
        @SuppressWarnings("unchecked")
        @Specialization
        public static Object perform(
                        VirtualFrame frame,
                        Map<TruffleString, RootCallTarget> functions,
                        @Bind("this") Node node) {
            SLContext.get(node).getFunctionRegistry().register(functions);

            RootCallTarget main = functions.get(SLStrings.MAIN);

            if (main != null) {
                return main.call(frame.getArguments());
            } else {
                return SLNull.SINGLETON;
            }
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLFunctionLiteralOperation {
        @Specialization
        public static Object execute(TruffleString functionName, @Cached("lookupFunction(functionName, this)") Object result) {
            return result;
        }

        static Object lookupFunction(TruffleString functionName, Node node) {
            return SLContext.get(node).getFunctionRegistry().lookup(functionName, true);
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLInvokeOperation {
        @Specialization(guards = {"function.getCallTarget() == callTarget"})
        public static Object executeSL(
                        SLFunction function,
                        @Variadic Object[] argumentValues,
                        @Cached("function.getCallTarget()") RootCallTarget callTarget,
                        @Cached("create(callTarget)") DirectCallNode dcn) {
            return dcn.call(argumentValues);
        }

        @Specialization
        public static Object execute(
                        Object function,
                        @Variadic Object[] argumentValues,
                        @Bind("this") Node node,
                        @Bind("$bci") int bci,
                        @CachedLibrary(limit = "3") InteropLibrary library) {
            try {
                return library.execute(function, argumentValues);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                /* Execute was not successful. */
                throw SLUndefinedNameException.undefinedFunction(node, bci, function);
            }
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLConvertToBoolean {
        @Specialization
        public static boolean perform(Object obj, @Bind("this") Node node, @Bind("$bci") int bci) {
            try {
                return SLTypesGen.expectBoolean(obj);
            } catch (UnexpectedResultException e) {
                throw SLException.typeError(node, bci, e.getResult());
            }
        }
    }
}
