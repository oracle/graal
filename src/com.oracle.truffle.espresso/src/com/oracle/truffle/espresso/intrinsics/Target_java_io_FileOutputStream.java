package com.oracle.truffle.espresso.intrinsics;

import java.io.IOException;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_io_FileOutputStream {
    @Intrinsic
    public static void initIDs() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static void writeBytes(StaticObject self, byte[] bytes, int offset, int len, boolean append) {
        StaticObject fileDescriptor = (StaticObject) Utils.getVm().getFieldObject(self, Utils.findDeclaredField(self.getKlass(), "fd"));
        int fd = Utils.getVm().getFieldInt(fileDescriptor, Utils.findDeclaredField(fileDescriptor.getKlass(), "fd"));
        if (fd == 1) {
            try {
                Utils.getContext().out().write(bytes, offset, len);
            } catch (IOException e) {
                // TODO(peterssen): Handle exception.
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("Cannot write to FD: " + fd + " operation not supported");
        }
    }
}
