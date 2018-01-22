package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.PolyglotImpl.wrapGuestException;

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * TODO merge this with PolyglotValue.PolyglotNode
 */
final class PolyglotBoundaryRootNode extends RootNode {

    private static final Object UNINITIALIZED_CONTEXT = new Object();
    private final Supplier<String> name;

    @CompilationFinal private boolean seenEnter;
    @CompilationFinal private boolean seenNonEnter;

    @CompilationFinal private Object constantContext = UNINITIALIZED_CONTEXT;

    @Child private ExecutableNode executable;

    protected PolyglotBoundaryRootNode(Supplier<String> name, ExecutableNode executable) {
        super(null);
        this.name = name;
        this.executable = executable;
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        Object languageContext = profileContext(args[0]);
        PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
        boolean needsEnter = context.needsEnter();
        Object prev;
        if (needsEnter) {
            if (!seenEnter) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenEnter = true;
            }
            prev = context.enter();
        } else {
            if (!seenNonEnter) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNonEnter = true;
            }
            prev = null;
        }
        try {
            return executable.execute(frame);
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw wrapGuestException(((PolyglotLanguageContext) languageContext), e);
        } finally {
            if (needsEnter) {
                context.leave(prev);
            }
        }
    }

    private Object profileContext(Object languageContext) {
        if (constantContext != null) {
            if (constantContext == languageContext) {
                return constantContext;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (constantContext == UNINITIALIZED_CONTEXT) {
                    constantContext = languageContext;
                } else {
                    constantContext = null;
                }
            }
        }
        return languageContext;
    }

    @Override
    public final String getName() {
        return name.get();
    }

    @Override
    public final String toString() {
        return getName();
    }
}
