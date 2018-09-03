package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_lang_Thread {
    // TODO(peterssen): Remove single thread shim, support real threads.
    // private StaticObject currentThread = null;

    @Intrinsic
    public static @Type(Thread.class) StaticObject currentThread() {
        EspressoContext context = Utils.getContext();
        if (context.getMainThread() == null) {
            Klass THREAD_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/Thread;"), null);

            Klass THREAD_GROUP_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/ThreadGroup;"), null);

            InterpreterToVM vm = Utils.getCallerNode().getVm();

            context.setMainThread(vm.newObject(THREAD_KLASS));
            StaticObject threadGroup = vm.newObject(THREAD_GROUP_KLASS);

            vm.setFieldInt(5, context.getMainThread(), Utils.findDeclaredField(THREAD_KLASS, "priority"));
            vm.setFieldObject(threadGroup, context.getMainThread(), Utils.findDeclaredField(THREAD_KLASS, "group"));
        }
        return context.getMainThread();
    }

    @Intrinsic(hasReceiver = true)
    public static void setPriority0(@Type(Thread.class) StaticObject self, int newPriority) {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static void setDaemon(@Type(Thread.class) StaticObject self, boolean on) {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static boolean isAlive(@Type(Thread.class) StaticObject self) {
        return false;
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static void start0(@Type(Thread.class) StaticObject self) {
        /* nop */ }

    @Intrinsic(hasReceiver = true)
    public static boolean isInterrupted(@Type(Thread.class) StaticObject self, boolean ClearInterrupted) {
        return false;
    }
}
