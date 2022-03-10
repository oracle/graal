package com.oracle.truffle.api.operation.test.slexample;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.util.SLToMemberNode;
import com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLObject;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateOperations
public class SlOperations {

    @Operation
    public static class SLAddOperation {
        @Specialization(rewriteOn = ArithmeticException.class)
        public static long add(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization(replaces = "add")
        @TruffleBoundary
        public static SLBigNumber addBig(SLBigNumber left, SLBigNumber right) {
            return new SLBigNumber(left.getValue().add(right.getValue()));
        }

        @Specialization(guards = "isString(left, right)")
        @TruffleBoundary
        public static TruffleString addString(Object left, Object right,
                        @Cached SLToTruffleStringNode toTruffleStringNodeLeft,
                        @Cached SLToTruffleStringNode toTruffleStringNodeRight,
                        @Cached TruffleString.ConcatNode concatNode) {
            return concatNode.execute(
                            toTruffleStringNodeLeft.execute(left),
                            toTruffleStringNodeRight.execute(right),
                            SLLanguage.STRING_ENCODING,
                            true);
        }

        public static boolean isString(Object a, Object b) {
            return a instanceof TruffleString || b instanceof TruffleString;
        }

        @Fallback
        public static Object typeError(Object left, Object right) {
            throw new RuntimeException("+ type error: " + left + ", " + right);
        }
    }

    @Operation
    public static class SLReadPropertyOperation {

        public static final int LIBRARY_LIMIT = 3;

        @Specialization(guards = "arrays.hasArrayElements(receiver)", limit = "3")
        public static Object readArray(Object receiver, Object index,
                        @CachedLibrary("receiver") InteropLibrary arrays,
                        @CachedLibrary("index") InteropLibrary numbers) {
            try {
                return arrays.readArrayElement(receiver, numbers.asLong(index));
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                // read was not successful. In SL we only have basic support for errors.
                throw SLUndefinedNameException.undefinedProperty(null, index);
            }
        }

        @Specialization(limit = "3")
        public static Object readSLObject(SLObject receiver, Object name,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary,
                        @Cached SLToTruffleStringNode toTruffleStringNode) {
            TruffleString nameTS = toTruffleStringNode.execute(name);
            Object result = objectLibrary.getOrDefault(receiver, nameTS, null);
            if (result == null) {
                // read was not successful. In SL we only have basic support for errors.
                throw SLUndefinedNameException.undefinedProperty(null, nameTS);
            }
            return result;
        }

        @Specialization(guards = {"!isSLObject(receiver)", "objects.hasMembers(receiver)"}, limit = "3")
        public static Object readObject(Object receiver, Object name,
                        @CachedLibrary("receiver") InteropLibrary objects,
                        @Cached SLToMemberNode asMember) {
            try {
                return objects.readMember(receiver, asMember.execute(name));
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // read was not successful. In SL we only have basic support for errors.
                throw SLUndefinedNameException.undefinedProperty(null, name);
            }
        }

        protected static boolean isSLObject(Object receiver) {
            return receiver instanceof SLObject;
        }
    }

    @Operation
    public static class SLInvokeNode {

        // @Child private SLExpressionNode functionNode;
        // @Children private final SLExpressionNode[] argumentNodes;
        // @Child private InteropLibrary library;
        //
        // public SLInvokeNode(SLExpressionNode functionNode, SLExpressionNode[] argumentNodes) {
        // this.functionNode = functionNode;
        // this.argumentNodes = argumentNodes;
        // this.library = InteropLibrary.getFactory().createDispatched(3);
        // }

        @ExplodeLoop
        @Specialization
        public static Object call(
                        Object function,
                        @Variadic Object[] argumentValues,
                        @CachedLibrary(limit = "3") InteropLibrary library) {
            try {
                return library.execute(function, argumentValues);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                /* Execute was not successful. */
                throw SLUndefinedNameException.undefinedFunction(null, function);
            }
        }

    }
}
