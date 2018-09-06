package com.oracle.truffle.espresso.intrinsics;

import java.io.IOException;
import java.io.OutputStream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_java_io_FileOutputStream {
    @Intrinsic
    public static void initIDs() {
        /* nop */
    }



    @Intrinsic(hasReceiver = true)
    public static void writeBytes(StaticObject self, byte[] bytes, int offset, int len, boolean append) {
        int fd = (int) meta((StaticObject) meta(self).field("fd").get()).field("fd").get();
        if (fd == 1 || fd == 2) {
            OutputStream known = (fd == 1) ? Utils.getContext().out() : Utils.getContext().err();
            try {
                write(known, bytes, offset, len);
            } catch (IOException e) {
                // TODO(peterssen): Handle exception.
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("Cannot write to FD: " + fd + " operation not supported");
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static void write(OutputStream out, byte[] bytes, int offset, int len) throws IOException {
        out.write(bytes, offset, len);
    }
}
