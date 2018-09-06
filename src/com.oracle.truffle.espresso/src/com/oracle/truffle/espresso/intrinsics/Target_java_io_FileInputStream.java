package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_io_FileInputStream {
    @Intrinsic
    public static void initIDs() {
        /* nop */
    }

    private static AtomicInteger fdCount = new AtomicInteger(3);
    private static ConcurrentHashMap<Integer, FileDescriptor> fdMap = new ConcurrentHashMap<>();

    @Intrinsic(hasReceiver = true)
    public static void open0(StaticObject self, @Type(String.class) StaticObject name) {
        // throws
        // FileNotFoundException;
        try {
            FileInputStream fis = new FileInputStream(Meta.toHost(name));
            FileDescriptor fd = fis.getFD();
            int fakeFd = fdCount.incrementAndGet();
            fdMap.put(fakeFd, fd);
            meta((StaticObject) meta(self).field("fd").get()).field("fd").set(fakeFd);

        } catch (FileNotFoundException e) {
            Meta meta = self.getKlass().getContext().getMeta();
            StaticObject ex = meta.exceptionKlass(FileNotFoundException.class).allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            throw new EspressoException(ex);
        } catch (IOException e) {
            Meta meta = self.getKlass().getContext().getMeta();
            StaticObject ex = meta.exceptionKlass(IOException.class).allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            throw new EspressoException(ex);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int readBytes(StaticObject self, byte b[], int off, int len) {
        // throws IOException;
        int fakeFd = (int) meta((StaticObject) meta(self).field("fd").get()).field("fd").get();
        try {
            // read from stdin
            if (fakeFd == 0) {
                return Utils.getContext().in().read(b, off, len);
            }
            FileDescriptor fd = fdMap.get(fakeFd);
            try (FileInputStream fis = new FileInputStream(fd)) {
                return fis.read(b, off, len);
            }
        }
        catch (IOException e) {
            Meta meta = self.getKlass().getContext().getMeta();
            StaticObject ex = meta.exceptionKlass(IOException.class).allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            throw new EspressoException(ex);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int available0(StaticObject self) {
        // throws IOException;
        int fakeFd = (int) meta((StaticObject) meta(self).field("fd").get()).field("fd").get();
        try {
            // read from stdin
            if (fakeFd == 0) {
                return Utils.getContext().in().available();
            }
            FileDescriptor fd = fdMap.get(fakeFd);
            try (FileInputStream fis = new FileInputStream(fd)) {
                return fis.available();
            }
        }
        catch (IOException e) {
            Meta meta = self.getKlass().getContext().getMeta();
            StaticObject ex = meta.exceptionKlass(IOException.class).allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            throw new EspressoException(ex);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static void close0(StaticObject self) {
        // throws IOException;
        int fakeFd = (int) meta((StaticObject) meta(self).field("fd").get()).field("fd").get();
        try {
            // read from stdin
            if (fakeFd == 0) {
                Utils.getContext().in().close();
            }
//            FileDescriptor fd = fdMap.get(fakeFd);
//            try (FileInputStream fis = new FileInputStream(fd)) {
//                fis.close();
//            }
        }
        catch (IOException e) {
            Meta meta = self.getKlass().getContext().getMeta();
            StaticObject ex = meta.exceptionKlass(IOException.class).allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            throw new EspressoException(ex);
        }
    }
}
