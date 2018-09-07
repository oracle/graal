package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.io.IOException;
import java.util.Arrays;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;

@EspressoIntrinsics
public class Target_java_lang_Runtime {
    @Intrinsic(hasReceiver = true)
    public static int availableProcessors(Object self) {
        return Runtime.getRuntime().availableProcessors();
    }

    // TODO(peterssen): This a hack to be able to spawn processes without going down to UNIXProcess.
    @Intrinsic(hasReceiver = true)
    public static @Type(Process.class) Object exec(StaticObject self, @Type(String[].class) StaticObject cmdarray) {
        Meta meta = meta(self).getMeta();
        Object[] wrapped = ((StaticObjectArray) cmdarray).getWrapped();
        String[] hostArgs = new String[wrapped.length];
        Arrays.setAll(hostArgs, i -> meta.toHost(wrapped[i]));

        try {
            Runtime.getRuntime().exec(hostArgs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return StaticObject.NULL;
    }
}
