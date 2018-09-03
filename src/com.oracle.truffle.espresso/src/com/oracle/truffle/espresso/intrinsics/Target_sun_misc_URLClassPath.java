package com.oracle.truffle.espresso.intrinsics;

import java.net.URL;

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_sun_misc_URLClassPath {
    /**
     * These ... new JVM_ functions uses hotspot internals to improve
     * sun.misc.URLClassPath search time, a hack!
     * http://mail.openjdk.java.net/pipermail/distro-pkg-dev/2015-December/034337.html
     */
    @Intrinsic
    public static @Type(URL[].class) StaticObject getLookupCacheURLs(@Type(ClassLoader.class) StaticObject classLoader) {
        return StaticObject.NULL;
        //EspressoContext context = Utils.getContext();
        //return context.getVm().newArray(context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/net/URL;"), null), 0);
    }
}
