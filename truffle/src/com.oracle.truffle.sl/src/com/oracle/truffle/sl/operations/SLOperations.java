package com.oracle.truffle.sl.operations;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.Variadic;
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
import com.oracle.truffle.sl.nodes.util.SLToMemberNode;
import com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode;
import com.oracle.truffle.sl.nodes.util.SLUnboxNode;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateOperations
public class SLOperations {

    @Operation(proxyNode = SLAddNode.class)
    public static class SLAddOperation {
    }

    @Operation(proxyNode = SLDivNode.class)
    public static class SLDivOperation {
    }

    @Operation(proxyNode = SLEqualNode.class)
    public static class SLEqualOperation {
    }

    public static class SLInvokeOperation {
        @Specialization
        public static Object execute(Object function, @Variadic Object[] argumentValues, @CachedLibrary(limit = "3") InteropLibrary library) {
            try {
                return library.execute(function, argumentValues);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                /* Execute was not successful. */
                throw SLUndefinedNameException.undefinedFunction(null, function);
            }
        }
    }

    @Operation(proxyNode = SLLessOrEqualNode.class)
    public static class SLLessOrEqualOperation {
    }

    @Operation(proxyNode = SLLessThanNode.class)
    public static class SLLessThanOperation {
    }

    @Operation(proxyNode = SLLogicalNotNode.class)
    public static class SLLogicalNotOperation {
    }

    @Operation(proxyNode = SLMulNode.class)
    public static class SLMulOperation {
    }

    @Operation(proxyNode = SLReadPropertyNode.class)
    public static class SLReadPropertyOperation {
    }

    @Operation(proxyNode = SLSubNode.class)
    public static class SLSubOperation {
    }

    @Operation(proxyNode = SLWritePropertyNode.class)
    public static class SLWritePropertyOperation {
    }

    @Operation(proxyNode = SLUnboxNode.class)
    public static class SLUnboxOperation {
    }
}
