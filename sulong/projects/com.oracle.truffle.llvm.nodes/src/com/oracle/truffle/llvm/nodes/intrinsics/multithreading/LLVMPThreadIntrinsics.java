package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMPThreadIntrinsics {

    public static PrintWriter debugOut = null;

    @CompilerDirectives.TruffleBoundary
    private static void printDebug(String str) {
        // create debug output tool
        if (debugOut == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                debugOut = new PrintWriter(new FileWriter("/home/florian/debug out"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        debugOut.println(str);
        debugOut.flush();
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCreate extends LLVMBuiltin {

        @Child LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object thread, Object attr, Object startRoutine, Object arg) {
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }

            // pointer to store the thread id
            LLVMPointer threadPtr = (LLVMPointer) thread; // is native

            // arguments are all pointers
            LLVMNativePointer attrPtr = (LLVMNativePointer) attr;
            LLVMManagedPointer functionPtr = (LLVMManagedPointer) startRoutine;
            LLVMNativePointer argPtr = (LLVMNativePointer) arg;

            // print arg types of function
            Type[] typeArr = ((LLVMFunctionDescriptor) functionPtr.getObject()).asFunction().getType().getArgumentTypes();
            for (Type t : typeArr) {
                printDebug("type: " + t.toString() + "\n");
            }

            // void pointer arg is type i8*...

            // create thread for execution of function
            Thread t = getContextReference().get().getEnv().createThread(() -> {
                CompilerDirectives.transferToInterpreter();
                RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RunNewThreadNode(getLLVMLanguage()));
                callTarget.call(startRoutine, arg);

                printDebug("func is done...");
            });

            // store cur id in thread var
            store.executeWithTarget(thread, t.getId());
            printDebug("id of thread: " + t.getId());
            // store thread with thread id in context
            getContextReference().get().threadStorage.put(t.getId(), t);

            // start thread
            t.start();

            // interesting stuff maybe
            // getContextReference().get().getHandleForManagedObject():
            // getContextReference().get().getThreadingStack();
            // getContextReference().get().registerThread();

            return 0;
        }
    }

    static class MyArgNode extends LLVMExpressionNode {
        private FrameSlot slot;

        private MyArgNode(FrameSlot slot) {
            this.slot = slot;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return frame.getValue(slot);
        }
    }

    private static class RunNewThreadNode extends RootNode {
        @Child LLVMExpressionNode callNode = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot functionSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot argSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot spSlot = null;

        protected RunNewThreadNode(LLVMLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LLVMStack stack = new LLVMStack(1000); // how big should it really be?
            LLVMStack.StackPointer sp = stack.newFrame();
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                functionSlot = frame.getFrameDescriptor().findOrAddFrameSlot("function");
                argSlot = frame.getFrameDescriptor().findOrAddFrameSlot("arg");
                spSlot = frame.getFrameDescriptor().findOrAddFrameSlot("sp");

                callNode = getCurrentContext(LLVMLanguage.class).getNodeFactory().createFunctionCall(
                        new MyArgNode(functionSlot),
                        new LLVMExpressionNode[] {
                                new MyArgNode(spSlot), new MyArgNode(argSlot)
                        },
                        new FunctionType(PointerType.VOID, new Type[] {}, false),
                        null
                );
            }
            // copy arguments to frame
            final Object[] arguments = frame.getArguments();
            Object function = arguments[0];
            Object arg = arguments[1];
            frame.setObject(functionSlot, function);
            frame.setObject(argSlot, arg);
            frame.setObject(spSlot, sp);

            callNode.executeGeneric(frame);
            return null;
        }
    }



    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object retval) {
            // LLVMPointer retPtr = (LLVMPointer) retval;
            printDebug("entered exit...");
            getContextReference().get().retValStorage.put(Thread.currentThread().getId(), retval);
            printDebug(retval.toString());
            // stop current thread
            // Thread.currentThread().interrupt();
            // Thread.currentThread().stop();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {

        @Child LLVMStoreNode storeNode;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object th, Object threadReturn) {
            if (storeNode == null) {
                // storeNode = LLVMPointerStoreNodeGen.create(null, null);
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeNode = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }
            long thLong = (long) th;
            try {
                printDebug("join try");
                Thread thread = (Thread) getContextReference().get().threadStorage.get(thLong);
                thread.join();
                printDebug("is joined");
                Object retVal = getContextReference().get().retValStorage.get(thLong);
                printDebug("aus der retValStorage: " + retVal.toString());

                // store return value in at ptr

                storeNode.executeWithTarget(threadReturn, retVal);

                printDebug("pointer written...");

            } catch (Exception e) {
                e.printStackTrace();
                printDebug("catch exc now...");

            }
            return 55;
        }

    }

    public abstract static class LLVMPThreadMyTest extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame) {
            return 35; // just to test return 35
        }
    }
}
