package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;

abstract class ArgumentNode extends Node {
    final PanamaType type;

    ArgumentNode(PanamaType type) {
        this.type = type;
    }

    abstract Object execute(Object value) throws UnsupportedTypeException;

    abstract static class ToVOIDNode extends ArgumentNode {

        ToVOIDNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        Object doConvert(Object value,
                         @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            return null;
        }

        Object toJava() {
            return NativePointer.NULL;
        }
    }

    static abstract class ToINT8Node extends ArgumentNode {

        ToINT8Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        byte doConvert(Object value,
                       @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asByte(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    static abstract class ToINT16Node extends ArgumentNode {

        ToINT16Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        short doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asShort(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    static abstract class ToINT32Node extends ArgumentNode {

        ToINT32Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        int doConvert(Object value,
                      @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asInt(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    static abstract class ToINT64Node extends ArgumentNode {

        ToINT64Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3") //, rewriteOn = UnsupportedTypeException.class)
        long doConvert(Object value,
                       @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asLong(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
//        @Specialization(limit = "3", replaces = "doConvert")
//        Object nullConvert(Object value,
//                           @CachedLibrary("value") InteropLibrary interop) {
//            if (interop.isNull(value)) {
//                return null;
//            }
//            throw CompilerDirectives.shouldNotReachHere();
//        }
    }

    static abstract class ToPointerNode extends ArgumentNode {

        ToPointerNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3") //, rewriteOn = UnsupportedTypeException.class)
        long doConvert(Object value,
                       @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asPointer(value);
            } catch (UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static abstract class ToFLOATNode extends ArgumentNode {

        ToFLOATNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        float doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asFloat(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    static abstract class ToDOUBLENode extends ArgumentNode {

        ToDOUBLENode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        double doConvert(Object value,
                         @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asDouble(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToSTRINGNode extends ArgumentNode {

        ToSTRINGNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        MemoryAddress doConvert(Object value,
                                @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return MemorySession.global().allocateUtf8String(interop.asString(value)).address(); // TODO: remove global
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }
}