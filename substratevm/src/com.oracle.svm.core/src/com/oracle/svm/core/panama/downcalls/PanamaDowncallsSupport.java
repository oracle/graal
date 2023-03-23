package com.oracle.svm.core.panama.downcalls;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import com.oracle.svm.core.panama.Target_jdk_internal_foreign_abi_NativeEntrypoint;
import com.oracle.svm.core.util.UserError;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class PanamaDowncallsSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void create() {
        ImageSingletons.add(PanamaDowncallsSupport.class, new PanamaDowncallsSupport());
    }

    @Fold
    public static PanamaDowncallsSupport singleton() {
        return ImageSingletons.lookup(PanamaDowncallsSupport.class);
    }
    public static int NOT_REGISTERED = -1;

    private int nextUID = 0;

    private final EconomicMap<Target_jdk_internal_foreign_abi_NativeEntrypoint, Integer> stubs = EconomicMap.create();

    private PanamaDowncallsSupport() {}

    public UnmodifiableEconomicMap<Target_jdk_internal_foreign_abi_NativeEntrypoint, Integer> mapping() {
        return stubs;
    }

    public Iterable<Target_jdk_internal_foreign_abi_NativeEntrypoint> registered() {
        return stubs.getKeys();
    }

    public int getStubId(Target_jdk_internal_foreign_abi_NativeEntrypoint nep) {
        return stubs.get(nep, NOT_REGISTERED);
    }

    public int stubCount() {
        assert nextUID == stubs.size();
        // return nextUID;
        return stubs.size();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void register(Target_jdk_internal_foreign_abi_NativeEntrypoint nep) {
        int uid = nextUID++;
        assert uid != NOT_REGISTERED;

        if (nextUID <= 0) {
            --nextUID;
            throw unsupportedFeature("Too many stubs for Panama downcalls");
        }

        stubs.put(nep, uid);
    }

    @SuppressWarnings("unused")
    public static Object doLinkToNative(long address, int typeId, Object... args) {
        UserError.abort("Call to doLinkToNative detected; you should enable the Panama Foreign feature.");
        return null;
    }
}
