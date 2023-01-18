package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.backend.panama.PanamaSignature.CachedSignatureInfo;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNode.GetArgumentNode;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNode.ConstArgumentNode;
import com.oracle.truffle.nfi.backend.panama.PanamaClosureFactory.CallClosureNodeGen;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@ExportLibrary(InteropLibrary.class)
final class PanamaClosure implements TruffleObject {
    final MemoryAddress symbol;

    PanamaClosure(MemoryAddress symbol) {
        this.symbol = symbol;
    }

    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage
    long asPointer() {
        return symbol.toRawLongValue();
    }


    abstract static class CachedClosureInfo {
        final CallTarget closureCallTarget;

        CachedClosureInfo(RootNode rootNode) {
            this.closureCallTarget = rootNode.getCallTarget();
        }
    }

    abstract static class MonomorphicClosureInfo extends CachedClosureInfo {
        public MethodHandle cachedHandle;

        private MonomorphicClosureInfo(RootNode rootNode) {
            super(rootNode);
        }

        static MonomorphicClosureInfo create(CachedSignatureInfo signatureInfo, Object executable) {
            CompilerAsserts.neverPartOfCompilation();
            PanamaNFILanguage lang = PanamaNFILanguage.get(null);
            PanamaType retType = signatureInfo.getRetType();
            ClosureArgumentNode recvNode = new ConstArgumentNode(executable);
            ClosureArgumentNode retBuffer = new GetArgumentNode(signatureInfo.argTypes.length);
            // TODO
//            .createUpcallHandle(language);
//            return base.asType(upcallType);
            if (retType.type == NativeSimpleType.STRING) {
                return StringRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else {
                return GenericRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            }
        }
    }

    @NodeChild(value = "receiver", type = ClosureArgumentNode.class)
    abstract static class CallClosureNode extends Node {

        protected abstract Object execute(VirtualFrame frame);

        @Children final ClosureArgumentNode[] argNodes;

        CallClosureNode(CachedSignatureInfo signature) {
            PanamaType[] args = signature.getArgTypes();
            argNodes = new ClosureArgumentNode[args.length];
            for (int i = 0; i < args.length; i++) {
                ClosureArgumentNode rawArg = new GetArgumentNode(i+1);
                argNodes[i] = args[i].createClosureArgumentNode(rawArg);
            }
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        Object doCall(VirtualFrame frame, Object receiver,
                      @CachedLibrary("receiver") InteropLibrary interop) {
            Object[] args = new Object[argNodes.length];
            for (int i = 0; i < argNodes.length; i++) {
                args[i] = argNodes[i].execute(frame);
            }

            try {
                return interop.execute(receiver, args);
            } catch (InteropException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    private static final class StringRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;
        @Child private InteropLibrary interopLibrary;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            RootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            CompilerAsserts.neverPartOfCompilation();

            CallTarget upcallTarget = rootNode.getCallTarget();
            MonomorphicClosureInfo info = new MonomorphicClosureInfo(rootNode) {};
            info.cachedHandle = handle_CallTarget_call.bindTo(upcallTarget).asCollector(Object[].class, 2).asType(METHOD_TYPE).asVarargsCollector(Object[].class);
            return info;
        }

        private StringRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame);
            if (interopLibrary.isNull(ret)) {
                return null;
            }
            // TODO NEW:
            Object receiver = frame.getArguments()[0];
            Object[] args = (Object[]) frame.getArguments()[1];
            try {
                Object result = interop.execute(receiver, args);
                if (result instanceof String) {
                    return MemorySession.global().allocateUtf8String((String) result).address();
                } else {
                    return result;
                }
            } catch (InteropException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    public static final class GenericRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;
        @Child private InteropLibrary interopLibrary;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signature, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            GenericRetClosureRootNode rootNode = new GenericRetClosureRootNode(lang, signature, recvNode);
            CompilerAsserts.neverPartOfCompilation();

            CallTarget upcallTarget = rootNode.getCallTarget();
            MonomorphicClosureInfo info = new MonomorphicClosureInfo(rootNode) {};
            info.cachedHandle = handle_CallTarget_call.bindTo(upcallTarget).asCollector(Object[].class, 2).asType(METHOD_TYPE).asVarargsCollector(Object[].class);
            return info;
        }

        private GenericRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
            // TODO check if RetNode necessary signature.getRetType()
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame);
            if (interopLibrary.isNull(ret)) {
                return null;
            }
            return ret;
        }
    }
}
