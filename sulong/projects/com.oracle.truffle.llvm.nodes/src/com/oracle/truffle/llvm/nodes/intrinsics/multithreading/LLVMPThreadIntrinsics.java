package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
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

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCreate extends LLVMBuiltin {

        @Child LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object thread, Object attr, Object startRoutine, Object arg) {

            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }

            if (debugOut == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                try {
                    debugOut = new PrintWriter(new FileWriter("/home/florian/debug out"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
                Truffle.getRuntime().createCallTarget(new RunNewThreadNode(getLLVMLanguage())).call(startRoutine, arg);

                debugOut.println("func is done...");
                debugOut.flush();
            });

            // store cur id in thread var
            store.executeWithTarget(thread, t.getId());

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

        @CompilerDirectives.TruffleBoundary
        private void printDebug(String str) {
            debugOut.write(str);
            debugOut.flush();
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

        protected RunNewThreadNode(LLVMLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {

            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                functionSlot = frame.getFrameDescriptor().findOrAddFrameSlot("function");
                argSlot = frame.getFrameDescriptor().findOrAddFrameSlot("arg");

                callNode = getCurrentContext(LLVMLanguage.class).getNodeFactory().createFunctionCall(
                        new MyArgNode(functionSlot),
                        new LLVMExpressionNode[] {
                                new MyArgNode(argSlot)
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

            callNode.executeGeneric(frame);
            return null;
        }
    }



    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object retval) {
            // LLVMPointer retPtr = (LLVMPointer) retval;
            debugOut.println("here in exit");
            debugOut.flush();
            getContextReference().get().retValStorage.put(Thread.currentThread().getId(), retval);
            ((Thread) (getContextReference().get().threadStorage.get(Thread.currentThread().getId()))).interrupt();
            return 0;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object th, Object threadReturn) {
            /*
             * long thLong = (long) th; LLVMPointer returnPtr = (LLVMPointer) threadReturn; Object
             * obj = getContextReference().get().retValStorage.get(thLong); boolean doIt = false;
             * try { debugOut.println("join try..." + thLong); debugOut.flush(); Thread thread =
             * (Thread) getContextReference().get().threadStorage.get(thLong); thread.join();
             * debugOut.println("is joined..."); debugOut.flush(); // store return value in at ptr
             * Object retVal = getContextReference().get().retValStorage.get(thLong);
             * debugOut.println("class of retVal: " + retVal.getClass()); debugOut.flush();
             * 
             * 
             * LLVMPointerStoreNode storeNode = LLVMPointerStoreNodeGen.create(null, null);
             * storeNode.executeWithTarget(returnPtr, retVal); debugOut.println("pointer written..."
             * ); debugOut.flush();
             * 
             * } catch (Exception e) { e.printStackTrace(); debugOut.println("catch exc now...");
             * debugOut.flush(); } Random r = new Random(System.currentTimeMillis()); doIt =
             * r.nextBoolean();
             * 
             * return doIt ? 35 : 45;
             */
            return 105;
        }

    }

    public abstract static class LLVMPThreadMyTest extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame) {
            return 35; // just to test return 35
        }
    }
}
