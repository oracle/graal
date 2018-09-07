package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

import java.io.IOException;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_java_lang_Thread {

    // TODO(peterssen): Remove single thread shim, support real threads.
    @Intrinsic
    public static @Type(Thread.class) StaticObject currentThread() {
        EspressoContext context = Utils.getContext();
        if (context.getMainThread() == null) {
            Meta meta = context.getMeta();
            Meta.Klass threadGroupKlass = meta.knownKlass(ThreadGroup.class);
            Meta.Klass threadKlass = meta.knownKlass(Thread.class);
            StaticObject mainThread = threadKlass.metaNew().fields(
                            Meta.Field.set("priority", 5),
                            Meta.Field.set("group", threadGroupKlass.allocateInstance())).getInstance();
            context.setMainThread(mainThread);
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

    @Intrinsic
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Meta meta = Utils.getContext().getMeta();
            StaticObject ex = meta.exceptionKlass(InterruptedException.class).allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            throw new EspressoException(ex);
        }
    }
}
