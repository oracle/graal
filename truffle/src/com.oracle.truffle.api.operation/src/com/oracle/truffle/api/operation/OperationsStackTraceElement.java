package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
final class OperationsStackTraceElement implements TruffleObject {

    private final SourceSection sourceSection;
    private final RootNode rootNode;

    public OperationsStackTraceElement(RootNode rootNode, SourceSection sourceSection) {
        this.rootNode = rootNode;
        this.sourceSection = sourceSection;
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return rootNode.getName() != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExecutableName() {
        return rootNode.getName();
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return sourceSection != null;
    }

    @ExportMessage
    SourceSection getSourceLocation() throws UnsupportedMessageException {
        if (sourceSection == null) {
            throw UnsupportedMessageException.create();
        } else {
            return sourceSection;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasDeclaringMetaObject() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getDeclaringMetaObject() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}