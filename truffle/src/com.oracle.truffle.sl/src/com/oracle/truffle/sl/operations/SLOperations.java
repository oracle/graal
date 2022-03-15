package com.oracle.truffle.sl.operations;

import java.util.List;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.TheNode;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.strings.TruffleString;
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
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateOperations
public class SLOperations {

    @Operation(proxyNode = SLAddNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLAddOperation {
    }

    @Operation(proxyNode = SLDivNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLDivOperation {
    }

    @Operation(proxyNode = SLEqualNode.class)
    @TypeSystemReference(SLTypes.class)
    public static class SLEqualOperation {
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static class SLFunctionLiteralOperation {
        @Specialization
        public static Object execute(TruffleString functionName, @TheNode Node node) {
            Object res = SLContext.get(node).getFunctionRegistry().lookup(functionName, true);
            return res;
        }
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
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
}
