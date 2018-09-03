package com.oracle.truffle.espresso.intrinsics;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_lang_System {

    @Intrinsic
    public static void exit(int status) {
        // TODO(peterssen): Use TruffleException.
        System.exit(status);
    }

    @Intrinsic
    public static @Type(Properties.class) StaticObject initProperties(@Type(Properties.class) StaticObject props) {
        EspressoContext context = Utils.getContext();
        final String[] importedProps = new String[]{
                        "java.version",
                        "java.vendor",
                        "java.vendor.url",
                        "java.home",
                        "java.class.version",
                        "java.class.path",
                        "os.name",
                        "os.arch",
                        "os.version",
                        "file.separator",
                        "path.separator",
                        "line.separator",
                        "user.name",
                        "user.home",
                        "user.dir",
                        // TODO(peterssen): Parse the boot classpath from arguments.
                        "sun.boot.class.path",

                        // Needed during initSystemClass to initialize props.
                        "file.encoding"
        };

        MethodInfo setProperty = props.getKlass().findDeclaredMethod("setProperty",
                        Object.class, String.class, String.class);

        for (String prop : importedProps) {

            StaticObject guestPropKey = Utils.toGuestString(context, prop);
            StaticObject guestPropValue;

            // Inject guest classpath.
            if (prop.equals("java.class.path")) {
                guestPropValue = Utils.toGuestString(context, context.getClasspath().toString());
            } else {
                guestPropValue = Utils.toGuestString(context, System.getProperty(prop));
            }

            setProperty.getCallTarget().call(props, guestPropKey, guestPropValue);
        }

        return props;
    }

    @Intrinsic
    public static void setIn0(@Type(InputStream.class) StaticObjectImpl in) {
        EspressoContext context = Utils.getContext();
        Klass SYSTEM_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/System;"), null);
        InterpreterToVM vm = Utils.getVm();
        vm.setFieldObject(in, SYSTEM_KLASS.getStatics(), Utils.findDeclaredField(SYSTEM_KLASS, "in"));
    }

    @Intrinsic
    public static void setOut0(@Type(PrintStream.class) StaticObject out) {
        EspressoContext context = Utils.getContext();
        Klass SYSTEM_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/System;"), null);
        InterpreterToVM vm = Utils.getVm();
        vm.setFieldObject(out, SYSTEM_KLASS.getStatics(), Utils.findDeclaredField(SYSTEM_KLASS, "out"));
    }

    @Intrinsic
    public static void setErr0(@Type(PrintStream.class) StaticObject err) {
        EspressoContext context = Utils.getContext();
        Klass SYSTEM_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/System;"), null);
        InterpreterToVM vm = Utils.getVm();
        vm.setFieldObject(err, SYSTEM_KLASS.getStatics(), Utils.findDeclaredField(SYSTEM_KLASS, "err"));
    }

    @Intrinsic
    public static void arraycopy(Object src, int srcPos,
                    Object dest, int destPos,
                    int length) {
        try {
            if (src instanceof StaticObjectArray && dest instanceof StaticObjectArray) {
                System.arraycopy(((StaticObjectArray) src).getWrapped(), srcPos, ((StaticObjectArray) dest).getWrapped(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Throwable e) {
            // TODO(peterssen): Throw guest exception.
            throw e;
        }
    }

    @Intrinsic
    public static long currentTimeMillis() {
        // TODO(peterssen): Speed up time.
        return System.currentTimeMillis();
    }

    @Intrinsic
    public static long nanoTime() {
        return System.nanoTime();
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic
    public static void loadLibrary(@Type(String.class) StaticObject libname) {
        /* nop */
    }
}
