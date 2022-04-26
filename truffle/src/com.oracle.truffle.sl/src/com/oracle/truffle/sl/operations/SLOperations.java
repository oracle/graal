package com.oracle.truffle.sl.operations;

import java.util.Collections;
import java.util.Map;

import com.oracle.truffle.api.Assumption;
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
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLException;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLTypes;
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

@GenerateOperations(decisionsFile = "decisions.xml")
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

        builder.beginReturn();
        builder.beginSLEvalRootOperation();
        builder.emitConstObject(Collections.unmodifiableMap(targets));
        builder.endSLEvalRootOperation();
        builder.endReturn();

        builder.build();
    }

    @Operation
    public static class SLEvalRootOperation {

        @Specialization
        public static Object doExecute(
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

        @Fallback
        public static Object fallback(Object ignored) {
            throw new RuntimeException("");
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLFunctionLiteralOperation {
        @Specialization
        public static SLFunction perform(
                        TruffleString functionName,
                        @Cached("lookupFunction(functionName, this)") SLFunction result) {
            assert result.getName().equals(functionName) : "functionName should be a compile-time constant";
            return result;
        }

        static SLFunction lookupFunction(TruffleString functionName, Node node) {
            return SLContext.get(node).getFunctionRegistry().lookup(functionName, true);
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLInvokeOperation {
        @Specialization(limit = "3", //
                        guards = "function.getCallTarget() == cachedTarget", //
                        assumptions = "callTargetStable")
        @SuppressWarnings("unused")
        protected static Object doDirect(SLFunction function, @Variadic Object[] arguments,
                        @Cached("function.getCallTargetStable()") Assumption callTargetStable,
                        @Cached("function.getCallTarget()") RootCallTarget cachedTarget,
                        @Cached("create(cachedTarget)") DirectCallNode callNode) {

            /* Inline cache hit, we are safe to execute the cached call target. */
            Object returnValue = callNode.call(arguments);
            return returnValue;
        }

        /**
         * Slow-path code for a call, used when the polymorphic inline cache exceeded its maximum
         * size specified in <code>INLINE_CACHE_SIZE</code>. Such calls are not optimized any
         * further, e.g., no method inlining is performed.
         */
        @Specialization(replaces = "doDirect")
        protected static Object doIndirect(SLFunction function, @Variadic Object[] arguments,
                        @Cached IndirectCallNode callNode) {
            /*
             * SL has a quite simple call lookup: just ask the function for the current call target,
             * and call it.
             */
            return callNode.call(function.getCallTarget(), arguments);
        }

        @Specialization
        protected static Object doInterop(
                        Object function,
                        @Variadic Object[] arguments,
                        @CachedLibrary(limit = "3") InteropLibrary library,
                        @Bind("this") Node node,
                        @Bind("$bci") int bci) {
            try {
                return library.execute(function, arguments);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                /* Execute was not successful. */
                throw SLUndefinedNameException.undefinedFunction(node, bci, function);
            }
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLConvertToBoolean {
        @Specialization
        public static boolean doBoolean(boolean obj) {
            return obj;
        }

        @Fallback
        public static void fallback(Object obj, @Bind("this") Node node, @Bind("$bci") int bci) {
            throw SLException.typeError(node, bci, obj);
        }
    }
}
