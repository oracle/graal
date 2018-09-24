package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@Surrogate("java.lang.ClassLoader$NativeLibrary")
interface NativeLibrary {
}

@EspressoIntrinsics(NativeLibrary.class)
public class Target_java_lang_ClassLoader_NativeLibrary {

    private static TruffleObject loadLibrary(String lib) {
        Source source = Source.newBuilder("nfi", String.format("load(RTLD_LAZY) '%s'", lib), "loadLibrary").build();
        CallTarget target = EspressoLanguage.getCurrentContext().getEnv().parse(source);
        return (TruffleObject) target.call();
    }

    @Intrinsic(hasReceiver = true)
    public static void load(StaticObject self, @Type(String.class) StaticObject name, boolean isBuiltin) {
        String hostName = Meta.toHost(name);
        TruffleObject lib = null;
        try {
            lib = loadLibrary(hostName);
        } catch (UnsatisfiedLinkError e) {
            meta(self).field("handle").set(0L);
            throw meta(self).getMeta().throwEx(UnsatisfiedLinkError.class);
        }
        long handle = EspressoLanguage.getCurrentContext().addNativeLibrary(lib);
        // System.err.println("Loading: " + hostName + " " + handle);
        // TODO(peterssen): Should call JNI_OnLoad, if it exists and get the JNI version, check if
        // compatible. Setting the default version as a workaround.
        meta(self).field("jniVersion").set(0x00010001);
        meta(self).field("handle").set(handle);
        meta(self).field("loaded").set(true);
    }

    @Intrinsic(hasReceiver = true)
    public static long find(StaticObject self, @Type(String.class) StaticObject name) {
        long libHandle = (long) meta(self).field("handle").get();
        if (libHandle != 0) {
            TruffleObject library = self.getKlass().getContext().getNativeLibraries().get(libHandle);
            assert library != null;
            try {
                ForeignAccess.sendRead(Message.READ.createNode(), library, Meta.toHost(name));
                System.err.println("Found " + Meta.toHost(name) + " in " + libHandle);
                return libHandle;
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                return 0;
            }
        }
        return 0;
    }
}
