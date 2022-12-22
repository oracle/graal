package com.oracle.truffle.nfi.backend.panama;

import java.lang.foreign.ValueLayout;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

import com.oracle.truffle.nfi.backend.panama.PanamaType.ConvertNode;

class ArgumentNode extends Node {
    final PanamaType type;

    public ArgumentNode(PanamaType type) {
        this.type = type;
    }

    public Object execute(Object arg) throws UnsupportedTypeException {
        return type.convertNode.execute(arg);
    }
}
