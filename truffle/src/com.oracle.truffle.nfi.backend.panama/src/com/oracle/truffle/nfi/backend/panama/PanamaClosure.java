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
import com.oracle.truffle.api.interop.UnsupportedTypeException;
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
            // TODO implement other return types
            if (retType.type == NativeSimpleType.STRING) {
                return StringRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else if (retType.type == NativeSimpleType.VOID) {
                return VoidRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else {
                return GenericRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            }
        }
    }

    abstract static class PolymorphicClosureInfo extends CachedClosureInfo {
        public MethodHandle cachedHandle;

        private PolymorphicClosureInfo(RootNode rootNode) {
            super(rootNode);
        }

        static PolymorphicClosureInfo create(CachedSignatureInfo signatureInfo) {
            CompilerAsserts.neverPartOfCompilation();
            PanamaNFILanguage lang = PanamaNFILanguage.get(null);
            PanamaType retType = signatureInfo.getRetType();
            if (retType.type == NativeSimpleType.STRING) {
                return StringRetClosureRootNode.createInfo(lang, signatureInfo);
            } else if (retType.type == NativeSimpleType.VOID) {
                return VoidRetClosureRootNode.createInfo(lang, signatureInfo);
            } else {
                return GenericRetClosureRootNode.createInfo(lang, signatureInfo);
            }
        }
    }

    abstract static class PanamaClosureRootNode extends RootNode {

        // Object closure(Object receiver, Object[] args)
        static final MethodType METHOD_TYPE = MethodType.methodType(Object.class, Object.class, Object[].class);

        @Child InteropLibrary interop;

        PanamaClosureRootNode(PanamaNFILanguage language) {
            super(language);
            this.interop = InteropLibrary.getFactory().createDispatched(3);
        }

        static final MethodHandle handle_CallTarget_call;

        MonomorphicClosureInfo createMonomorphicClosureInfo() {
            CompilerAsserts.neverPartOfCompilation();
            CallTarget upcallTarget = getCallTarget();
            MonomorphicClosureInfo info = new MonomorphicClosureInfo(this) {};
            info.cachedHandle = handle_CallTarget_call.bindTo(upcallTarget)
                    .asCollector(Object[].class, 2).asType(METHOD_TYPE)
                    .asVarargsCollector(Object[].class);
            return info;
        }

        PolymorphicClosureInfo createPolymorphicClosureInfo() {
            CompilerAsserts.neverPartOfCompilation();
            CallTarget upcallTarget = getCallTarget();
            PolymorphicClosureInfo info = new PolymorphicClosureInfo(this) {};
            info.cachedHandle = handle_CallTarget_call.bindTo(upcallTarget)
                    .asCollector(Object[].class, 2).asType(METHOD_TYPE)
                    .asVarargsCollector(Object[].class);
            return info;
        }

        static {
            MethodType callType = MethodType.methodType(Object.class, Object[].class);
            try {
                handle_CallTarget_call = MethodHandles.lookup().findVirtual(CallTarget.class, "call", callType);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
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
                ClosureArgumentNode rawArg = new GetArgumentNode(i);
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
        @Child ArgumentNode toJavaRet;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            PanamaClosureRootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createMonomorphicClosureInfo();
        }

        static PolymorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            PanamaClosureRootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createPolymorphicClosureInfo();
        }

        private StringRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            toJavaRet = signature.retType.createArgumentNode();
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame);
            if (interopLibrary.isNull(ret)) {
                return null;
            }
            try {
                return toJavaRet.execute(ret);
            } catch (UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    private static final class VoidRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            PanamaClosureRootNode rootNode = new VoidRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createMonomorphicClosureInfo();
        }

        static PolymorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            PanamaClosureRootNode rootNode = new VoidRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createPolymorphicClosureInfo();
        }

        private VoidRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            callClosure.execute(frame);
            return null;
        }
    }

    @NodeChild(type = ClosureArgumentNode.class)
    public static final class GenericRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;
        @Child ArgumentNode toJavaRet;
        @Child private InteropLibrary interopLibrary;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signature, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            PanamaClosureRootNode rootNode = new GenericRetClosureRootNode(lang, signature, recvNode);
            return rootNode.createMonomorphicClosureInfo();
        }

        static PolymorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signature) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signature.argTypes.length);
            PanamaClosureRootNode rootNode = new GenericRetClosureRootNode(lang, signature, recvNode);
            return rootNode.createPolymorphicClosureInfo();
        }

        private GenericRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            toJavaRet = signature.retType.createArgumentNode();
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame);
            if (interopLibrary.isNull(ret)) {
                return NativePointer.NULL.asPointer();
            }
            try {
                return toJavaRet.execute(ret);
            } catch (UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}
