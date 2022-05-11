package com.oracle.truffle.sl.operations;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.GenerateOperations.Metadata;
import com.oracle.truffle.api.operation.MetadataKey;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.expression.SLAddNode;
import com.oracle.truffle.sl.nodes.expression.SLDivNode;
import com.oracle.truffle.sl.nodes.expression.SLEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLFunctionLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLLessOrEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLLessThanNode;
import com.oracle.truffle.sl.nodes.expression.SLLogicalNotNode;
import com.oracle.truffle.sl.nodes.expression.SLMulNode;
import com.oracle.truffle.sl.nodes.expression.SLReadPropertyNode;
import com.oracle.truffle.sl.nodes.expression.SLSubNode;
import com.oracle.truffle.sl.nodes.expression.SLWritePropertyNode;
import com.oracle.truffle.sl.nodes.util.SLToBooleanNode;
import com.oracle.truffle.sl.nodes.util.SLUnboxNode;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLStrings;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateOperations(//
                decisionsFile = "decisions.json", //
                boxingEliminationTypes = {long.class, boolean.class})
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
@OperationProxy(SLFunctionLiteralNode.class)
@OperationProxy(SLToBooleanNode.class)
public final class SLOperations {

    @Metadata("MethodName") //
    public static final MetadataKey<TruffleString> METHOD_NAME = new MetadataKey<>(SLStrings.EMPTY_STRING);

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static final class SLInvoke {
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
}
