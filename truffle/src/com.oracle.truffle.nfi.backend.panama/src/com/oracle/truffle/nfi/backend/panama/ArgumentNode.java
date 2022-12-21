package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

abstract class ArgumentNode extends Node {
    final PanamaType type;

    public ArgumentNode(PanamaType type) {
        this.type = type;
    }

    public abstract Object execute(Object arg) throws UnsupportedTypeException;

    @Specialization
    public Object intToPrimitive(Object arg,
                   @CachedLibrary(limit = "1") InteropLibrary argLib) {
        try {
            return argLib.asInt(arg); // TODO Implement other types
        } catch (UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }
}
