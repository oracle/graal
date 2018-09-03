package com.oracle.truffle.espresso.intrinsics;

@EspressoIntrinsics
public class Target_java_lang_Runtime {
    @Intrinsic(hasReceiver = true)
    public static int availableProcessors(Object self) {
        return Runtime.getRuntime().availableProcessors();
    }
}
