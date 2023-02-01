package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.Method;

@ExportLibrary(InteropLibrary.class)
public final class ForeignStackTraceElementObject implements TruffleObject {

    private final Method method;
    private final SourceSection sourceSection;

    public ForeignStackTraceElementObject(Method method, SourceSection sourceSection) {
        this.method = method;
        this.sourceSection = sourceSection;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    Object getExecutableName() {
        return method.getNameAsString();
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
        return true;
    }

    @ExportMessage
    Object getDeclaringMetaObject() {
        return method.getDeclaringKlass().mirror();
    }
}
