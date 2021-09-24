package com.oracle.truffle.llvm.tests.interop;

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback;

public class PolyglotArrayToNativeTest extends PolyglotArrayTestBase {
    public static final String PAYLOAD = "payload";
    public static final int ARRAY_SIZE = 10;
    private static Value polyglotReadPointerLibrary;
    private static Object pointerTypeId;
    private static Value getArrayType;

    /**
     * Load bitcode library. Method name needs to be different than in the base class, otherwise it
     * is not executed.
     */
    @BeforeClass
    public static void loadPolygloArrayToNativeLibrary() {
        polyglotReadPointerLibrary = loadTestBitcodeValue("polyglotArrayToNative.c");
        Value lib = runWithPolyglot.getPolyglotContext().asValue(polyglotReadPointerLibrary);
        Value getTypes = lib.getMember("get_Simple_typeid");

        getTypes.execute(new TestCallback(1, args -> {
            pointerTypeId = args[0];
            return null;
        }));

        getArrayType = lib.getMember("get_Simple_array_typeid");

    }

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    @SuppressWarnings("static-method")
    static class SimpleWrapper implements TruffleObject {
        private final long delegate;

        private SimpleWrapper(long l) {
            this.delegate = l;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean b) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @ExportMessage
        boolean isMemberReadable(String key) {
            return PAYLOAD.equals(key);
        }

        @ExportMessage
        Object readMember(String key) throws UnknownIdentifierException {
            if (PAYLOAD.equals(key)) {
                return delegate;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnknownIdentifierException.create(key);
        }

        @ExportMessage
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        void toNative() {
            // nop
        }

        @ExportMessage
        long asPointer() {
            return delegate;
        }

        @ExportMessage
        boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        Object getNativeType() {
            return pointerTypeId;
        }
    }

    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class SimpleArrayWrapper implements TruffleObject {
        private final SimpleWrapper[] objects;

        private SimpleArrayWrapper(SimpleWrapper[] objects) {
            this.objects = objects;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < objects.length;
        }

        @ExportMessage
        long getArraySize() {
            return objects.length;
        }

        @ExportMessage
        Object readArrayElement(long idx) {
            return objects[(int) idx];
        }

        @ExportMessage
        boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        Object getNativeType() {
            try {
                Object[] result = new Object[1];
                getArrayType.execute((long) objects.length, new TestCallback(1, args -> {
                    result[0] = args[0];
                    return null;
                }));
                return result[0];
            } catch (AbstractTruffleException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Test
    public void test() {
        Value read = polyglotReadPointerLibrary.getMember("simple_array_to_native");
        Assert.assertNotNull("Function not found: simple_array_to_native", read);
        SimpleWrapper[] objects = new SimpleWrapper[ARRAY_SIZE];
        for (int i = 0; i < ARRAY_SIZE; i++) {
            objects[i] = new SimpleWrapper(i);
        }
        Value ret = read.execute(new SimpleArrayWrapper(objects), (long) ARRAY_SIZE);
        Assert.assertTrue(ret.hasArrayElements());
        Assert.assertEquals(ARRAY_SIZE, ret.getArraySize());
        /*
         * 'ret' is of type 'Simple[]'; and 'Simple' is a struct with one field 'void *payload'. We
         * want to have the int value of the pointer.
         */
        for (int i = 0; i < ARRAY_SIZE; i++) {
            Value arrayElement = ret.getArrayElement(i);
            Assert.assertTrue(arrayElement.hasMember(PAYLOAD));
            Value payload = arrayElement.getMember(PAYLOAD);
            Assert.assertTrue(payload.isNativePointer());
            Assert.assertEquals(i, payload.asNativePointer());
        }
    }
}