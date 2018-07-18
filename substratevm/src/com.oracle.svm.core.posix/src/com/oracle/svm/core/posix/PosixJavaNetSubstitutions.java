/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.posix;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Ifaddrs;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.NetIf;
import com.oracle.svm.core.posix.headers.Netdb;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.Poll;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

/** Dummy class to have a class with the file's name. */
public final class PosixJavaNetSubstitutions {

    /** Private constructor: No instances. */
    private PosixJavaNetSubstitutions() {
    }
}

@TargetClass(className = "java.net.PlainDatagramSocketImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_PlainDatagramSocketImpl {

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected void bind0(int lport, InetAddress laddr) throws SocketException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.bind0(int, InetAddress)");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected void send(DatagramPacket p) throws IOException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.send(DatagramPacket)");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected int peek(InetAddress i) throws IOException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.peek(InetAddress)");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected int peekData(DatagramPacket p) throws IOException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.peekData(DatagramPacket)");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected void receive0(DatagramPacket p) throws IOException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.receive0(DatagramPacket)");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected void datagramSocketClose() {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.datagramSocketClose()");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected Object socketGetOption(int opt) {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.socketGetOption(int)");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected void socketSetOption0(int opt, Object val) {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.socketSetOption0(int, Object)");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected void datagramSocketCreate() {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.datagramSocketCreate()");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    int dataAvailable() {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.dataAvailable()");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected void setTimeToLive(int ttl) {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.setTimeToLive(int)");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected int getTimeToLive() {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.getTimeToLive()");
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    protected void connect0(InetAddress address, int port) {
        throw VMError.unsupportedFeature("Unimplemented: java.net.PlainDatagramSocketImpl.connect0(InetAddress, int)");
    }

}

@TargetClass(java.net.DatagramSocket.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_DatagramSocket {

    @Substitute
    @SuppressWarnings("unused")
    Target_java_net_DatagramSocket(SocketAddress bindaddr) throws SocketException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.DatagramSocket.<init>(SocketAddress)");
    }

    @Substitute
    private void checkOldImpl() {
        // it calls java.net.PlainDatagramSocketImpl.peekData(DatagramPacket) which is unimplemented
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    public void bind(SocketAddress addr) throws SocketException {
        throw VMError.unsupportedFeature("Unimplemented: java.net.DatagramSocket.bind(SocketAddress)");
    }
}

@TargetClass(java.net.ServerSocket.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_ServerSocket {

    @Alias boolean oldImpl;

    /* TODO: I do not support old (pre-JDK-1.4) implementations of ServerSocket. */
    @Substitute
    private void checkOldImpl() {
        oldImpl = false;
    }

}

// Allow methods with non-standard names: Checkstyle: stop

@TargetClass(className = "java.net.InetAddress")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_InetAddress {

    /*
     * Aliases to get visibility to fields.
     */

    @Alias static int IPv4;

    @Alias static int IPv6;

    @Alias //
    @TargetElement(name = "preferIPv6Address", onlyWith = JDK8OrEarlier.class) //
    static boolean preferIPv6AddressJDK8OrEarlier;

    @Alias //
    @TargetElement(name = "preferIPv6Address", onlyWith = JDK9OrLater.class) //
    static int preferIPv6AddressJDK9OrLater;

    @Alias //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    static /* final */ int PREFER_IPV4_VALUE;

    @Alias //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    static /* final */ int PREFER_IPV6_VALUE;

    @Alias //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    static /* final */ int PREFER_SYSTEM_VALUE;

    @Alias Target_java_net_InetAddress_InetAddressHolder holder;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.FromAlias) //
    static HashMap<String, Void> lookupTable = new HashMap<>();

    /**
     * Force the JDK to re-initialize address caches at run time.
     *
     * We do not recompute the fields addressCache and negativeCache to new instances. Instead, we
     * reset the internals of the Cache instances referenced by the fields in
     * {@link Target_java_net_InetAddress_Cache#cache} - that is easier since it does not require us
     * to instantiate a non-public JDK class during image generation.
     */
    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.Reset) //
    static boolean addressCacheInit = false;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.Reset) //
    static InetAddress[] unknown_array;

    @Alias //
    @TargetElement(name = "cachedLocalHost", onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.Reset) //
    static InetAddress cachedLocalHostJDK8OrEarlier;

    @Alias //
    @TargetElement(name = "cachedLocalHost", onlyWith = JDK9OrLater.class) //
    @RecomputeFieldValue(kind = Kind.Reset) //
    static Target_java_net_InetAddress_CachedLocalHost cachedLocalHostJDK9OrLater;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    @RecomputeFieldValue(kind = Kind.Reset) //
    static long cacheTime;
}

@TargetClass(className = "java.net.InetAddress", innerClass = "Cache", onlyWith = JDK8OrEarlier.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_InetAddress_Cache {

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = LinkedHashMap.class)//
    LinkedHashMap<String, Object> cache;
}

@TargetClass(className = "java.net.InetAddress", innerClass = "CachedLocalHost", onlyWith = JDK9OrLater.class)
final class Target_java_net_InetAddress_CachedLocalHost {
}

/** Methods to operate on java.net.InetAddress instances. */
class Util_java_net_InetAddress {

    /** Private constructor: No instances. */
    private Util_java_net_InetAddress() {
    }

    /*
     * Conversion between the Java and substitution type systems.
     */

    static Target_java_net_InetAddress from_InetAddress(InetAddress ia) {
        return KnownIntrinsics.unsafeCast(ia, Target_java_net_InetAddress.class);
    }

    /** Initialization. */

    /* Do not re-format commented out code: @formatter:off */
    /* From jdk/src/share/native/java/net/InetAddress.c. */
    // 048 JNIEXPORT void JNICALL
    // 049 Java_java_net_InetAddress_init(JNIEnv *env, jclass cls) {
    static void Java_java_net_InetAddress_init() {
        // 050 jclass c = (*env)->FindClass(env,"java/net/InetAddress");
        // 051 CHECK_NULL(c);
        // 052 ia_class = (*env)->NewGlobalRef(env, c);
        // 053 CHECK_NULL(ia_class);
        // 054 c = (*env)->FindClass(env,"java/net/InetAddress$InetAddressHolder");
        // 055 CHECK_NULL(c);
        // 056 iac_class = (*env)->NewGlobalRef(env, c);
        // 057 ia_holderID = (*env)->GetFieldID(env, ia_class, "holder", "Ljava/net/InetAddress$InetAddressHolder;");
        // 058 CHECK_NULL(ia_holderID);
        // 059 ia_preferIPv6AddressID = (*env)->GetStaticFieldID(env, ia_class, "preferIPv6Address", "Z");
        // 060 CHECK_NULL(ia_preferIPv6AddressID);
        // 061
        // 062 iac_addressID = (*env)->GetFieldID(env, iac_class, "address", "I");
        // 063 CHECK_NULL(iac_addressID);
        // 064 iac_familyID = (*env)->GetFieldID(env, iac_class, "family", "I");
        // 065 CHECK_NULL(iac_familyID);
        // 066 iac_hostNameID = (*env)->GetFieldID(env, iac_class, "hostName", "Ljava/lang/String;");
    }
    /* @formatter:on */
}

@TargetClass(className = "java.net.InterfaceAddress")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_InterfaceAddress {

    @Alias InetAddress address;

    @Alias Inet4Address broadcast;

    @Alias short maskLength;

    @Alias
    Target_java_net_InterfaceAddress() {
    }
}

/** Methods to operate on java.net.InterfaceAddress instances. */
final class Util_java_net_InterfaceAddress {

    static InterfaceAddress toInterfaceAddress(Target_java_net_InterfaceAddress tjnia) {
        return KnownIntrinsics.unsafeCast(tjnia, InterfaceAddress.class);
    }

    static InterfaceAddress newInterfaceAddress() {
        return toInterfaceAddress(new Target_java_net_InterfaceAddress());
    }
}

/*
 * Note to self: I could refer to the target class as "java.net.InetAddress$InetAddressHolder", but
 * that seems worse.
 */
/** Aliases to get visibility to fields. */
@TargetClass(className = "java.net.InetAddress", innerClass = "InetAddressHolder")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_InetAddress_InetAddressHolder {

    /* Aliases to get visibility. */

    @Alias String hostName;

    @Alias int address;

    @Alias int family;

    @Alias
    Target_java_net_InetAddress_InetAddressHolder() {
    }

    @Alias
    // 215 void init(String hostName, int family) { .... }
    native void init(String hostNameArg, int familyArg);
}

@TargetClass(className = "java.net.InetAddressContainer")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_InetAddressContainer {
    @Alias InetAddress addr;
}

class Util_java_net_InetAddressContainer {

    /**
     * If obj is an InetAddressContainer, set the addr field. Returns true on success, false on
     * failure.
     */
    static boolean setAddr(Object obj, InetAddress addr) {
        final Target_java_net_InetAddressContainer asIAC = narrow(obj);
        if (asIAC != null) {
            asIAC.addr = addr;
            return true;
        }
        return false;
    }

    /** A type-checked narrow to a type I can not see, by using the target class. */
    static Target_java_net_InetAddressContainer narrow(Object obj) {
        final Class<Target_java_net_InetAddressContainer> clazz = Target_java_net_InetAddressContainer.class;
        if (clazz.isInstance(obj)) {
            return clazz.cast(obj);
        }
        return null;
    }
}

@TargetClass(className = "java.net.Inet4Address")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_Inet4Address {

    @Alias
    Target_java_net_Inet4Address() {
    }
}

/** Methods to operate on java.net.Inet4Address instances. */
class Util_java_net_Inet4Address {

    /** Private constructor: No instances. */
    private Util_java_net_Inet4Address() {
    }

    /** Create an instance of an Inet4Address. */
    static Inet4Address new_Inet4Address() {
        Target_java_net_Inet4Address tjni4a = new Target_java_net_Inet4Address();
        Inet4Address result = Util_java_net_Inet4Address.to_Inet4Address(tjni4a);
        return result;
    }

    /* Cast between Java and substitution types. */

    static Inet4Address to_Inet4Address(Target_java_net_Inet4Address tjni4a) {
        return KnownIntrinsics.unsafeCast(tjni4a, Inet4Address.class);
    }

    /* Initialization. */

    // 060 static int initialized = 0;
    static int initialized = 0;

    // 058 static jboolean initializeInetClasses(JNIEnv *env) {
    static boolean initializeInetClasses() {
        // 061 if (!initialized) {
        if (!CTypeConversion.toBoolean(initialized)) {
            // 062 ni_iacls = (*env)->FindClass(env, "java/net/InetAddress");
            // 063 CHECK_NULL_RETURN(ni_iacls, JNI_FALSE);
            // 064 ni_iacls = (*env)->NewGlobalRef(env, ni_iacls);
            // 065 CHECK_NULL_RETURN(ni_iacls, JNI_FALSE);
            // 066 ni_ia4cls = (*env)->FindClass(env, "java/net/Inet4Address");
            // 067 CHECK_NULL_RETURN(ni_ia4cls, JNI_FALSE);
            // 068 ni_ia4cls = (*env)->NewGlobalRef(env, ni_ia4cls);
            // 069 CHECK_NULL_RETURN(ni_ia4cls, JNI_FALSE);
            // 070 ni_ia4ctrID = (*env)->GetMethodID(env, ni_ia4cls, "<init>", "()V");
            // 071 CHECK_NULL_RETURN(ni_ia4ctrID, JNI_FALSE);
            // 072 initialized = 1;
            initialized = 1;
        }
        // 074 return JNI_TRUE;
        return Util_jni.JNI_TRUE();
    }

    /* From jdk/src/share/native/java/net/Inet4Address.c. */
    // 042 JNIEXPORT void JNICALL
    // 043 Java_java_net_Inet4Address_init(JNIEnv *env, jclass cls) {
    static void Java_java_net_Inet4Address_init() {
        // 044 jclass c = (*env)->FindClass(env, "java/net/Inet4Address");
        // 045 CHECK_NULL(c);
        // 046 ia4_class = (*env)->NewGlobalRef(env, c);
        // 047 CHECK_NULL(ia4_class);
        // 048 ia4_ctrID = (*env)->GetMethodID(env, ia4_class, "<init>", "()V");
    }
}

/* Do not re-format commented-out code.  @formatter:off */
/* In particular, the */
/*     337 #else /* defined(_ALLBSD_SOURCE) && !defined(HAS_GLIBC_GETHOSTBY_R) */
/* branch */
/** Substitutions for the code from src/solaris/native/java/net/Inet4AddressImpl.c?v=Java_1.8.0_40_b10 */
@TargetClass(className = "java.net.Inet4AddressImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_Inet4AddressImpl {

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    InetAddress anyLocalAddress;
    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    InetAddress loopbackAddress;

    @Substitute
    // 348 /*
    // 349  * Class:     java_net_Inet4AddressImpl
    // 350  * Method:    getLocalHostName
    // 351  * Signature: ()Ljava/lang/String;
    // 352  */
    // 353 JNIEXPORT jstring JNICALL
    // 354 Java_java_net_Inet4AddressImpl_getLocalHostName(JNIEnv *env, jobject this) {
    @SuppressWarnings({"static-method"})
    public String getLocalHostName() {
        // 355     char hostname[NI_MAXHOST+1];
        CCharPointer hostname = StackValue.get(Netdb.NI_MAXHOST() + 1, CCharPointer.class);
        // 357     hostname[0] = '\0';
        hostname.write(0, (byte) '\0');
        // 358     if (JVM_GetHostName(hostname, sizeof(hostname))) {
        if (CTypeConversion.toBoolean(VmPrimsJVM.JVM_GetHostName(hostname, Netdb.NI_MAXHOST() + 1))) {
            // 359         /* Something went wrong, maybe networking is not setup? */
            // 360         strcpy(hostname, "localhost");
            try (CCharPointerHolder pin = CTypeConversion.toCString("localhost")) {
                LibC.strcpy(hostname, pin.get());
            }
        } else {
            // 362         struct addrinfo hints, *res;
            Netdb.addrinfo hints = StackValue.get(Netdb.addrinfo.class);
            Netdb.addrinfoPointer res = StackValue.get(Netdb.addrinfoPointer.class);
            // 363         int error;
            int error;
            // 365         hostname[NI_MAXHOST] = '\0';
            hostname.write(Netdb.NI_MAXHOST(), (byte) '\0');
            // 366         memset(&hints, 0, sizeof(hints));
            LibC.memset(hints, WordFactory.zero(), SizeOf.unsigned(Netdb.addrinfo.class));
            // 367         hints.ai_flags = AI_CANONNAME;
            hints.set_ai_flags(Netdb.AI_CANONNAME());
            // 368         hints.ai_family = AF_INET;
            hints.set_ai_family(Socket.AF_INET());
            // 370         error = getaddrinfo(hostname, NULL, &hints, &res);
            error = Netdb.getaddrinfo(hostname, WordFactory.nullPointer(), hints, res);
            // 372         if (error == 0) {/* host is known to name service */
            if (error == 0) {
                // 373             getnameinfo(res->ai_addr,
                // 374                         res->ai_addrlen,
                // 375                         hostname,
                // 376                         NI_MAXHOST,
                // 377                         NULL,
                // 378                         0,
                // 379                         NI_NAMEREQD);
                Netdb.getnameinfo(res.read().ai_addr(), res.read().ai_addrlen(), hostname, Netdb.NI_MAXHOST(), WordFactory.nullPointer(), 0, Netdb.NI_NAMEREQD());
                // 381             /* if getnameinfo fails hostname is still the value
                // 382                from gethostname */
                // 384             freeaddrinfo(res);
                Netdb.freeaddrinfo(res.read());
            }
        }
        // 387     return (*env)->NewStringUTF(env, hostname);
        return CTypeConversion.toJavaString(hostname);
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    // 390 /*
    // 391  * Find an internet address for a given hostname.  Note that this
    // 392  * code only works for addresses of type INET. The translation
    // 393  * of %d.%d.%d.%d to an address (int) occurs in java now, so the
    // 394  * String "host" shouldn't *ever* be a %d.%d.%d.%d string
    // 395  *
    // 396  * Class:     java_net_Inet4AddressImpl
    // 397  * Method:    lookupAllHostAddr
    // 398  * Signature: (Ljava/lang/String;)[[B
    // 399  */
    // 401 JNIEXPORT jobjectArray JNICALL
    // 402 Java_java_net_Inet4AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
    // 403                                                 jstring host) {
    InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException {
        // 404     const char *hostname;
        CCharPointer hostname;
        // 405     jobjectArray ret = 0;
        InetAddress[] ret = null;
        // 406     int retLen = 0;
        int retLen = 0;
        // 407     int error = 0;
        int error = 0;
        // 408     struct addrinfo hints, *res, *resNew = NULL;
        Netdb.addrinfo hints = StackValue.get(Netdb.addrinfo.class);
        Netdb.addrinfoPointer res_Pointer = StackValue.get(Netdb.addrinfoPointer.class);
        Netdb.addrinfo resNew = WordFactory.nullPointer();
        // 410     if (!initializeInetClasses(env))
        if (!Util_java_net_Inet4AddressImpl.initializeInetClasses()) {
            // 411         return NULL;
            return null;
        }
        // 413     if (IS_NULL(host)) {
        if (host == null) {
            // 414         JNU_ThrowNullPointerException(env, "host is null");
            // 415         return 0;
            throw new NullPointerException("host is null");
        }
        // 417     hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
        try {
            try (CCharPointerHolder hostname_Pin = CTypeConversion.toCString(host)) {
                hostname = hostname_Pin.get();
                // 418     CHECK_NULL_RETURN(hostname, NULL);
                if (hostname.isNull()) {
                    return null;
                }
                // 420     /* Try once, with our static buffer. */
                // 421     memset(&hints, 0, sizeof(hints));
                LibC.memset(hints, WordFactory.zero(), SizeOf.unsigned(Netdb.addrinfo.class));
                // 422     hints.ai_flags = AI_CANONNAME;
                hints.set_ai_flags(Netdb.AI_CANONNAME());
                // 423     hints.ai_family = AF_INET;
                hints.set_ai_family(Socket.AF_INET());
                // 425 #ifdef __solaris__
                // 426     /*
                // 427      * Workaround for Solaris bug 4160367 - if a hostname contains a
                // 428      * white space then 0.0.0.0 is returned
                // 429      */
                // 430     if (isspace((unsigned char)hostname[0])) {
                // 431         JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                // 432                         (char *)hostname);
                // 433         JNU_ReleaseStringPlatformChars(env, host, hostname);
                // 434         return NULL;
                // 435     }
                // 436 #endif
                // 438     error = getaddrinfo(hostname, NULL, &hints, &res);
                error = Netdb.getaddrinfo(hostname, WordFactory.nullPointer(), hints, res_Pointer);
                // 440     if (error) {
                if (CTypeConversion.toBoolean(error)) {
                    // 441         /* report error */
                    // 442         ThrowUnknownHostExceptionWithGaiError(env, hostname, error);
                    /* FIXME: Not implementing ThrowUnknownHostExceptionWithGaiError. */
                    throw new UnknownHostException(host);
                    // 443         JNU_ReleaseStringPlatformChars(env, host, hostname);
                    /* Released when exiting CCharPointerHolder hostname_Pin block. */
                    // 444         return NULL;
                    /* Throws exception instead of returning. */
                } else {
                    // 446         int i = 0;
                    int i = 0;
                    // 447         struct addrinfo *itr, *last = NULL, *iterator = res;
                    Netdb.addrinfo itr;
                    Netdb.addrinfo last = WordFactory.nullPointer();
                    Netdb.addrinfo iterator;
                    iterator = res_Pointer.read();
                    // 449         while (iterator != NULL) {
                    while (iterator.isNonNull()) {
                        // 450             // remove the duplicate one
                        // 451             int skip = 0;
                        int skip = 0;
                        // 452             itr = resNew;
                        itr = resNew;
                        // 453             while (itr != NULL) {
                        while (itr.isNonNull()) {
                            // 454                 struct sockaddr_in *addr1, *addr2;
                            NetinetIn.sockaddr_in addr1;
                            NetinetIn.sockaddr_in addr2;
                            // 455                 addr1 = (struct sockaddr_in *)iterator->ai_addr;
                            addr1 = (NetinetIn.sockaddr_in) iterator.ai_addr();
                            // 456                 addr2 = (struct sockaddr_in *)itr->ai_addr;
                            addr2 = (NetinetIn.sockaddr_in) itr.ai_addr();
                            // 457                 if (addr1->sin_addr.s_addr ==
                            // 458                     addr2->sin_addr.s_addr) {
                            if (addr1.sin_addr().s_addr() == addr2.sin_addr().s_addr()) {
                                // 459                     skip = 1;
                                skip = 1;
                                // 460                     break;
                                break;
                            }
                            // 462                 itr = itr->ai_next;
                            itr = itr.ai_next();
                        }
                        // 465             if (!skip) {
                        if (!CTypeConversion.toBoolean(skip)) {
                            // 466                 struct addrinfo *next
                            // 467                     = (struct addrinfo*) malloc(sizeof(struct addrinfo));
                            Netdb.addrinfo next = LibC.malloc(SizeOf.unsigned(Netdb.addrinfo.class));
                            // 468                 if (!next) {
                            if (!CTypeConversion.toBoolean(next)) {
                                // 469                     JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed");
                                // 470                     ret = NULL;
                                ret = null;
                                throw new OutOfMemoryError("Native heap allocation failed");
                                // 471                     goto cleanupAndReturn;
                                /* See finally block. */
                            }
                            // 473                 memcpy(next, iterator, sizeof(struct addrinfo));
                            LibC.memcpy(next, iterator, SizeOf.unsigned(Netdb.addrinfo.class));
                            // 474                 next->ai_next = NULL;
                            next.set_ai_next(null);
                            // 475                 if (resNew == NULL) {
                            if (resNew.isNull()) {
                                // 476                     resNew = next;
                                resNew = next;
                            } else {
                                // 478                     last->ai_next = next;
                                last.set_ai_next(next);
                            }
                            // 480                 last = next;
                            last = next;
                            // 481                 i++;
                            i++;
                        }
                        // 483             iterator = iterator->ai_next;
                        iterator = iterator.ai_next();
                    }
                    // 486         retLen = i;
                    retLen = i;
                    // 487         iterator = resNew;
                    iterator = resNew;
                    // 489         ret = (*env)->NewObjectArray(env, retLen, ni_iacls, NULL);
                    ret = new InetAddress[retLen];
                    // 491         if (IS_NULL(ret)) {
                    // 492             /* we may have memory to free at the end of this */
                    // 493             goto cleanupAndReturn;
                    // 494         }
                    // 496         i = 0;
                    i = 0;
                    // 497         while (iterator != NULL) {
                    while (iterator.isNonNull()) {
                        // 498             jobject iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
                        Inet4Address iaObj = Util_java_net_Inet4Address.new_Inet4Address();
                        // 499             if (IS_NULL(iaObj)) {
                        if (iaObj == null) {
                            // 500                 ret = NULL;
                            ret = null;
                            // 501                 goto cleanupAndReturn;
                            return ret;
                        }
                        // 503             setInetAddress_addr(env, iaObj, ntohl(((struct sockaddr_in*)iterator->ai_addr)->sin_addr.s_addr));
                        JavaNetNetUtil.setInetAddress_addr(iaObj, NetinetIn.ntohl(((NetinetIn.sockaddr_in) iterator.ai_addr()).sin_addr().s_addr()));
                        // 504             setInetAddress_hostName(env, iaObj, host);
                        JavaNetNetUtil.setInetAddress_hostName(iaObj, host);
                        // 505             (*env)->SetObjectArrayElement(env, ret, i++, iaObj);
                        ret[i++] = iaObj;
                        // 506             iterator = iterator->ai_next;
                        iterator = iterator.ai_next();
                    }
                    return ret;
                }
            }
        } finally {
            // 510  cleanupAndReturn:
            // 512         struct addrinfo *iterator, *tmp;
            Netdb.addrinfo iterator;
            Netdb.addrinfo tmp;
            // 513         iterator = resNew;
            iterator = resNew;
            // 514         while (iterator != NULL) {
            while (iterator.isNonNull()) {
                // 515             tmp = iterator;
                tmp = iterator;
                // 516             iterator = iterator->ai_next;
                iterator = iterator.ai_next();
                // 517             free(tmp);
                LibC.free(tmp);
            }
            // 519         JNU_ReleaseStringPlatformChars(env, host, hostname);
            /* This happened when I exited the CCharPointerHolder hostname_Pin block. */
            // 522     freeaddrinfo(res);
            Netdb.freeaddrinfo(res_Pointer.read());
        }
    }

    @Substitute
    // 527 /*
    // 528  * Class:     java_net_Inet4AddressImpl
    // 529  * Method:    getHostByAddr
    // 530  * Signature: (I)Ljava/lang/String;
    // 531  */
    // 532 JNIEXPORT jstring JNICALL
    // 533 Java_java_net_Inet4AddressImpl_getHostByAddr(JNIEnv *env, jobject this,
    // 534                                             jbyteArray addrArray) {
    @SuppressWarnings({"static-method"})
    public String getHostByAddr(byte[] addrArray) throws UnknownHostException {
        // 535     jstring ret = NULL;
        String ret = null;
        // 537     char host[NI_MAXHOST+1];
        CCharPointer host = StackValue.get(Netdb.NI_MAXHOST() + 1, CCharPointer.class);
        // 538     int error = 0;
        int error = 0;
        // 539     int len = 0;
        int len = 0;
        // 540     jbyte caddr[4];
        CCharPointer caddr = StackValue.get(4, CCharPointer.class);
        // 542     struct sockaddr_in him4;
        NetinetIn.sockaddr_in him4 = StackValue.get(NetinetIn.sockaddr_in.class);
        // 543     struct sockaddr *sa;
        Socket.sockaddr sa;
        // 545     jint addr;
        int addr;
        // 546     (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
        VmPrimsJNI.GetByteArrayRegion(addrArray, 0, 4, caddr);
        // 547     addr = ((caddr[0]<<24) & 0xff000000);
        addr = ((caddr.read(0) << 24) & 0xff000000);
        // 548     addr |= ((caddr[1] <<16) & 0xff0000);
        addr |= ((caddr.read(1) << 16) & 0xff0000);
        // 549     addr |= ((caddr[2] <<8) & 0xff00);
        addr |= ((caddr.read(2) << 8) & 0xff00);
        // 550     addr |= (caddr[3] & 0xff);
        addr |= (caddr.read(3) & 0xff);
        // 551     memset((void *) &him4, 0, sizeof(him4));
        LibC.memset(him4, WordFactory.zero(), SizeOf.unsigned(NetinetIn.sockaddr_in.class));
        // 552     him4.sin_addr.s_addr = (uint32_t) htonl(addr);
        him4.sin_addr().set_s_addr(NetinetIn.htonl(addr));
        // 553     him4.sin_family = AF_INET;
        him4.set_sin_family(Socket.AF_INET());
        // 554     sa = (struct sockaddr *) &him4;
        sa = (Socket.sockaddr) him4;
        // 555     len = sizeof(him4);
        len = SizeOf.get(NetinetIn.sockaddr_in.class);
        // 557     error = getnameinfo(sa, len, host, NI_MAXHOST, NULL, 0,
        // 558                         NI_NAMEREQD);
        error = Netdb.getnameinfo(sa, len, host, Netdb.NI_MAXHOST(), WordFactory.nullPointer(), 0, Netdb.NI_NAMEREQD());
        // 560     if (!error) {
        if (!CTypeConversion.toBoolean(error)) {
            // 561         ret = (*env)->NewStringUTF(env, host);
            ret = CTypeConversion.toJavaString(host);
        }
        // 564     if (ret == NULL) {
        if (ret == null) {
            // 565         JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
            throw new java.net.UnknownHostException();
        }
        // 568     return ret;
        return ret;
    }
}
final class Util_java_net_Inet4AddressImpl {

    /* This will be initialized during native image construction. */
    // 060     static int initialized = 0;
    static int initialized = 0;

    // 058 static jboolean initializeInetClasses(JNIEnv *env)
    // 059 {
    static boolean initializeInetClasses() {
        // 061     if (!initialized) {
        if (!CTypeConversion.toBoolean(initialized)) {
            // 062         ni_iacls = (*env)->FindClass(env, "java/net/InetAddress");
            // 063         CHECK_NULL_RETURN(ni_iacls, JNI_FALSE);
            // 064         ni_iacls = (*env)->NewGlobalRef(env, ni_iacls);
            // 065         CHECK_NULL_RETURN(ni_iacls, JNI_FALSE);
            // 066         ni_ia4cls = (*env)->FindClass(env, "java/net/Inet4Address");
            // 067         CHECK_NULL_RETURN(ni_ia4cls, JNI_FALSE);
            // 068         ni_ia4cls = (*env)->NewGlobalRef(env, ni_ia4cls);
            // 069         CHECK_NULL_RETURN(ni_ia4cls, JNI_FALSE);
            // 070         ni_ia4ctrID = (*env)->GetMethodID(env, ni_ia4cls, "<init>", "()V");
            // 071         CHECK_NULL_RETURN(ni_ia4ctrID, JNI_FALSE);
            // 072         initialized = 1;
            initialized = 1;
        }
        // 074     return JNI_TRUE;
        return Util_jni.JNI_TRUE();
    }
}
/* @formatter:on */

/** Aliases to get visibility to fields. */
@TargetClass(className = "java.net.Inet6Address")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_Inet6Address {

    @Alias static int INADDRSZ;

    @Alias int cached_scope_id;

    @Alias Target_java_net_Inet6Address_Inet6AddressHolder holder6;

    // 375 Inet6Address() { .... }
    @Alias
    Target_java_net_Inet6Address() {
    }
}

/** Methods to operate on java.net.Inet6Address instances. */
class Util_java_net_Inet6Address {

    /** Private constructor: No instances. */
    private Util_java_net_Inet6Address() {
    }

    /*
     * Casts between Java and substitution types.
     */

    static Target_java_net_Inet6Address from_Inet6Address(Inet6Address i6a) {
        return KnownIntrinsics.unsafeCast(i6a, Target_java_net_Inet6Address.class);
    }

    static Inet6Address to_Inet6Address(Target_java_net_Inet6Address tia6Obj) {
        return KnownIntrinsics.unsafeCast(tia6Obj, Inet6Address.class);
    }

    /** Create an instance of an Inet6Address. */
    static Inet6Address new_Inet6Address() {
        Target_java_net_Inet6Address tjni6a = new Target_java_net_Inet6Address();
        Inet6Address result = Util_java_net_Inet6Address.to_Inet6Address(tjni6a);
        return result;
    }

    /* Initialization. */

    /* Do not re-wrap commented out code: @formatter:off */
    /* From jdk/src/share/native/java/net/Inet6Address.c */
    // 050 JNIEXPORT void JNICALL
    // 051 Java_java_net_Inet6Address_init(JNIEnv *env, jclass cls) {
    static void Java_java_net_Inet6Address_init() {
        // 052 jclass ia6h_class;
        // 053 jclass c = (*env)->FindClass(env, "java/net/Inet6Address");
        // 054 CHECK_NULL(c);
        // 055 ia6_class = (*env)->NewGlobalRef(env, c);
        // 056 CHECK_NULL(ia6_class);
        // 057 ia6h_class = (*env)->FindClass(env, "java/net/Inet6Address$Inet6AddressHolder");
        // 058 CHECK_NULL(ia6h_class);
        // 059 ia6_holder6ID = (*env)->GetFieldID(env, ia6_class, "holder6", "Ljava/net/Inet6Address$Inet6AddressHolder;");
        // 060 CHECK_NULL(ia6_holder6ID);
        // 061 ia6_ipaddressID = (*env)->GetFieldID(env, ia6h_class, "ipaddress", "[B");
        // 062 CHECK_NULL(ia6_ipaddressID);
        // 063 ia6_scopeidID = (*env)->GetFieldID(env, ia6h_class, "scope_id", "I");
        // 064 CHECK_NULL(ia6_scopeidID);
        // 065 ia6_cachedscopeidID = (*env)->GetFieldID(env, ia6_class, "cached_scope_id", "I");
        // 066 CHECK_NULL(ia6_cachedscopeidID);
        // 067 ia6_scopeidsetID = (*env)->GetFieldID(env, ia6h_class, "scope_id_set", "Z");
        // 068 CHECK_NULL(ia6_scopeidsetID);
        // 069 ia6_scopeifnameID = (*env)->GetFieldID(env, ia6h_class, "scope_ifname", "Ljava/net/NetworkInterface;");
        // 070 CHECK_NULL(ia6_scopeifnameID);
        // 071 ia6_ctrID = (*env)->GetMethodID(env, ia6_class, "<init>", "()V");
    }
    /* @formatter:on */
}

/** Aliases to get visibility to fields. */
@TargetClass(className = "java.net.Inet6Address", innerClass = "Inet6AddressHolder")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_Inet6Address_Inet6AddressHolder {

    // Aliases to get visibility for substituted methods.

    @Alias byte[] ipaddress;

    @Alias int scope_id;

    @Alias boolean scope_id_set;

    @Alias NetworkInterface scope_ifname;

    /*
     * Inet6Address$Inet6AddressHolder is a *non-static* inner class, so the constructors are passed
     * a hidden argument which is the outer Inet6Address. If I have to write an @Alias for a
     * constructor, I have to (a) explicitly declare that parameter, and (b) supply it in the calls.
     */
}

@TargetClass(className = "java.net.Inet6AddressImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_Inet6AddressImpl {

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    InetAddress anyLocalAddress;
    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    InetAddress loopbackAddress;

    @Substitute
    @SuppressWarnings({"static-method"})
    public String getHostByAddr(byte[] addrArray) throws UnknownHostException {
        String ret = null;
        CCharPointer host = StackValue.get(Netdb.NI_MAXHOST() + 1);
        int error = 0;
        int len = 0;
        CCharPointer caddr = StackValue.get(16);

        NetinetIn.sockaddr_in him4 = StackValue.get(NetinetIn.sockaddr_in.class);
        NetinetIn.sockaddr_in6 him6 = StackValue.get(NetinetIn.sockaddr_in6.class);
        Socket.sockaddr sa;

        if (addrArray.length == 4) {
            /*
             * For IPv4 addresses construct a sockaddr_in structure.
             */
            int addr = 0;
            addr |= ((addrArray[0] << 24) & 0xff000000);
            addr |= ((addrArray[1] << 16) & 0xff0000);
            addr |= ((addrArray[2] << 8) & 0xff00);
            addr |= ((addrArray[3] << 0) & 0xff);
            LibC.memset(him4, WordFactory.signed(0), SizeOf.unsigned(NetinetIn.sockaddr_in.class));
            him4.sin_addr().set_s_addr(NetinetIn.htonl(addr));
            him4.set_sin_family(Socket.AF_INET());
            sa = (Socket.sockaddr) him4;
            len = SizeOf.get(NetinetIn.sockaddr_in.class);
        } else {
            /*
             * For IPv6 address construct a sockaddr_in6 structure.
             */
            try (PinnedObject pinnedAddrArray = PinnedObject.create(addrArray)) {
                CCharPointer addrArray0 = pinnedAddrArray.addressOfArrayElement(0);
                LibC.memcpy(caddr, addrArray0, WordFactory.unsigned(16));
            }
            LibC.memset(him6, WordFactory.signed(0), SizeOf.unsigned(NetinetIn.sockaddr_in6.class));
            LibC.memcpy(him6.sin6_addr(), caddr, SizeOf.unsigned(NetinetIn.in6_addr.class));
            him6.set_sin6_family(Socket.AF_INET6());
            sa = (Socket.sockaddr) him6;
            len = SizeOf.get(NetinetIn.sockaddr_in6.class);
        }

        error = Netdb.getnameinfo(sa, len, host, Netdb.NI_MAXHOST(), WordFactory.nullPointer(), 0, Netdb.NI_NAMEREQD());

        if (error == 0) {
            ret = CTypeConversion.toJavaString(host);
        }

        if (ret == null) {
            throw new UnknownHostException();
        }

        return ret;
    }

    /* Do not re-format commented-out code: @formatter:off */
    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    public String getLocalHostName() throws UnknownHostException {
        CCharPointer hostname = StackValue.get(Netdb.NI_MAXHOST() + 1, CCharPointer.class);
        hostname.write(0, (byte) '\0');
        if (Unistd.gethostname(hostname, WordFactory.unsigned(Netdb.NI_MAXHOST() + 1)) != 0) {
            /* Something went wrong, maybe networking is not setup? */
            try (CCharPointerHolder pin = CTypeConversion.toCString("localhost")) {
                LibC.strcpy(hostname, pin.get());
            }
        } else {
            // ensure null-terminated
            hostname.write(Netdb.NI_MAXHOST(), (byte) '\0');
            // 078 #if defined(__linux__) || defined(_ALLBSD_SOURCE)
            if (IsDefined.__linux__() || IsDefined._ALLBSD_SOURCE()) {
                // 079         /* On Linux/FreeBSD gethostname() says "host.domain.sun.com". On
                // 080          * Solaris gethostname() says "host", so extra work is needed.
                // 081          */
                // 082 #else
            } else {
                // TODO: Solaris-specific work-around.
                // 083         /* Solaris doesn't want to give us a fully qualified domain name.
                // 084          * We do a reverse lookup to try and get one. This works
                // 085          * if DNS occurs before NIS in /etc/resolv.conf, but fails
                // 086          * if NIS comes first (it still gets only a partial name).
                // 087          * We use thread-safe system calls.
                // 088          */
                // 089 #ifdef AF_INET6
                // 090         struct addrinfo hints, *res;
                // 091         int error;
                // 092
                // 093         memset(&hints, 0, sizeof(hints));
                // 094         hints.ai_flags = AI_CANONNAME;
                // 095         hints.ai_family = AF_UNSPEC;
                // 096
                // 097         error = getaddrinfo(hostname, NULL, &hints, &res);
                // 098
                // 099         if (error == 0) {
                // 100             /* host is known to name service */
                // 101             error = getnameinfo(res->ai_addr,
                // 102                                 res->ai_addrlen,
                // 103                                 hostname,
                // 104                                 NI_MAXHOST,
                // 105                                 NULL,
                // 106                                 0,
                // 107                                 NI_NAMEREQD);
                // 108
                // 109             /* if getnameinfo fails hostname is still the value
                // 110                from gethostname */
                // 111
                // 112             freeaddrinfo(res);
                // 113         }
                // 114 #endif /* AF_INET6 */
            }
            // 115 #endif /* __linux__ || _ALLBSD_SOURCE */
        }
        return CTypeConversion.toJavaString(hostname);
    }
    /* @formatter:on */

    // Do not re-wrap long lines and comments: @formatter:off
    @Substitute
    @SuppressWarnings({"static-method"})
    public InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException, SocketException, InterruptedException {
        CCharPointer hostname;
        InetAddress[] ret = null;
        int retLen = 0;

        int error = 0;
        Netdb.addrinfo hints = StackValue.get(Netdb.addrinfo.class);
        Netdb.addrinfo res = WordFactory.nullPointer();
        Netdb.addrinfoPointer resPtr = StackValue.get(Netdb.addrinfoPointer.class);
        Netdb.addrinfo resNew = WordFactory.nullPointer();

        if (host == null) {
            throw new NullPointerException("host is null");
        }
        try (CCharPointerHolder hostPin = CTypeConversion.toCString(host)) {
            // "hostname" is pinned through the end of the method.
            hostname = hostPin.get();
            // #ifdef MACOSX
            if (IsDefined.MACOSX()) {
                /*
                 * If we're looking up the local machine, attempt to get the address from
                 * getifaddrs. This ensures we get an IPv6 address for the local machine.
                 */
                ret = Util_java_net_Inet6AddressImpl.lookupIfLocalhost(hostname, true);
                if (ret != null) {
                    return ret;
                }
            }
            // #endif MACOSX

            /* Try once, with our static buffer. */
            LibC.memset(hints, WordFactory.signed(0), SizeOf.unsigned(Netdb.addrinfo.class));
            hints.set_ai_flags(Netdb.AI_CANONNAME());
            hints.set_ai_family(Socket.AF_UNSPEC());
            // TODO: Solaris-specific work-around
            // #ifdef __solaris__
            // /*
            // .* Workaround for Solaris bug 4160367 - if a hostname contains a
            // .* white space then 0.0.0.0 is returned
            // .*/
            // if (isspace((unsigned char)hostname[0])) {
            //     JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
            //     JNU_ReleaseStringPlatformChars(env, host, hostname);
            //     return NULL;
            // }
            // #endif
            try { // res needs cleanup at "cleanupAndReturn".
                error = Netdb.getaddrinfo(hostname, WordFactory.nullPointer(), hints, resPtr);
                if (error != 0) {
                    throw new UnknownHostException(host);
                } else {
                    res = resPtr.read();
                    int i = 0;
                    int inetCount = 0;
                    int inet6Count = 0;
                    int inetIndex = 0;
                    int inet6Index = 0;
                    int originalIndex = 0;
                    Netdb.addrinfo itr;
                    Netdb.addrinfo last = WordFactory.nullPointer();
                    Netdb.addrinfo iterator = res;
                    while (iterator.isNonNull()) {
                        boolean skip = false;
                        itr = resNew;
                        while (itr.isNonNull()) {
                            if ((iterator.ai_family() == itr.ai_family()) && (iterator.ai_addrlen() == itr.ai_addrlen())) {
                                if (itr.ai_family() == Socket.AF_INET()) {
                                    NetinetIn.sockaddr_in addr1;
                                    NetinetIn.sockaddr_in addr2;
                                    addr1 = (NetinetIn.sockaddr_in) iterator.ai_addr();
                                    addr2 = (NetinetIn.sockaddr_in) itr.ai_addr();
                                    if (addr1.sin_addr().s_addr() == addr2.sin_addr().s_addr()) {
                                        skip = true;
                                        break;
                                    }
                                } else {
                                    NetinetIn.sockaddr_in6 addr1;
                                    NetinetIn.sockaddr_in6 addr2;
                                    addr1 = (NetinetIn.sockaddr_in6) iterator.ai_addr();
                                    addr2 = (NetinetIn.sockaddr_in6) itr.ai_addr();
                                    int t;
                                    for (t = 0; t < 16; t++) {
                                        if (addr1.sin6_addr().s6_addr().read(t) != addr2.sin6_addr().s6_addr().read(t)) {
                                            break;
                                        }
                                    }
                                    if (t < 16) {
                                        itr = itr.ai_next();
                                        continue;
                                    } else {
                                        skip = true;
                                        break;
                                    }
                                }
                            } else if ((iterator.ai_family() != Socket.AF_INET()) && (iterator.ai_family() != Socket.AF_INET6())) {
                                /* we can't handle other family types */
                                skip = true;
                                break;
                            }
                            itr = itr.ai_next();
                        }
                        if (!skip) {
                            Netdb.addrinfo next = LibC.malloc(SizeOf.unsigned(Netdb.addrinfo.class));
                            if (next.isNull()) {
                                throw new OutOfMemoryError("malloc failed");
                            }
                            LibC.memcpy(next, iterator, SizeOf.unsigned(Netdb.addrinfo.class));
                            next.set_ai_next(WordFactory.nullPointer());
                            if (resNew.isNull()) {
                                resNew = next;
                            } else {
                                last.set_ai_next(next);
                            }
                            last = next;
                            i++;
                            if (iterator.ai_family() == Socket.AF_INET()) {
                                inetCount++;
                            } else if (iterator.ai_family() == Socket.AF_INET6()) {
                                inet6Count++;
                            }
                        }
                        iterator = iterator.ai_next();
                    }
                    retLen = i;
                    iterator = resNew;

                    ret = new InetAddress[retLen];

                    if (GraalServices.Java8OrEarlier) {
                        if (Target_java_net_InetAddress.preferIPv6AddressJDK8OrEarlier) {
                            /* AF_INET addresses will be offset by inet6Count */
                            inetIndex = inet6Count;
                            inet6Index = 0;
                        } else {
                            /* AF_INET6 addresses will be offset by inetCount */
                            inetIndex = 0;
                            inet6Index = inetCount;
                        }
                    } else {
                        if (Target_java_net_InetAddress.preferIPv6AddressJDK9OrLater == Target_java_net_InetAddress.PREFER_IPV6_VALUE) {
                            inetIndex = inet6Count;
                            inet6Index = 0;
                        } else if (Target_java_net_InetAddress.preferIPv6AddressJDK9OrLater == Target_java_net_InetAddress.PREFER_IPV4_VALUE) {
                            inetIndex = 0;
                            inet6Index = inetCount;
                        } else if (Target_java_net_InetAddress.preferIPv6AddressJDK9OrLater == Target_java_net_InetAddress.PREFER_SYSTEM_VALUE) {
                            inetIndex = 0;
                            inet6Index = 0;
                            originalIndex = 0;
                        }
                    }

                    while (iterator.isNonNull()) {
                        int ret1;
                        if (iterator.ai_family() == Socket.AF_INET()) {
                            Inet4Address iaObj = Util_java_net_Inet4Address.new_Inet4Address();
                            JavaNetNetUtil.setInetAddress_addr(iaObj, NetinetIn.ntohl(((NetinetIn.sockaddr_in) iterator.ai_addr()).sin_addr().s_addr()));
                            JavaNetNetUtil.setInetAddress_hostName(iaObj, host);
                            ret[inetIndex | originalIndex] = iaObj;
                            inetIndex++;
                        } else if (iterator.ai_family() == Socket.AF_INET6()) {
                            // 455 jint scope = 0;
                            int scope = 0;
                            // 457 jobject iaObj = (*env)->NewObject(env, ni_ia6cls, ni_ia6ctrID);
                            Inet6Address iaObj = Util_java_net_Inet6Address.new_Inet6Address();
                            // 458 if (IS_NULL(iaObj)) {
                            if (iaObj == null) {
                                // 459 ret = NULL;
                                ret = null;
                                // 460 goto cleanupAndReturn;
                                return ret;
                            }
                            // 462 ret1 = setInet6Address_ipaddress(env, iaObj, (char *)&(((struct sockaddr_in6*)iterator->ai_addr)->sin6_addr));
                            ret1 = JavaNetNetUtil.setInet6Address_ipaddress(iaObj, ((NetinetIn.sockaddr_in6) iterator.ai_addr()).sin6_addr().s6_addr());
                            // 463 if (!ret1) {
                            if (!CTypeConversion.toBoolean(ret1)) {
                                // 464 ret = NULL;
                                ret = null;
                                // 465 goto cleanupAndReturn;
                                return ret;
                            }
                            // 468 scope = ((struct sockaddr_in6*)iterator->ai_addr)->sin6_scope_id;
                            scope = ((NetinetIn.sockaddr_in6) iterator.ai_addr()).sin6_scope_id();
                            // 469 if (scope != 0) { /* zero is default value, no need to set */
                            if (scope != 0) { /* zero is default value, no need to set */
                                // 470 setInet6Address_scopeid(env, iaObj, scope);
                                JavaNetNetUtil.setInet6Address_scopeid(iaObj, scope);
                            }
                            // 472 setInetAddress_hostName(env, iaObj, host);
                            JavaNetNetUtil.setInetAddress_hostName(iaObj, host);
                            // 473 (*env)->SetObjectArrayElement(env, ret, inet6Index, iaObj);
                            ret[inet6Index | originalIndex] = iaObj;
                            // 474 inet6Index++;
                            inet6Index++;
                        }
                        if (!GraalServices.Java8OrEarlier) {
                            if (Target_java_net_InetAddress.preferIPv6AddressJDK9OrLater == Target_java_net_InetAddress.PREFER_SYSTEM_VALUE) {
                                originalIndex++;
                                inetIndex = 0;
                                inet6Index = 0;
                            }
                        }
                        iterator = iterator.ai_next();
                    }
                }
            } finally {
                /* cleanupAndReturn: */
                Netdb.addrinfo iterator;
                Netdb.addrinfo tmp;
                iterator = resNew;
                while (iterator.isNonNull()) {
                    tmp = iterator;
                    iterator = iterator.ai_next();
                    LibC.free(tmp);
                }
                Netdb.freeaddrinfo(res);
            }
            // JNU_ReleaseStringPlatformChars(env, host, hostname)
            // happens when I exit the CCharPointerHolder region.
        }
        return ret;
    }
    // @formatter:on

}

final class Util_java_net_Inet6AddressImpl {

    static InetAddress[] lookupIfLocalhost(CCharPointer hostname, boolean includeV6) throws SocketException, InterruptedException {
        /* #ifdef MACOSX */
        if (IsDefined.MACOSX()) {
            /* also called from Inet4AddressImpl.c */
            InetAddress[] result = null;
            CCharPointer myhostname = StackValue.get(Netdb.NI_MAXHOST() + 1, CCharPointer.class);
            Ifaddrs.ifaddrs ifa = WordFactory.nullPointer();
            Ifaddrs.ifaddrsPointer ifaPointer = StackValue.get(Ifaddrs.ifaddrsPointer.class);
            int i;
            int j;
            int addrs4 = 0;
            int addrs6 = 0;
            int numV4Loopbacks = 0;
            int numV6Loopbacks = 0;
            boolean includeLoopback = false;
            String name;
            /*
             * If the requested name matches this host's hostname, return IP addresses from all
             * attached interfaces. (#2844683 et al) This prevents undesired PPP dialup, but may
             * return addresses that don't actually correspond to the name (if the name actually
             * matches something in DNS etc.
             */
            myhostname.write(0, (byte) '\0');
            if (Unistd.gethostname(myhostname, WordFactory.unsigned(Netdb.NI_MAXHOST())) == 0) {
                /* Something went wrong, maybe networking is not setup? */
                return null;
            }
            myhostname.write(Netdb.NI_MAXHOST(), (byte) '\0');
            if (LibC.strcmp(myhostname, hostname) != 0) {
                // Non-self lookup
                return null;
            }
            try {
                if (Ifaddrs.getifaddrs(ifaPointer) != 0) {
                    JavaNetNetUtilMD.NET_ThrowNew(Errno.errno(), "Can't get local interface addresses");
                    return null;
                }
                ifa = ifaPointer.read();
                name = CTypeConversion.toJavaString(hostname);
                /*
                 * Iterate over the interfaces, and total up the number of IPv4 and IPv6 addresses
                 * we have. Also keep a count of loopback addresses. We need to exclude them in the
                 * normal case, but return them if we don't get an IP address.
                 */
                Ifaddrs.ifaddrs iter = ifa;
                while (iter.isNonNull()) {
                    int family = iter.ifa_addr().sa_family();
                    if ((iter.ifa_name().read(0) != '\0') && iter.ifa_addr().isNonNull()) {
                        boolean isLoopback = ((iter.ifa_flags() & NetIf.IFF_LOOPBACK()) != 0);
                        if (family == Socket.AF_INET()) {
                            addrs4++;
                            if (isLoopback) {
                                numV4Loopbacks++;
                            }
                        } else if ((family == Socket.AF_INET6()) && includeV6) {
                            addrs6++;
                            if (isLoopback) {
                                numV6Loopbacks++;
                            }
                        } else {
                            /* We don't care e.g. AF_LINK */
                        }
                    }
                    iter = iter.ifa_next();
                }
                if ((addrs4 == numV4Loopbacks) && (addrs6 == numV6Loopbacks)) {
                    // We don't have a real IP address, just loopback. We need to include
                    // loopback in our results.
                    includeLoopback = true;
                }
                /* Create and fill the Java array. */
                int arraySize = addrs4 + addrs6 - (includeLoopback ? 0 : (numV4Loopbacks + numV6Loopbacks));
                result = new InetAddress[arraySize];
                if (GraalServices.Java8OrEarlier) {
                    if (Target_java_net_InetAddress.preferIPv6AddressJDK8OrEarlier) {
                        i = includeLoopback ? addrs6 : (addrs6 - numV6Loopbacks);
                        j = 0;
                    } else {
                        i = 0;
                        j = includeLoopback ? addrs4 : (addrs4 - numV4Loopbacks);
                    }
                } else {
                    /* TODO: `i` and `j` need to be initialized. But to what values? */
                    i = 0;
                    j = 0;
                    throw VMError.unsupportedFeature("JDK9OrLater: PosixJavaNetSubstitutions.Util_java_net_Inet6AddressImpl.lookupIfLocalhost: https://bugs.openjdk.java.net/browse/JDK-8205076");
                }
                // Now loop around the ifaddrs
                iter = ifa;
                while (iter.isNonNull()) {
                    boolean isLoopback = ((iter.ifa_flags() & NetIf.IFF_LOOPBACK()) != 0);
                    int family = iter.ifa_addr().sa_family();
                    if ((iter.ifa_name().read(0) != '\0') && (iter.ifa_addr().isNonNull()) && ((family == Socket.AF_INET()) || ((family == Socket.AF_INET6() && includeV6))) &&
                                    ((!isLoopback) || includeLoopback)) {
                        int index = (family == Socket.AF_INET()) ? i++ : j++;
                        // The space pointed to by portPointer is unused here,
                        // but I have to allocate it because it gets written by the call.
                        CIntPointer portPointer = StackValue.get(CIntPointer.class);
                        InetAddress o = JavaNetNetUtil.NET_SockaddrToInetAddress(iter.ifa_addr(), portPointer);
                        if (o != null) {
                            throw new OutOfMemoryError("Object allocation failed");
                        }
                        JavaNetNetUtil.setInetAddress_hostName(o, name);
                        result[index] = o;
                    }
                    iter = iter.ifa_next();
                }
            } finally {
                /* done: */
                Ifaddrs.freeifaddrs(ifa);
            }
            return result;
            // #endif MACOSX
        } else {
            return null;
        }
    }
}

@TargetClass(java.net.NetworkInterface.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_NetworkInterface {

    /* Aliases to get visibility to fields. */

    @Alias static int defaultIndex;

    @Alias String name;
    @Alias String displayName;
    @Alias int index;
    @Alias InetAddress addrs[];
    @Alias InterfaceAddress bindings[];
    @Alias NetworkInterface childs[];
    @Alias NetworkInterface parent;
    @Alias boolean virtual;

    @Alias
    public Target_java_net_NetworkInterface() {
    }

    @Substitute
    @SuppressWarnings({"unused"})
    static NetworkInterface getByName0(String name) throws SocketException {
        VMError.unimplemented();
        return null;
    }

    // { Do not format quoted code: @formatter:off
    // Translation of jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10.
    // 410 /*
    // 411  * Class:     java_net_NetworkInterface
    // 412  * Method:    getAll
    // 413  * Signature: ()[Ljava/net/NetworkInterface;
    // 414  */
    // 415 JNIEXPORT jobjectArray JNICALL Java_java_net_NetworkInterface_getAll
    // 416     (JNIEnv *env, jclass cls) {
    @Substitute
    static NetworkInterface[] getAll() {
        // 417
        // 418     netif *ifs, *curr;
        JavaNetNetworkInterface.netif ifs;
        JavaNetNetworkInterface.netif curr;
        // 419     jobjectArray netIFArr;
        NetworkInterface[] netIFArr;
        // 420     jint arr_index, ifCount;
        int arr_index;
        int ifCount;
        // 421
        // 422     ifs = enumInterfaces(env);
        ifs = JavaNetNetworkInterface.enumInterfaces();
        // 423     if (ifs == NULL) {
        if (ifs == null) {
            // 424         return NULL;
            return null;
        }
        // 426
        // 427     /* count the interface */
        // 428     ifCount = 0;
        ifCount = 0;
        // 429     curr = ifs;
        curr = ifs;
        // 430     while (curr != NULL) {
        while (curr != null) {
            // 431         ifCount++;
            ifCount++;
            // 432         curr = curr->next;
            curr = curr.next;
        }
        // 434
        // 435     /* allocate a NetworkInterface array */
        // 436     netIFArr = (*env)->NewObjectArray(env, ifCount, cls, NULL);
        netIFArr = new NetworkInterface[ifCount];
        // 437     if (netIFArr == NULL) {
        /* Dead code. */
        // 438         freeif(ifs);
        // 439         return NULL;
        // 440     }
        // 441
        // 442     /*
        // 443      * Iterate through the interfaces, create a NetworkInterface instance
        // 444      * for each array element and populate the object.
        // 445      */
        // 446     curr = ifs;
        curr = ifs;
        // 447     arr_index = 0;
        arr_index = 0;
        // 448     while (curr != NULL) {
        while (curr != null) {
            // 449         jobject netifObj;
            NetworkInterface netifObj;
            // 450
            // 451         netifObj = createNetworkInterface(env, curr);
            netifObj = JavaNetNetworkInterface.createNetworkInterface(curr);
            // 452         if (netifObj == NULL) {
            if (netifObj == null) {
                // 453             freeif(ifs);
                /* `ifs` is heap-allocated. */
                // 454             return NULL;
                return null;
            }
            // 456
            // 457         /* put the NetworkInterface into the array */
            // 458         (*env)->SetObjectArrayElement(env, netIFArr, arr_index++, netifObj);
            netIFArr[arr_index++] = netifObj;
            // 459
            // 460         curr = curr->next;
            curr = curr.next;
        }
        // 462
        // 463     freeif(ifs);
        /* `ifs` is heap-allocated. */
        // 464     return netIFArr;
        return netIFArr;
        }

        @Substitute
        @SuppressWarnings({"unused"})
        private static NetworkInterface getByIndex0(int index) {
            throw VMError.unsupportedFeature("Unimplemented: java.net.NetworkInterface.getByIntexO(int)");
        }

        @Substitute
        @SuppressWarnings({"unused"})
        private static NetworkInterface getByInetAddress0(InetAddress addr) {
            throw VMError.unsupportedFeature("Unimplemented: java.net.getByInetAddress0.getByIntexO(InetAddress)");
        }
}
    // } Do not format quoted code: @formatter:on

class Util_java_net_NetworkInterface {

    static NetworkInterface toNetworkInterface(Target_java_net_NetworkInterface tjnni) {
        return KnownIntrinsics.unsafeCast(tjnni, NetworkInterface.class);
    }

    static Target_java_net_NetworkInterface fromNetworkInterface(NetworkInterface ni) {
        return KnownIntrinsics.unsafeCast(ni, Target_java_net_NetworkInterface.class);
    }

    public static NetworkInterface newNetworkInterface() {
        return toNetworkInterface(new Target_java_net_NetworkInterface());
    }
}

@TargetClass(java.net.Socket.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_Socket {

    @Alias boolean oldImpl;

    // TODO: I do not support old (pre-JDK-1.4) implementations of Socket.
    @Substitute
    private void checkOldImpl() {
        oldImpl = false;
    }
}

/**
 * Note to self: Note the use of <blockquote>@TargetClass(className = "foo.Bar")</blockquote> rather
 * than <blockquote>@TargetClass(foo.Bar.class)</blockquote> when foo.Bar is not visible to me as a
 * class.
 */
@TargetClass(className = "java.net.SocketInputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_SocketInputStream {

    /* Do not re-format commeted-out code: @formatter:off
    // 055 /*
    // 056  * Class: java_net_SocketInputStream
    // 057  * Method: socketRead0
    // 058  * Signature: (Ljava/io/FileDescriptor;[BIII)I
    // 059  */
    // 060 JNIEXPORT jint JNICALL
    // 061 Java_java_net_SocketInputStream_socketRead0(JNIEnv *env, jobject this,
    // 062                                             jobject fdObj, jbyteArray data,
    // 063                                             jint off, jint len, jint timeout)
    @Substitute
    @SuppressWarnings({"static-method", "finally"})
    private int socketRead0(FileDescriptor fdObj, byte[] data, int off, int lenArg, int timeout) throws IOException, OutOfMemoryError, sun.net.ConnectionResetException {
        int len = lenArg;
        // 065     char BUF[MAX_BUFFER_LEN];
        CCharPointer BUF = StackValue.get(JavaNetNetUtilMD.MAX_BUFFER_LEN(), CCharPointer.class);
        // 066     char *bufP;
        CCharPointer bufP = WordFactory.nullPointer();
        // 067     jint fd, nread;
        int fd;
        int nread;
        // 069     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 070         /* shouldn't this be a NullPointerException? -br */
            // 071         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            // 072                         "Socket closed");
            // 073         return -1;
            throw new SocketException("Socket closed");
        } else {
            // 075         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
            // 076         /* Bug 4086704 - If the Socket associated with this file descriptor
            // 077          * was closed (sysCloseFD), then the file descriptor is set to -1.
            // 078          */
            // 079         if (fd == -1) {
            if (fd == -1) {
                // 080         JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
                // 081         return -1;
                throw new SocketException("Socket closed");
            }
        }
        // 085     /*
        // 086      * If the read is greater than our stack allocated buffer then
        // 087      * we allocate from the heap (up to a limit)
        // 088      */
        // 089     if (len > MAX_BUFFER_LEN) {
        if (len > JavaNetNetUtilMD.MAX_BUFFER_LEN()) {
            // 090      if (len > MAX_HEAP_BUFFER_LEN) {
            if (len > JavaNetNetUtilMD.MAX_HEAP_BUFFER_LEN()) {
                // 091      len = MAX_HEAP_BUFFER_LEN;
                len = JavaNetNetUtilMD.MAX_HEAP_BUFFER_LEN();
            }
            // 093          bufP = (char *)malloc((size_t)len);
            bufP = LibC.malloc(WordFactory.unsigned(len));
            // 094          if (bufP == NULL) {
            if (bufP.isNull()) {
                // 095         bufP = BUF;
                bufP = BUF;
                // 096         len = MAX_BUFFER_LEN;
                len = JavaNetNetUtilMD.MAX_BUFFER_LEN();
            }
        } else {
            // 099     bufP = BUF;
            bufP = BUF;
        }
        // 102     if (timeout) {
        if (CTypeConversion.toBoolean(timeout)) {
            // 103         nread = NET_Timeout(fd, timeout);
            nread = JavaNetNetUtilMD.NET_Timeout(fd, timeout);
            // 104         if (nread <= 0) {
            if (nread <= 0) {
                try {
                    // 105             if (nread == 0) {
                    if (nread == 0) {
                        // 106                 JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                        // 107                             "Read timed out");
                        throw new SocketTimeoutException("Read timed out");
                        // 108             } else if (nread == JVM_IO_ERR) {
                    } else if (nread == Target_jvm.JVM_IO_ERR()) {
                        // 109                 if (errno == EBADF) {
                        if (Errno.errno() == Errno.EBADF()) {
                            // 110                      JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
                            throw new SocketException("Socket closed");
                            // 111                  } else if (errno == ENOMEM) {
                        } else if (Errno.errno() == Errno.ENOMEM()) {
                            // 112                      JNU_ThrowOutOfMemoryError(env, "NET_Timeout native heap allocation failed");
                            throw new OutOfMemoryError("NET_Timeout native heap allocation failed");
                        } else {
                            // 114                      NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                            // 115                                                   "select/poll failed");
                            throw new SocketException(PosixUtils.lastErrorString("select/poll failed"));
                        }
                        // 117             } else if (nread == JVM_IO_INTR) {
                    } else if (nread == Target_jvm.JVM_IO_INTR()) {
                        // 118                 JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        // 119                             "Operation interrupted");
                        throw new InterruptedException("Operation interrupted");
                    }
                } finally {
                    // 121             if (bufP != BUF) {
                    if (bufP.notEqual(BUF) && bufP.isNonNull()) {
                        // 122                 free(bufP);
                        LibC.free(bufP);
                        bufP = WordFactory.nullPointer();
                    }
                    // 124             return -1;
                    return -1;
                }
            }
        }
        try {
            // 128     nread = NET_Read(fd, bufP, len);
            nread = JavaNetNetUtilMD.NET_Read(fd, bufP, len);
            // 130     if (nread <= 0) {
            if (nread <= 0) {
                // 131         if (nread < 0) {
                if (nread < 0) {
                    // 133             switch (errno) {
                    // 134                 case ECONNRESET:
                    // 135                 case EPIPE:
                    if ((Errno.errno() == Errno.ECONNRESET()) || (Errno.errno() == Errno.EPIPE())) {
                        // 136                     JNU_ThrowByName(env, "sun/net/ConnectionResetException",
                        // 137                         "Connection reset");
                        // 138                     break;
                        throw new sun.net.ConnectionResetException("Connection reset");
                        // 140                 case EBADF:
                    } else if (Errno.errno() == Errno.EBADF()) {
                        // 141                     JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        // 142                         "Socket closed");
                        // 143                     break;
                        throw new SocketException("Socket closed");
                        // 145                 case EINTR:
                    } else if (Errno.errno() == Errno.EINTR()) {
                        // 146                      JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        // 147                            "Operation interrupted");
                        // 148                      break;
                        throw new InterruptedIOException("Operation interrupted");
                        // 150                 default:
                    } else {
                        // 151                     NET_ThrowByNameWithLastError(env,
                        // 152                         JNU_JAVANETPKG "SocketException", "Read failed");
                        throw new SocketException(PosixUtils.lastErrorString("Read failed"));
                    }
                }
            } else {
                // 156         (*env)->SetByteArrayRegion(env, data, off, nread, (jbyte *)bufP);
                VmPrimsJNI.SetByteArrayRegion(data, off, nread, bufP);
            }
        } finally {
            // 159     if (bufP != BUF) {
            if (bufP.notEqual(BUF) && bufP.isNonNull()) {
                // 160         free(bufP);
                LibC.free(bufP);
                bufP = WordFactory.nullPointer();
            }
        }
        return nread;
    }
    /* @formatter:on */
}

/** Translations from src/solaris/native/java/net/SocketOutputStream.c. */
@TargetClass(className = "java.net.SocketOutputStream")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_SocketOutputStream {

    /* Do not re-format commented-out code: @formatter:off */
    // 056 /*
    // 057  * Class:     java_net_SocketOutputStream
    // 058  * Method:    socketWrite0
    // 059  * Signature: (Ljava/io/FileDescriptor;[BII)V
    // 060  */
    // 061 JNIEXPORT void JNICALL
    // 062 Java_java_net_SocketOutputStream_socketWrite0(JNIEnv *env, jobject this,
    // 063                                               jobject fdObj,
    // 064                                               jbyteArray data,
    // 065                                               jint off, jint len) {
    @Substitute
    @SuppressWarnings({"static-method", "finally"})
    private void socketWrite0(FileDescriptor fdObj, byte[] data, int offArg, int lenArg) throws IOException {
        /* Local variable copies rather than assign to formal parameter. */
        int off = offArg;
        int len = lenArg;
        // 066     char *bufP;
        CCharPointer bufP = WordFactory.nullPointer();
        // 067     char BUF[MAX_BUFFER_LEN];
        CCharPointer BUF = StackValue.get(JavaNetNetUtilMD.MAX_BUFFER_LEN(), CCharPointer.class);
        // 068     int buflen;
        int buflen;
        // 069     int fd;
        int fd;
        // 071     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 072         JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
            // 073         return;
            throw new SocketException("socket closed");
        } else {
            // 075         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
            // 076         /* Bug 4086704 - If the Socket associated with this file descriptor
            // 077          * was closed (sysCloseFD), the file descriptor is set to -1.
            // 078          */
            // 079         if (fd == -1) {
            if (fd == -1) {
                // 080             JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
                // 081             return;
                throw new SocketException("Socket closed");
            }
        }
        // 086     if (len <= MAX_BUFFER_LEN) {
        if (len <= JavaNetNetUtilMD.MAX_BUFFER_LEN()) {
            // 087         bufP = BUF;
            bufP = BUF;
            // 088         buflen = MAX_BUFFER_LEN;
            buflen = JavaNetNetUtilMD.MAX_BUFFER_LEN();
        } else {
            // 090         buflen = min(MAX_HEAP_BUFFER_LEN, len);
            buflen = Integer.min(JavaNetNetUtilMD.MAX_HEAP_BUFFER_LEN(), len);
            // 091         bufP = (char *)malloc((size_t)buflen);
            bufP = LibC.malloc(WordFactory.unsigned(buflen));
            // 093         /* if heap exhausted resort to stack buffer */
            // 094         if (bufP == NULL) {
            if (bufP.isNull()) {
                // 095             bufP = BUF;
                bufP = BUF;
                // 096             buflen = MAX_BUFFER_LEN;
                buflen = JavaNetNetUtilMD.MAX_HEAP_BUFFER_LEN();
            }
        }
        try {
            // 100     while(len > 0) {
            while (len > 0) {
                // 101         int loff = 0;
                int loff = 0;
                // 102         int chunkLen = min(buflen, len);
                int chunkLen = Integer.min(buflen, len);
                // 103         int llen = chunkLen;
                int llen = chunkLen;
                // 104         (*env)->GetByteArrayRegion(env, data, off, chunkLen, (jbyte *)bufP);
                VmPrimsJNI.GetByteArrayRegion(data, off, chunkLen, bufP);
                // 106         while(llen > 0) {
                while (llen > 0) {
                    try {
                        // 107             int n = NET_Send(fd, bufP + loff, llen, 0);
                        int n = JavaNetNetUtilMD.NET_Send(fd, bufP.addressOf(loff), llen, 0);
                        // 108             if (n > 0) {
                        if (n > 0) {
                            // 109                 llen -= n;
                            llen -= n;
                            // 110                 loff += n;
                            loff += n;
                            // 111                 continue;
                            continue;
                        }
                        // 113             if (n == JVM_IO_INTR) {
                        if (n == Target_jvm.JVM_IO_INTR()) {
                            // 114                 JNU_ThrowByName(env, "java/io/InterruptedIOException", 0);
                            throw new InterruptedIOException();
                        } else {
                            // 116                 if (errno == ECONNRESET) {
                            if (Errno.errno() == Errno.ECONNRESET()) {
                                // 117                     JNU_ThrowByName(env, "sun/net/ConnectionResetException",
                                // 118                         "Connection reset");
                                throw new sun.net.ConnectionResetException("Connection reset");
                            } else {
                                // 120                     NET_ThrowByNameWithLastError(env, "java/net/SocketException",
                                // 121                         "Write failed");
                                throw new SocketException(PosixUtils.lastErrorString("Write failed"));
                            }
                        }
                    } finally {
                        // 124             if (bufP != BUF) {
                        if (bufP.notEqual(BUF) && bufP.isNonNull()) {
                            // 125                 free(bufP);
                            LibC.free(bufP);
                            bufP = WordFactory.nullPointer();
                        }
                        // 127             return;
                        return;
                    }
                }
                // 129         len -= chunkLen;
                len -= chunkLen;
                // 130         off += chunkLen;
                off += chunkLen;
            }
        } finally {
            // 133     if (bufP != BUF) {
            if (bufP.notEqual(BUF) && bufP.isNonNull()) {
                // 134         free(bufP);
                LibC.free(bufP);
                bufP = WordFactory.nullPointer();
            }
        }
    }
    /* @formatter:on */
}

@TargetClass(className = "java.net.SocketImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_SocketImpl {

    /* Aliases to get visibility for substituted methods. */

    @Alias ServerSocket serverSocket;

    @Alias FileDescriptor fd;

    @Alias InetAddress address;

    @Alias int port;

    @Alias int localport;
}

/** Methods to operate on java.net.SocketImpl instances. */
class Util_java_net_SocketImpl {

    /** Private constructor: No instances. */
    private Util_java_net_SocketImpl() {
    }

    /*
     * Conversion between the Java and substitution type systems.
     */

    static Target_java_net_SocketImpl from_SocketImpl(SocketImpl socketImpl) {
        return KnownIntrinsics.unsafeCast(socketImpl, Target_java_net_SocketImpl.class);
    }
}

// 044 abstract class AbstractPlainSocketImpl extends SocketImpl
@TargetClass(className = "java.net.AbstractPlainSocketImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_AbstractPlainSocketImpl {

    /* Aliases to get visibility for substituted methods. */
    // 046 /* instance variable for SO_TIMEOUT */
    // 047 int timeout; // timeout in millisec
    @Alias int timeout;

    // 048 // traffic class
    // 049 private int trafficClass;
    @Alias int trafficClass;
}

/** Translations from jdk/src/solaris/native/java/net/PlainSocketImpl.c */
@TargetClass(className = "java.net.PlainSocketImpl")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_java_net_PlainSocketImpl {

    /* Substitutions for native methods. */

    /* Do not re-format commented-out code: @formatter:off */
    @SuppressWarnings("finally")
    @Substitute
    // 176 /*
    // 177  * Class:     java_net_PlainSocketImpl
    // 178  * Method:    socketCreate
    // 179  * Signature: (Z)V */
    // 180 JNIEXPORT void JNICALL
    // 181 Java_java_net_PlainSocketImpl_socketCreate(JNIEnv *env, jobject this,
    // 182                                            jboolean stream) {
    void socketCreate(boolean stream) throws IOException, InterruptedException {
        // 183 jobject fdObj, ssObj;
        FileDescriptor fdObj;
        ServerSocket ssObj;
        // 184 int fd;
        int fd;
        // 185 int type = (stream ? SOCK_STREAM : SOCK_DGRAM);
        int type = (stream ? Socket.SOCK_STREAM() : Socket.SOCK_DGRAM());
        // 186 #ifdef AF_INET6
        int domain;
        if (IsDefined.socket_AF_INET6()) {
            // 187 int domain = ipv6_available() ? AF_INET6 : AF_INET;
            domain = JavaNetNetUtil.ipv6_available() ? Socket.AF_INET6() : Socket.AF_INET();
        } else {
            // 189 int domain = AF_INET;
            domain = Socket.AF_INET();
        }
        // 190 #endif
        // 191
        // 192 if (socketExceptionCls == NULL) {
        // 193 jclass c = (*env)->FindClass(env, "java/net/SocketException");
        // 194 CHECK_NULL(c);
        // 195 socketExceptionCls = (jclass)(*env)->NewGlobalRef(env, c);
        // 196 CHECK_NULL(socketExceptionCls);
        // 197 }
        // 198 fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 199
        // 200 if (fdObj == NULL) {
        // 201 (*env)->ThrowNew(env, socketExceptionCls, "null fd object");
        // 202 return;
        // 203 }
        if (fdObj == null) {
            throw new SocketException("null fd object");
        }
        // 204
        // 205 if ((fd = JVM_Socket(domain, type, 0)) == JVM_IO_ERR) {
        if ((fd = Socket.socket(domain, type, 0)) == Target_jvm.JVM_IO_ERR()) {
            // 206 /* note: if you run out of fds, you may not be able to load
            // 207 * the exception class, and get a NoClassDefFoundError
            // 208 * instead.
            // 209 */
            // 210 NET_ThrowNew(env, errno, "can't create socket");
            JavaNetNetUtilMD.NET_ThrowNew(Errno.errno(), "can't create socket");
            // 211 return;
            return;
        }
        // 214 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 215 /* Disable IPV6_V6ONLY to ensure dual-socket support */
            // 216 if (domain == AF_INET6) {
            if (domain == Socket.AF_INET6()) {
                // 217 int arg = 0;
                CIntPointer argPointer = StackValue.get(CIntPointer.class);
                argPointer.write(0);
                // 218 if (setsockopt(fd, IPPROTO_IPV6(), IPV6_V6ONLY, (char*)&arg,
                // 219 sizeof(int)) < 0) {
                if (Socket.setsockopt(fd, NetinetIn.IPPROTO_IPV6(), NetinetIn.IPV6_V6ONLY(), argPointer, SizeOf.get(CIntPointer.class)) < 0) {
                    try {
                        // 220 NET_ThrowNew(env, errno, "cannot set IPPROTO_IPV6");
                        JavaNetNetUtilMD.NET_ThrowNew(Errno.errno(), "cannot set IPPROTO_IPV6");
                    } finally {
                        // 221 close(fd);
                        Unistd.close(fd);
                        // 222 return;
                        return;
                    }
                }
            }
        }
        // 225 #endif /* AF_INET6 */
        // 226
        // 227 /*
        // 228 * If this is a server socket then enable SO_REUSEADDR
        // 229 * automatically and set to non blocking.
        // 230 */
        // 231 ssObj = (*env)->GetObjectField(env, this, psi_serverSocketID);
        ssObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).serverSocket;
        // 232 if (ssObj != NULL) {
        if (ssObj != null) {
            // 233 int arg = 1;
            CIntPointer argPointer = StackValue.get(CIntPointer.class);
            argPointer.write(1);
            // 234 SET_NONBLOCKING(fd);
            Util_java_net_PlainSocketImpl.SET_NONBLOCKING(fd);
            // 235 if (JVM_SetSockOpt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg,
            // 236 sizeof(arg)) < 0) {
            if (Socket.setsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_REUSEADDR(), argPointer, SizeOf.get(CIntPointer.class)) < 0) {
                try {
                    // 237 NET_ThrowNew(env, errno, "cannot set SO_REUSEADDR");
                    JavaNetNetUtilMD.NET_ThrowNew(Errno.errno(), "cannot set SO_REUSEADDR");
                } finally {
                    // 238 close(fd);
                    Unistd.close(fd);
                    // 239 return;
                }
            }
        }
        // 243 (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);
        PosixUtils.setFD(fdObj, fd);
        /* @formatter:on */
    }

    /* Do not re-format commented-out code: @formatter:off */
    // 798 /*
    // 799  * Class:     java_net_PlainSocketImpl
    // 800  * Method:    socketAvailable
    // 801  * Signature: ()I
    // 802  */
    // 803 JNIEXPORT jint JNICALL
    // 804 Java_java_net_PlainSocketImpl_socketAvailable(JNIEnv *env, jobject this) {
    @Substitute
    int socketAvailable() throws IOException {
        // 806     jint ret = -1;
        CIntPointer ret_Pointer = StackValue.get(CIntPointer.class);
        ret_Pointer.write(-1);
        // 807     jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 808     jint fd;
        int fd;
        // 810     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 811         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            // 812                         "Socket closed");
            throw new SocketException("Socket closed");
            // 813         return -1;
        } else {
            // 815         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 817     /* JVM_SocketAvailable returns 0 for failure, 1 for success */
        // 818     if (!JVM_SocketAvailable(fd, &ret)){
        if (! CTypeConversion.toBoolean(VmPrimsJVM.JVM_SocketAvailable(fd, ret_Pointer))) {
            // 819         if (errno == ECONNRESET) {
            if (Errno.errno() == Errno.ECONNRESET()) {
                // 820             JNU_ThrowByName(env, "sun/net/ConnectionResetException", "");
                throw new sun.net.ConnectionResetException("");
            } else {
                // 822             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                // 823                                          "ioctl FIONREAD failed");
                throw new SocketException(PosixUtils.lastErrorString("ioctl FIONREAD failed"));
            }
        }
        // 826     return ret;
        return ret_Pointer.read();
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    // 543 /*
    // 544  * Class:     java_net_PlainSocketImpl
    // 545  * Method:    socketBind
    // 546  * Signature: (Ljava/net/InetAddress;I)V
    // 547  */
    // 548 JNIEXPORT void JNICALL
    // 549 Java_java_net_PlainSocketImpl_socketBind(JNIEnv *env, jobject this,
    // 550                                          jobject iaObj, jint localport) {
    @Substitute
    void socketBind(InetAddress iaObj, int localportArg) throws IOException {
        int localport = localportArg;
        // 552     /* fdObj is the FileDescriptor field on this */
        // 553     jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 554     /* fd is an int field on fdObj */
        // 555     int fd;
        int fd;
        // 556     int len;
        CIntPointer len_Pointer = StackValue.get(CIntPointer.class);
        len_Pointer.write(0);
        // 557     SOCKADDR him;
        Socket.sockaddr him = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
        // 559     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
        // 560         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
        // 561                         "Socket closed");
        // 562         return;
            throw new SocketException("Socket closed");
        } else {
        // 564         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 566     if (IS_NULL(iaObj)) {
        if (iaObj == null) {
        // 567         JNU_ThrowNullPointerException(env, "iaObj is null.");
        // 568         return;
            throw new NullPointerException("iaObj is null.");
        }
        // 571     /* bind */
        // 572     if (NET_InetAddressToSockaddr(env, iaObj, localport, (struct sockaddr *)&him, &len, JNI_TRUE) != 0) {
        if (JavaNetNetUtilMD.NET_InetAddressToSockaddr(iaObj, localport, him, len_Pointer, Util_jni.JNI_TRUE()) != 0) {
        // 573       return;
            return;
        }
        // 575     setDefaultScopeID(env, (struct sockaddr *)&him);
        JavaNetNetUtilMD.setDefaultScopeID(him);
        // 577     if (NET_Bind(fd, (struct sockaddr *)&him, len) < 0) {
        if (JavaNetNetUtilMD.NET_Bind(fd, him, len_Pointer.read()) < 0) {
        // 578         if (errno == EADDRINUSE || errno == EADDRNOTAVAIL ||
        // 579             errno == EPERM || errno == EACCES) {
            if (Errno.errno() == Errno.EADDRINUSE() || Errno.errno() == Errno.EADDRNOTAVAIL() || //
                            Errno.errno() == Errno.EPERM() || Errno.errno() == Errno.EACCES()) {
        // 580             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "BindException",
        // 581                            "Bind failed");
                throw new BindException(PosixUtils.lastErrorString("Bind failed"));
            } else {
        // 583             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
        // 584                            "Bind failed");
                throw new SocketException(PosixUtils.lastErrorString("Bind failed"));
            }
        // 586         return;
        }
        // 589     /* set the address */
        // 590     (*env)->SetObjectField(env, this, psi_addressID, iaObj);
        Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).address = iaObj;
        // 592     /* initialize the local port */
        // 593     if (localport == 0) {
        if (localport == 0) {
        // 594         /* Now that we're a connected socket, let's extract the port number
        // 595          * that the system chose for us and store it in the Socket object.
        // 596          */
        // 597         if (JVM_GetSockName(fd, (struct sockaddr *)&him, &len) == -1) {
            if (VmPrimsJVM.JVM_GetSockName(fd, him, len_Pointer) == -1) {
        // 598             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
        // 599                            "Error getting socket name");
        // 600             return;
                throw new SocketException(PosixUtils.lastErrorString("Error getting socket name"));
            }
        // 602         localport = NET_GetPortFromSockaddr((struct sockaddr *)&him);
            localport = JavaNetNetUtilMD.NET_GetPortFromSockaddr(him);
        // 603         (*env)->SetIntField(env, this, psi_localportID, localport);
            Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).localport = localport;
        } else {
        // 605         (*env)->SetIntField(env, this, psi_localportID, localport);
            Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).localport = localport;
        }
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    // 829 /*
    // 830  * Class:     java_net_PlainSocketImpl
    // 831  * Method:    socketClose0
    // 832  * Signature: (Z)V
    // 833  */
    // 834 JNIEXPORT void JNICALL
    // 835 Java_java_net_PlainSocketImpl_socketClose0(JNIEnv *env, jobject this,
    // 836                                           jboolean useDeferredClose) {
    @Substitute
    void socketClose0(boolean useDeferredClose) throws IOException {
        // 838     jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 839     jint fd;
        int fd;
        // 841     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 842         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            // 843                         "socket already closed");
            throw new SocketException("socket already closed");
            // 844         return;
        } else {
            // 846         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 848     if (fd != -1) {
        if (fd != -1) {
            // 849         if (useDeferredClose && marker_fd >= 0) {
            if (useDeferredClose && (Util_java_net_PlainSocketImpl.marker_fd >= 0)) {
                // 850             NET_Dup2(marker_fd, fd);
                JavaNetNetUtilMD.NET_Dup2(Util_java_net_PlainSocketImpl.marker_fd, fd);
            } else {
                // 852             (*env)->SetIntField(env, fdObj, IO_fd_fdID, -1);
                PosixUtils.setFD(fdObj, -1);
                // 853             NET_SocketClose(fd);
                JavaNetNetUtilMD.NET_SocketClose(fd);
            }
        }
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    @Substitute
    @SuppressWarnings({"unused"})
    void socketConnect(InetAddress iaObj, int port, int timeoutArg) throws IOException {
        /* local copy of "timeout" argument so it can be modified. */
        int timeout = timeoutArg;
        // 259    jint localport = (*env)->GetIntField(env, this, psi_localportID);
        int localport = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).localport;
        // 260    int len = 0;
        CIntPointer len_Pointer = StackValue.get(CIntPointer.class);
        len_Pointer.write(0);
        // 262    /* fdObj is the FileDescriptor field on this */
        // 263    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 265    class clazz = (*env)->GetObjectClass(env, this);
        // 267    jobject fdLock;
        Object fdLock;
        // 269    jint trafficClass = (*env)->GetIntField(env, this, psi_trafficClassID);
        int trafficClass = Util_java_net_PlainSocketImpl.as_Target_java_net_AbstractPlainSocketImpl(this).trafficClass;
        // 271    /* fd is an int field on iaObj */
        // 272    jint fd;
        int fd;
        // 274    SOCKADDR him;
        Socket.sockaddr him = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
        // 275    /* The result of the connection */
        // 276    int connect_rv = -1;
        CIntPointer connect_rv_Pointer = StackValue.get(CIntPointer.class);
        connect_rv_Pointer.write(-1);
        // 278    if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 279    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
            throw new SocketException("Socket closed");
            // 280    return;
        } else {
            // 282    fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 284    if (IS_NULL(iaObj)) {
        if (iaObj == null) {
            // 285    JNU_ThrowNullPointerException(env, "inet address argument null.");
            throw new NullPointerException("inet address argument null.");
            // 286    return;
        }
        // 289    /* connect */
        // 290    if (NET_InetAddressToSockaddr(env, iaObj, port, (struct sockaddr *)&him, &len, JNI_TRUE) != 0) {
        if (JavaNetNetUtilMD.NET_InetAddressToSockaddr(iaObj, port, him, len_Pointer, Util_jni.JNI_TRUE()) != 0) {
            // 291    return;
            return;
        }
        // 293    setDefaultScopeID(env, (struct sockaddr *)&him);
        JavaNetNetUtilMD.setDefaultScopeID(him);
        // 295 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 296    if (trafficClass != 0 && ipv6_available()) {
            if ((trafficClass != 0) && JavaNetNetUtil.ipv6_available()) {
                // 297    NET_SetTrafficClass((struct sockaddr *)&him, trafficClass);
                JavaNetNetUtilMD.NET_SetTrafficClass(him, trafficClass);
            }
        }
        // 299 #endif /* AF_INET6 */
        // 300    if (timeout <= 0) {
        if (timeout <= 0) {
            // 301    connect_rv = NET_Connect(fd, (struct sockaddr *)&him, len);
            connect_rv_Pointer.write(JavaNetNetUtilMD.NET_Connect(fd, him, len_Pointer.read()));
            // 302 #ifdef __solaris__
            // 303        if (connect_rv == JVM_IO_ERR && errno == EINPROGRESS ) {
            // 305            /* This can happen if a blocking connect is interrupted by a signal.
            // 306             * See 6343810.
            // 307             */
            // 308        while (1) {
            // 309 #ifndef USE_SELECT
            // 310                 {
            // 311                     struct pollfd pfd;
            // 312                     pfd.fd = fd;
            // 313                     pfd.events = POLLOUT;
            // 314
            // 315                     connect_rv = NET_Poll(&pfd, 1, -1);
            // 316                 }
            // 317 #else /* USE_SELECT */
            // 318                 {
            // 319                     fd_set wr, ex;
            // 320
            // 321                     FD_ZERO(&wr);
            // 322                     FD_SET(fd, &wr);
            // 323                     FD_ZERO(&ex);
            // 324                     FD_SET(fd, &ex);
            // 325
            // 326                     connect_rv = NET_Select(fd+1, 0, &wr, &ex, 0);
            // 327                 }
            // 328 #endif /* USE_SELECT */
            // 329
            // 330                 if (connect_rv == JVM_IO_ERR) {
            // 331                     if (errno == EINTR) {
            // 332                         continue;
            // 333                     } else {
            // 334                         break;
            // 335                     }
            // 336                 }
            // 337                 if (connect_rv > 0) {
            // 338                     int optlen;
            // 339                     /* has connection been established */
            // 340                     optlen = sizeof(connect_rv);
            // 341                     if (JVM_GetSockOpt(fd, SOL_SOCKET, SO_ERROR,
            // 342                                         (void*)&connect_rv, &optlen) <0) {
            // 343                         connect_rv = errno;
            // 344                     }
            // 345
            // 346                     if (connect_rv != 0) {
            // 347                         /* restore errno */
            // 348                         errno = connect_rv;
            // 349                         connect_rv = JVM_IO_ERR;
            // 350                     }
            // 351                     break;
            // 352                 }
            // 353             }
            // 354         }
            // 355 #endif /* __solaris__ */
        } else {
            // 357         /*
            // 358          * A timeout was specified. We put the socket into non-blocking
            // 359          * mode, connect, and then wait for the connection to be
            // 360          * established, fail, or timeout.
            // 361          */
            // 362         SET_NONBLOCKING(fd);
            Util_java_net_PlainSocketImpl.SET_NONBLOCKING(fd);
            // 364         /* no need to use NET_Connect as non-blocking */
            // 365         connect_rv = connect(fd, (struct sockaddr *)&him, len);
            connect_rv_Pointer.write(Socket.connect(fd, him, len_Pointer.read()));
            // 367         /* connection not established immediately */
            // 368         if (connect_rv != 0) {
            if (connect_rv_Pointer.read() != 0) {
                // 369             int optlen;
                CIntPointer optlen_Pointer = StackValue.get(CIntPointer.class);
                // 370             jlong prevTime = JVM_CurrentTimeMillis(env, 0);
                long prevTime = Target_java_lang_System.currentTimeMillis();
                // 372             if (errno != EINPROGRESS) {
                if (Errno.errno() != Errno.EINPROGRESS()) {
                    // 373                 NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                    // 374                              "connect failed");
                    try {
                        throw new ConnectException(PosixUtils.lastErrorString("connect failed"));
                    } finally {
                        // 375                 SET_BLOCKING(fd);
                        Util_java_net_PlainSocketImpl.SET_BLOCKING(fd);
                        // 376                 return;
                    }
                }
                // 379             /*
                // 380              * Wait for the connection to be established or a
                // 381              * timeout occurs. poll/select needs to handle EINTR in
                // 382              * case lwp sig handler redirects any process signals to
                // 383              * this thread.
                // 384              */
                // 385             while (1) {
                while (true) {
                    // 386                 jlong newTime;
                    long newTime;
                    /*
                     * I am assuming that USE_SELECT is not defined.
                     * Cf. https://bugs.openjdk.java.net/browse/JDK-8035949
                     */
                    // 387 #ifndef USE_SELECT
                    // 388                 {
                    // 389                     struct pollfd pfd;
                    Poll.pollfd pfd = StackValue.get(Poll.pollfd.class);
                    // 390                     pfd.fd = fd;
                    pfd.set_fd(fd);
                    // 391                     pfd.events = POLLOUT;
                    pfd.set_events(Poll.POLLOUT());
                    // 393                     errno = 0;
                    Errno.set_errno(0);
                    // 394                     connect_rv = NET_Poll(&pfd, 1, timeout);
                    connect_rv_Pointer.write(JavaNetNetUtilMD.NET_Poll(pfd, 1, timeout));
                    // 395                 }
                    // 396 #else /* USE_SELECT */
                    // 397                 {
                    // 398                     fd_set wr, ex;
                    // 399                     struct timeval t;
                    // 400
                    // 401                     t.tv_sec = timeout / 1000;
                    // 402                     t.tv_usec = (timeout % 1000) * 1000;
                    // 403
                    // 404                     FD_ZERO(&wr);
                    // 405                     FD_SET(fd, &wr);
                    // 406                     FD_ZERO(&ex);
                    // 407                     FD_SET(fd, &ex);
                    // 408
                    // 409                     errno = 0;
                    // 410                     connect_rv = NET_Select(fd+1, 0, &wr, &ex, &t);
                    // 411                 }
                    // 412 #endif /* USE_SELECT */
                    // 414                 if (connect_rv >= 0) {
                    if (connect_rv_Pointer.read() >= 0) {
                        // 415                     break;
                        break;
                    }
                    // 417                 if (errno != EINTR) {
                    if (Errno.errno() != Errno.EINTR()) {
                        // 418                     break;
                        break;
                    }
                    // 421                 /*
                    // 422                  * The poll was interrupted so adjust timeout and
                    // 423                  * restart
                    // 424                  */
                    // 425                 newTime = JVM_CurrentTimeMillis(env, 0);
                    newTime = Target_java_lang_System.currentTimeMillis();
                    // 426                 timeout -= (newTime - prevTime);
                    timeout -= (newTime - prevTime);
                    // 427                 if (timeout <= 0) {
                    if (timeout <= 0) {
                        // 428                     connect_rv = 0;
                        connect_rv_Pointer.write(0);
                        // 429                     break;
                        break;
                    }
                    // 431                 prevTime = newTime;
                    prevTime = newTime;
                } /* while */
                // 435             if (connect_rv == 0) {
                if (connect_rv_Pointer.read() == 0) {
                    try {
                        // 436                 JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                        // 437                             "connect timed out");
                        throw new SocketTimeoutException("connect timed out");
                    } finally {
                        // 439                 /*
                        // 440                  * Timeout out but connection may still be established.
                        // 441                  * At the high level it should be closed immediately but
                        // 442                  * just in case we make the socket blocking again and
                        // 443                  * shutdown input & output.
                        // 444                  */
                        // 445                 SET_BLOCKING(fd);
                        Util_java_net_PlainSocketImpl.SET_BLOCKING(fd);
                        // 446                 JVM_SocketShutdown(fd, 2);
                        VmPrimsJVM.JVM_SocketShutdown(fd, 2);
                        // 447                 return;
                    }
                }
                // 449
                // 450             /* has connection been established */
                // 451             optlen = sizeof(connect_rv);
                optlen_Pointer.write(SizeOf.get(CIntPointer.class));
                // 452             if (JVM_GetSockOpt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv,
                // 453                                &optlen) <0) {
                if (VmPrimsJVM.JVM_GetSockOpt(fd, Socket.SOL_SOCKET(), Socket.SO_ERROR(), (CCharPointer) connect_rv_Pointer, optlen_Pointer) < 0) {
                    // 454                 connect_rv = errno;
                    connect_rv_Pointer.write(Errno.errno());
                }
            }
            // 458         /* make socket blocking again */
            // 459         SET_BLOCKING(fd);
            Util_java_net_PlainSocketImpl.SET_BLOCKING(fd);
            // 461         /* restore errno */
            // 462         if (connect_rv != 0) {
            if (connect_rv_Pointer.read() != 0) {
                // 463             errno = connect_rv;
                Errno.set_errno(connect_rv_Pointer.read());
                // 464             connect_rv = JVM_IO_ERR;
                connect_rv_Pointer.write(Target_jvm.JVM_IO_ERR());
            }
        }
        // 468     /* report the appropriate exception */
        // 469     if (connect_rv < 0) {
        if (connect_rv_Pointer.read() < 0) {
            // 471 #ifdef __linux__
            if (IsDefined.__linux__()) {
                // 472         /*
                // 473          * Linux/GNU distribution setup /etc/hosts so that
                // 474          * InetAddress.getLocalHost gets back the loopback address
                // 475          * rather than the host address. Thus a socket can be
                // 476          * bound to the loopback address and the connect will
                // 477          * fail with EADDRNOTAVAIL. In addition the Linux kernel
                // 478          * returns the wrong error in this case - it returns EINVAL
                // 479          * instead of EADDRNOTAVAIL. We handle this here so that
                // 480          * a more descriptive exception text is used.
                // 481          */
                // 482         if (connect_rv == JVM_IO_ERR && errno == EINVAL) {
                if (connect_rv_Pointer.read() == Target_jvm.JVM_IO_ERR() && Errno.errno() == Errno.EINVAL()) {
                    // 483             JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                    // 484                 "Invalid argument or cannot assign requested address");
                    // 485             return;
                    throw new SocketException("Invalid argument or cannot assign requested address");
                    // 486         }
                }
            }
            // 487 #endif
            // 488         if (connect_rv == JVM_IO_INTR) {
            if (connect_rv_Pointer.read() == Target_jvm.JVM_IO_INTR()) {
                // 489             JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                // 490                             "operation interrupted");
                throw new InterruptedIOException("operation interrupted");
                // 491 #if defined(EPROTO)
                // 492         } else if (errno == EPROTO) {
            } else if (Errno.errno() == Errno.EPROTO()) {
                // 493             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ProtocolException",
                // 494                            "Protocol error");
                throw new ProtocolException(PosixUtils.lastErrorString("Protocol error"));
                // 495 #endif
                // 496         } else if (errno == ECONNREFUSED) {
            } else if (Errno.errno() == Errno.ECONNREFUSED()) {
                // 497             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                // 498                            "Connection refused");
                throw new ConnectException(PosixUtils.lastErrorString("Connection refused"));
                // 499         } else if (errno == ETIMEDOUT) {
            } else if (Errno.errno() == Errno.ETIMEDOUT()) {
                // 500             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                // 501                            "Connection timed out");
                throw new ConnectException(PosixUtils.lastErrorString("Connection timed out"));
                // 502         } else if (errno == EHOSTUNREACH) {
            } else if (Errno.errno() == Errno.EHOSTUNREACH()) {
                // 503             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "NoRouteToHostException",
                // 504                            "Host unreachable");
                throw new NoRouteToHostException(PosixUtils.lastErrorString("Host unreachable"));
                // 505         } else if (errno == EADDRNOTAVAIL) {
            } else if (Errno.errno() == Errno.EADDRNOTAVAIL()) {
                // 506             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "NoRouteToHostException",
                // 507                              "Address not available");
                throw new NoRouteToHostException(PosixUtils.lastErrorString("Address not available"));
                // 508         } else if ((errno == EISCONN) || (errno == EBADF)) {
            } else if ((Errno.errno() == Errno.EISCONN()) || (Errno.errno() == Errno.EBADF())) {
                // 509             JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                // 510                             "Socket closed");
                throw new SocketException(PosixUtils.lastErrorString("Socket closed"));
                // 511         } else {
            } else {
                // 512             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "connect failed");
                throw new SocketException(PosixUtils.lastErrorString("connect failed"));
            }
            // 514         return;
            /* Elided return because the "throw"s above do that. */
        }
        // 517     (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);
        PosixUtils.setFD(fdObj, fd);
        // 519     /* set the remote peer address and port */
        // 520     (*env)->SetObjectField(env, this, psi_addressID, iaObj);
        Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).address = iaObj;
        // 521     (*env)->SetIntField(env, this, psi_portID, port);
        Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).port = port;
        // 523     /*
        // 524      * we need to initialize the local port field if bind was called
        // 525      * previously to the connect (by the client) then localport field
        // 526      * will already be initialized
        // 527      */
        // 528     if (localport == 0) {
        if (localport == 0) {
            // 529         /* Now that we're a connected socket, let's extract the port number
            // 530          * that the system chose for us and store it in the Socket object.
            // 531          */
            // 532         len = SOCKADDR_LEN;
            len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 533         if (JVM_GetSockName(fd, (struct sockaddr *)&him, &len) == -1) {
            if (VmPrimsJVM.JVM_GetSockName(fd, him, len_Pointer) == -1) {
                // 534             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                // 535                            "Error getting socket name");
                throw new SocketException(PosixUtils.lastErrorString("Error getting socket name"));
            } else {
                // 537             localport = NET_GetPortFromSockaddr((struct sockaddr *)&him);
                localport = JavaNetNetUtilMD.NET_GetPortFromSockaddr(him);
                // 538             (*env)->SetIntField(env, this, psi_localportID, localport);
                Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).localport = localport;
            }
        }
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    @Substitute
    // 609 /*
    // 610  * Class:     java_net_PlainSocketImpl
    // 611  * Method:    socketListen
    // 612  * Signature: (I)V
    // 613  */
    // 614 JNIEXPORT void JNICALL
    // 615 Java_java_net_PlainSocketImpl_socketListen (JNIEnv *env, jobject this,
    // 616                                             jint count) {
    void socketListen(int countArg) throws IOException {
        int count = countArg;
        // 618     /* this FileDescriptor fd field */
        // 619     jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 620     /* fdObj's int fd field */
        // 621     int fd;
        int fd;
        // 623     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
        // 624         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
        // 625                         "Socket closed");
        // 626         return;
            throw new SocketException("Socket closed");
        } else {
        // 628         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 631     /*
        // 632      * Workaround for bugid 4101691 in Solaris 2.6. See 4106600.
        // 633      * If listen backlog is Integer.MAX_VALUE then subtract 1.
        // 634      */
        // 635     if (count == 0x7fffffff)
        if (count == Integer.MAX_VALUE) {
        // 636         count -= 1;
            count -= 1;
        }
        // 638     if (JVM_Listen(fd, count) == JVM_IO_ERR) {
        if (VmPrimsJVM.JVM_Listen(fd, count) == JavavmExportJvm.JvmIoErrorCode.JVM_IO_ERR()) {
        // 639         NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
        // 640                        "Listen failed");
            throw new SocketException(PosixUtils.lastErrorString("Listen failed"));
        }
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    // 644 /*
    // 645  * Class:     java_net_PlainSocketImpl
    // 646  * Method:    socketAccept
    // 647  * Signature: (Ljava/net/SocketImpl;)V
    // 648  */
    // 649 JNIEXPORT void JNICALL
    // 650 Java_java_net_PlainSocketImpl_socketAccept(JNIEnv *env, jobject this,
    // 651                                            jobject socket) {
    @Substitute
    void socketAccept(SocketImpl socket) throws IOException {
        // 653     /* fields on this */
        // 654     int port;
        CIntPointer port_Pointer = StackValue.get(CIntPointer.class);
        // 655     jint timeout = (*env)->GetIntField(env, this, psi_timeoutID);
        int timeout = Util_java_net_PlainSocketImpl.as_Target_java_net_AbstractPlainSocketImpl(this).timeout;
        // 656     jlong prevTime = 0;
        long prevTime = 0;
        // 657     jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 658
        // 659     /* the FileDescriptor field on socket */
        // 660     jobject socketFdObj;
        FileDescriptor socketFdObj;
        // 661     /* the InetAddress field on socket */
        // 662     jobject socketAddressObj;
        InetAddress socketAddressObj;
        // 663
        // 664     /* the ServerSocket fd int field on fdObj */
        // 665     jint fd;
        int fd;
        // 666
        // 667     /* accepted fd */
        // 668     jint newfd;
        /* Initialized for the use in the finally-clause. */
        int newfd = -1;
        // 669
        // 670     SOCKADDR him;
        Socket.sockaddr him = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
        // 671     int len;
        CIntPointer len_Pointer = StackValue.get(CIntPointer.class);
        // 672
        // 673     len = SOCKADDR_LEN;
        len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
        // 674
        // 675     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 676         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            // 677                         "Socket closed");
            // 678         return;
            throw new SocketException("Socket closed");
        } else {
            // 680         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 682     if (IS_NULL(socket)) {
        if (socket == null) {
            // 683         JNU_ThrowNullPointerException(env, "socket is null");
            // 684         return;
            throw new NullPointerException("socket is null");
        }
        try {
            // 686
            // 687     /*
            // 688      * accept connection but ignore ECONNABORTED indicating that
            // 689      * connection was eagerly accepted by the OS but was reset
            // 690      * before accept() was called.
            // 691      *
            // 692      * If accept timeout in place and timeout is adjusted with
            // 693      * each ECONNABORTED or EWOULDBLOCK to ensure that semantics
            // 694      * of timeout are preserved.
            // 695      */
            // 696     for (;;) {
            for (;;) {
                // 697         int ret;
                int ret;
                // 698
                // 699         /* first usage pick up current time */
                // 700         if (prevTime == 0 && timeout > 0) {
                if (prevTime == 0 && timeout > 0) {
                    // 701             prevTime = JVM_CurrentTimeMillis(env, 0);
                    prevTime = System.currentTimeMillis();
                }
                // 703
                // 704         /* passing a timeout of 0 to poll will return immediately,
                // 705            but in the case of ServerSocket 0 means infinite. */
                // 706         if (timeout <= 0) {
                if (timeout <= 0) {
                    // 707             ret = NET_Timeout(fd, -1);
                    ret = JavaNetNetUtilMD.NET_Timeout(fd, -1);
                } else {
                    // 709             ret = NET_Timeout(fd, timeout);
                    ret = JavaNetNetUtilMD.NET_Timeout(fd, timeout);
                }
                // 711         if (ret == 0) {
                if (ret == 0) {
                    // 712             JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                    // 713                             "Accept timed out");
                    // 714             return;
                    throw new SocketTimeoutException("Accept timed out");
                    // 715         } else if (ret == JVM_IO_ERR) {
                } else if (ret == Target_jvm.JVM_IO_ERR()) {
                    // 716             if (errno == EBADF) {
                    if (Errno.errno() == Errno.EBADF()) {
                        // 717                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
                        throw new SocketException("Socket closed");
                        // 718             } else if (errno == ENOMEM) {
                    } else if (Errno.errno() == Errno.ENOMEM()) {
                        // 719                JNU_ThrowOutOfMemoryError(env, "NET_Timeout native heap allocation failed");
                        throw new OutOfMemoryError("NET_Timeout native heap allocation failed");
                    } else {
                        // 721                NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "Accept failed");
                        throw new SocketException(PosixUtils.lastErrorString("Accept failed"));
                    }
                    // 723             return;
                    // 724         } else if (ret == JVM_IO_INTR) {
                } else if (ret == Target_jvm.JVM_IO_INTR()) {
                    // 725             JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                    // 726                             "operation interrupted");
                    // 727             return;
                    throw new InterruptedIOException("operation interrupted");
                }
                // 729
                // 730         newfd = NET_Accept(fd, (struct sockaddr *)&him, (jint*)&len);
                newfd = JavaNetNetUtilMD.NET_Accept(fd, him, len_Pointer);
                // 731
                // 732         /* connection accepted */
                // 733         if (newfd >= 0) {
                if (newfd >= 0) {
                    // 734             SET_BLOCKING(newfd);
                    Util_java_net_PlainSocketImpl.SET_BLOCKING(newfd);
                    // 735             break;
                    break;
                }
                // 737
                // 738         /* non (ECONNABORTED or EWOULDBLOCK) error */
                // 739         if (!(errno == ECONNABORTED || errno == EWOULDBLOCK)) {
                if (!(Errno.errno() == Errno.ECONNABORTED() || Errno.errno() == Errno.EWOULDBLOCK())) {
                    // 740             break;
                    break;
                }
                // 742
                // 743         /* ECONNABORTED or EWOULDBLOCK error so adjust timeout if there is one. */
                // 744         if (timeout) {
                if (CTypeConversion.toBoolean(timeout)) {
                    // 745             jlong currTime = JVM_CurrentTimeMillis(env, 0);
                    long currTime = System.currentTimeMillis();
                    // 746             timeout -= (currTime - prevTime);
                    timeout -= (currTime - prevTime);
                    // 747
                    // 748             if (timeout <= 0) {
                    if (timeout <= 0) {
                        // 749                 JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                        // 750                                 "Accept timed out");
                        // 751                 return;
                        throw new SocketTimeoutException("Accept timed out");
                    }
                    // 753             prevTime = currTime;
                    prevTime = currTime;
                }
            }
            // 756
            // 757     if (newfd < 0) {
            if (newfd < 0) {
                // 758         if (newfd == -2) {
                if (newfd == -2) {
                    // 759             JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                    // 760                             "operation interrupted");
                    throw new InterruptedIOException("operation interrupted");
                } else {
                    // 762             if (errno == EINVAL) {
                    if (Errno.errno() == Errno.EINVAL()) {
                        // 763                 errno = EBADF;
                        Errno.set_errno(Errno.EBADF());
                    }
                    // 765             if (errno == EBADF) {
                    if (Errno.errno() == Errno.EBADF()) {
                        // 766                 JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
                        throw new SocketException("Socket closed");
                    } else {
                        // 768                 NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "Accept failed");
                        throw new SocketException(PosixUtils.lastErrorString("Accept failed"));
                    }
                }
                // 771         return;
            }
        } finally {
            // 773
            // 774     /*
            // 775      * fill up the remote peer port and address in the new socket structure.
            // 776      */
            // 777     socketAddressObj = NET_SockaddrToInetAddress(env, (struct sockaddr *)&him, &port);
            socketAddressObj = JavaNetNetUtil.NET_SockaddrToInetAddress(him, port_Pointer);
            // 778     if (socketAddressObj == NULL) {
            if (socketAddressObj == null) {
                // 779         /* should be pending exception */
                // 780         close(newfd);
                Unistd.close(newfd);
                // 781         return;
            }
        }
        // 783
        // 784     /*
        // 785      * Populate SocketImpl.fd.fd
        // 786      */
        // 787     socketFdObj = (*env)->GetObjectField(env, socket, psi_fdID);
        socketFdObj = Util_java_net_SocketImpl.from_SocketImpl(socket).fd;
        // 788     (*env)->SetIntField(env, socketFdObj, IO_fd_fdID, newfd);
        PosixUtils.setFD(socketFdObj, newfd);
        // 789
        // 790     (*env)->SetObjectField(env, socket, psi_addressID, socketAddressObj);
        Util_java_net_SocketImpl.from_SocketImpl(socket).address = socketAddressObj;
        // 791     (*env)->SetIntField(env, socket, psi_portID, port);
        Util_java_net_SocketImpl.from_SocketImpl(socket).port = port_Pointer.read();
        /* Not re-using the frame-local "port". */
        // 792     /* also fill up the local port information */
        // 793      port = (*env)->GetIntField(env, this, psi_localportID);
        int thisLocalPort = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).localport;
        // 794     (*env)->SetIntField(env, socket, psi_localportID, port);
        Util_java_net_SocketImpl.from_SocketImpl(socket).localport = thisLocalPort;
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    // jdk/src/solaris/classes/java/net/PlainSocketImpl.java?v=Java_1.8.0_40_b10
    // 102 native void socketShutdown(int howto) throws IOException;
    //
    // jdk/src/solaris/native/java/net/PlainSocketImpl.c?v=Java_1.8.0_40_b10
    // 858 /*
    // 859  * Class:     java_net_PlainSocketImpl
    // 860  * Method:    socketShutdown
    // 861  * Signature: (I)V
    // 862  */
    // 863 JNIEXPORT void JNICALL
    // 864 Java_java_net_PlainSocketImpl_socketShutdown(JNIEnv *env, jobject this,
    // 865                                              jint howto)
    // 866 {
    @Substitute
    void socketShutdown(int howto) throws IOException {
        // 867
        // 868     jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
        FileDescriptor fdObj = Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd;
        // 869     jint fd;
        int fd;
        // 870
        // 871     /*
        // 872      * WARNING: THIS NEEDS LOCKING. ALSO: SHOULD WE CHECK for fd being
        // 873      * -1 already?
        // 874      */
        // 875     if (IS_NULL(fdObj)) {
        if (fdObj == null) {
            // 876         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            // 877                         "socket already closed");
            throw new SocketException("socket already closed");
            // 878         return;
            /* Unreachable! */
        } else {
            // 880         fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
            fd = PosixUtils.getFD(fdObj);
        }
        // 882     JVM_SocketShutdown(fd, howto);
        VmPrimsJVM.JVM_SocketShutdown(fd, howto);
    }
    /* @formatter:on */

    @Substitute
    static void initProto() {
        VMError.unimplemented();
        return;
    }

    /* Do not re-format commented-out code: @formatter:off */
    // 886 /*
    // 887  * Class:     java_net_PlainSocketImpl
    // 888  * Method:    socketSetOption
    // 889  * Signature: (IZLjava/lang/Object;)V
    // 890  */
    // 891 JNIEXPORT void JNICALL
    // 892 Java_java_net_PlainSocketImpl_socketSetOption(JNIEnv *env, jobject this,
    // 893                                               jint cmd, jboolean on,
    // 894                                               jobject value) {
    @Substitute
    void socketSetOption(int cmd, boolean on, Object value) throws SocketException {
        // 895     int fd;
        int fd;
        // 896     int level, optname, optlen;
        CIntPointer level_Pointer = StackValue.get(CIntPointer.class);
        CIntPointer optname_Pointer = StackValue.get(CIntPointer.class);
        int optlen;
        /* Translated as a WordPointer to the larger of the arms. */
        // 897     union {
        // 898         int i;
        // 899         struct linger ling;
        // 900     } optval;
        /* Guess which arm of the union is larger. Trust, but verify. */
        final int sizeof_optval = SizeOf.get(Socket.linger.class);
        VMError.guarantee(SizeOf.get(CIntPointer.class) <= sizeof_optval, "sizeof(int) <= sizeof(union optval)");
        VMError.guarantee(SizeOf.get(Socket.linger.class) <= sizeof_optval, "sizeof(struct linger) <= sizeof(union optval)");
        WordPointer optval_Pointer = StackValue.get(sizeof_optval);
        // 901
        // 902     /*
        // 903      * Check that socket hasn't been closed
        // 904      */
        // 905     fd = getFD(env, this);
        fd = PosixUtils.getFD(Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd);
        // 906     if (fd < 0) {
        if (fd < 0) {
        // 907         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
        // 908                         "Socket closed");
        // 909         return;
            throw new SocketException("Socket closed");
        }
        // 911
        // 912     /*
        // 913      * SO_TIMEOUT is a NOOP on Solaris/Linux
        // 914      */
        // 915     if (cmd == java_net_SocketOptions_SO_TIMEOUT) {
        if (cmd == SocketOptions.SO_TIMEOUT) {
        // 916         return;
            return;
        }
        // 918
        // 919     /*
        // 920      * Map the Java level socket option to the platform specific
        // 921      * level and option name.
        // 922      */
        // 923     if (NET_MapSocketOption(cmd, &level, &optname)) {
        if (CTypeConversion.toBoolean(JavaNetNetUtilMD.NET_MapSocketOption(cmd, level_Pointer, optname_Pointer))) {
        // 924         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
        // 925         return;
            throw new SocketException("Invalid option");
        }
        // 927
        // 928     switch (cmd) {
        switch (cmd) {
            // 929         case java_net_SocketOptions_SO_SNDBUF :
            // 930         case java_net_SocketOptions_SO_RCVBUF :
            // 931         case java_net_SocketOptions_SO_LINGER :
            // 932         case java_net_SocketOptions_IP_TOS :
            case SocketOptions.SO_SNDBUF:
            case SocketOptions.SO_RCVBUF:
            case SocketOptions.SO_LINGER:
            case SocketOptions.IP_TOS:
            {
                /* Accessing values of java.lang.Integer the easy way. */
                Integer valueInteger = (Integer) value;
                // 934                 jclass cls;
                // 935                 jfieldID fid;
                // 936
                // 937                 cls = (*env)->FindClass(env, "java/lang/Integer");
                // 938                 CHECK_NULL(cls);
                // 939                 fid = (*env)->GetFieldID(env, cls, "value", "I");
                // 940                 CHECK_NULL(fid);
                // 941
                // 942                 if (cmd == java_net_SocketOptions_SO_LINGER) {
                if (cmd == SocketOptions.SO_LINGER) {
                    // 943                     if (on) {
                    if (on) {
                        // 944                         optval.ling.l_onoff = 1;
                        ((Socket.linger) optval_Pointer).set_l_onoff(1);
                        // 945                         optval.ling.l_linger = (*env)->GetIntField(env, value, fid);
                        ((Socket.linger) optval_Pointer).set_l_linger(valueInteger.intValue());
                    } else {
                        // 947                         optval.ling.l_onoff = 0;
                        ((Socket.linger) optval_Pointer).set_l_onoff(0);
                        // 948                         optval.ling.l_linger = 0;
                        ((Socket.linger) optval_Pointer).set_l_linger(0);
                    }
                    // 950                     optlen = sizeof(optval.ling);
                    optlen = SizeOf.get(Socket.linger.class);
                    /* Copy to optval. */
                    LibC.memcpy(optval_Pointer, optval_Pointer, WordFactory.unsigned(optlen));
                } else {
                    // 952                     optval.i = (*env)->GetIntField(env, value, fid);
                    ((CIntPointer) optval_Pointer).write(valueInteger.intValue());
                    // 953                     optlen = sizeof(optval.i);
                    optlen = SizeOf.get(CIntPointer.class);
                    /* Copy to optval. */
                    LibC.memcpy(optval_Pointer, optval_Pointer, WordFactory.unsigned(optlen));
                }
                // 955
                // 956                 break;
                break;
            }
            // 958
            // 959         /* Boolean -> int */
            // 960         default :
            default:
                // 961             optval.i = (on ? 1 : 0);
                ((CIntPointer) optval_Pointer).write(on ? 1 : 0);
                // 962             optlen = sizeof(optval.i);
                optlen = SizeOf.get(CIntPointer.class);
                /* Copy to optval. */
                LibC.memcpy(optval_Pointer, optval_Pointer, WordFactory.unsigned(optlen));
                // 963
        }
        // 965
        // 966     if (NET_SetSockOpt(fd, level, optname, (const void *)&optval, optlen) < 0) {
        if (JavaNetNetUtilMD.NET_SetSockOpt(fd, level_Pointer.read(), optname_Pointer.read(), optval_Pointer, optlen) < 0) {
        // 967 #if defined(__solaris__) || defined(_AIX)
        // 968         if (errno == EINVAL) {
            if (Errno.errno() == Errno.EINVAL()) {
        // 969             // On Solaris setsockopt will set errno to EINVAL if the socket
        // 970             // is closed. The default error message is then confusing
        // 971             char fullMsg[128];
        // 972             jio_snprintf(fullMsg, sizeof(fullMsg), "Invalid option or socket reset by remote peer");
        // 973             JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", fullMsg);
        // 974             return;
                throw new SocketException("Invalid option or socket reset by remote peer");
            }
        // 976 #endif /* __solaris__ */
        // 977         NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
        // 978                                       "Error setting socket option");
            throw new SocketException(PosixUtils.lastErrorString("Error setting socket option"));
        }
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    @Substitute
    /* Translated from src/solaris/native/java/net/PlainSocketImpl.c?v=Java_1.8.0_40_b10. */
    // 982 /*
    // 983  * Class:     java_net_PlainSocketImpl
    // 984  * Method:    socketGetOption
    // 985  * Signature: (I)I
    // 986  */
    // 987 JNIEXPORT jint JNICALL
    // 988 Java_java_net_PlainSocketImpl_socketGetOption(JNIEnv *env, jobject this,
    // 989                                               jint cmd, jobject iaContainerObj) {
    int socketGetOption(int cmd, Object iaContainerObj) throws SocketException {
        // 990
        // 991     int fd;
        int fd;
        // 992     int level, optname, optlen;
        CIntPointer level_Pointer = StackValue.get(CIntPointer.class);
        CIntPointer optname_Pointer = StackValue.get(CIntPointer.class);
        CIntPointer optlen_Pointer = StackValue.get(CIntPointer.class);
        /* Translated as a WordPointer to the larger of the arms. */
        // 993     union {
        // 994         int i;
        // 995         struct linger ling;
        // 996     } optval;
        /* Guess which arm of the union is larger. Trust, but verify. */
        final int sizeof_optval = SizeOf.get(Socket.linger.class);
        VMError.guarantee(SizeOf.get(CIntPointer.class) <= sizeof_optval, "sizeof(int) <= sizeof(union optval)");
        VMError.guarantee(SizeOf.get(Socket.linger.class) <= sizeof_optval, "sizeof(struct linger) <= sizeof(union optval)");
        WordPointer optval_Pointer = StackValue.get(sizeof_optval);
        // 997
        // 998     /*
        // 999      * Check that socket hasn't been closed
        // 1000      */
        // 1001     fd = getFD(env, this);
        fd = PosixUtils.getFD(Util_java_net_PlainSocketImpl.as_Target_java_net_SocketImpl(this).fd);
        // 1002     if (fd < 0) {
        if (fd < 0) {
            // 1003         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            // 1004                         "Socket closed");
            // 1005         return -1;
            throw new SocketException("Socket closed");
        }
        // 1007
        // 1008     /*
        // 1009      * SO_BINDADDR isn't a socket option
        // 1010      */
        // 1011     if (cmd == java_net_SocketOptions_SO_BINDADDR) {
        if (cmd == SocketOptions.SO_BINDADDR) {
            // 1012         SOCKADDR him;
            Socket.sockaddr him = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 1013         socklen_t len = 0;
            CIntPointer len_Pointer = StackValue.get(CIntPointer.class);
            // 1014         int port;
            CIntPointer port_Pointer = StackValue.get(CIntPointer.class);
            // 1015         jobject iaObj;
            InetAddress iaObj;
            // 1016         jclass iaCntrClass;
            // 1017         jfieldID iaFieldID;
            // 1018
            // 1019         len = SOCKADDR_LEN;
            len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 1020
            // 1021         if (getsockname(fd, (struct sockaddr *)&him, &len) < 0) {
            if (Target_os.get_sock_name(fd, him, len_Pointer) < 0) {
                // 1022             NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                // 1023                              "Error getting socket name");
                // 1024             return -1;
                throw new SocketException(PosixUtils.lastErrorString("Error getting socket name"));
            }
            // 1026         iaObj = NET_SockaddrToInetAddress(env, (struct sockaddr *)&him, &port);
            iaObj = JavaNetNetUtil.NET_SockaddrToInetAddress(him, port_Pointer);
            // 1027         CHECK_NULL_RETURN(iaObj, -1);
            if (iaObj == null) {
                return -1;
            }
            // 1028
            // 1029         iaCntrClass = (*env)->GetObjectClass(env, iaContainerObj);
            // 1030         iaFieldID = (*env)->GetFieldID(env, iaCntrClass, "addr", "Ljava/net/InetAddress;");
            // 1031         CHECK_NULL_RETURN(iaFieldID, -1);
            // 1032         (*env)->SetObjectField(env, iaContainerObj, iaFieldID, iaObj);
            // 1033         return 0; /* notice change from before */
            /*
             * The code above works for any instance that has an `InetAddress addr` field.
             * The code below assumes that `iaContainerObj` is an `InetAddressContainer`.
             */
            if (Util_java_net_InetAddressContainer.setAddr(iaContainerObj, iaObj)) {
                return 0;
            } else {
                return -1;
            }
        }
        // 1035
        // 1036     /*
        // 1037      * Map the Java level socket option to the platform specific
        // 1038      * level and option name.
        // 1039      */
        // 1040     if (NET_MapSocketOption(cmd, &level, &optname)) {
        if (CTypeConversion.toBoolean(JavaNetNetUtilMD.NET_MapSocketOption(cmd, level_Pointer, optname_Pointer))) {
            // 1041         JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
            // 1042         return -1;
            throw new SocketException("Invalid option");
        }
        // 1044
        // 1045     /*
        // 1046      * Args are int except for SO_LINGER
        // 1047      */
        // 1048     if (cmd == java_net_SocketOptions_SO_LINGER) {
        if (cmd == SocketOptions.SO_LINGER) {
            // 1049         optlen = sizeof(optval.ling);
            optlen_Pointer.write(SizeOf.get(Socket.linger.class));
        } else {
            // 1051         optlen = sizeof(optval.i);
            optlen_Pointer.write(SizeOf.get(CIntPointer.class));
        }
        // 1053
        // 1054     if (NET_GetSockOpt(fd, level, optname, (void *)&optval, &optlen) < 0) {
        if (JavaNetNetUtilMD.NET_GetSockOpt(fd, level_Pointer.read(), optname_Pointer.read(), optval_Pointer, optlen_Pointer) < 0) {
            // 1055         NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
            // 1056                                       "Error getting socket option");
            // 1057         return -1;
            throw new SocketException(PosixUtils.lastErrorString("Error getting socket option"));
        }
        // 1059
        // 1060     switch (cmd) {
        switch (cmd) {
            // 1061         case java_net_SocketOptions_SO_LINGER:
            case SocketOptions.SO_LINGER: {
                // 1062             return (optval.ling.l_onoff ? optval.ling.l_linger: -1);
                if (CTypeConversion.toBoolean(((Socket.linger) optval_Pointer).l_onoff()))  {
                    return ((Socket.linger) optval_Pointer).l_linger();
                } else {
                    return -1;
                }
            }
                // 1063
                // 1064         case java_net_SocketOptions_SO_SNDBUF:
            case SocketOptions.SO_SNDBUF:
                // 1065         case java_net_SocketOptions_SO_RCVBUF:
            case SocketOptions.SO_RCVBUF:
                // 1066         case java_net_SocketOptions_IP_TOS:
            case SocketOptions.IP_TOS: {
                // 1067             return optval.i;
                return ((CIntPointer) optval_Pointer).read();
            }
            // 1068
            // 1069         default :
            default:  {
                // 1070             return (optval.i == 0) ? -1 : 1;
                if (((CIntPointer) optval_Pointer).read() == 0) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }
    }
    /* @formatter:on */

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    void socketSendUrgentData(int data) throws IOException {
        VMError.unimplemented();
        return;
    }
}

final class Util_java_net_PlainSocketImpl {

    // 075 /*
    // 076 * file descriptor used for dup2
    // 077 */
    // 078 static int marker_fd = -1;
    static int marker_fd = -1;

    /** Private constructor: No instances. */
    private Util_java_net_PlainSocketImpl() {
    }

    /* Mimic the Java class hierarchy. */

    static Target_java_net_SocketImpl as_Target_java_net_SocketImpl(Target_java_net_PlainSocketImpl tjnsi) {
        return KnownIntrinsics.unsafeCast(tjnsi, Target_java_net_SocketImpl.class);
    }

    static Target_java_net_AbstractPlainSocketImpl as_Target_java_net_AbstractPlainSocketImpl(Target_java_net_PlainSocketImpl tjnsi) {
        return KnownIntrinsics.unsafeCast(tjnsi, Target_java_net_AbstractPlainSocketImpl.class);
    }

    /* Do not re-format commented-out code: @formatter:off */
    /* I think this was a macro because it needs a block with local variables. */
    // 081 #define SET_NONBLOCKING(fd) { \
    static void SET_NONBLOCKING(int fd) {
        // 082         int flags = fcntl(fd, F_GETFL); \
        int flags = Fcntl.fcntl(fd, Fcntl.F_GETFL());
        // 083         flags |= O_NONBLOCK;            \
        flags |= Fcntl.O_NONBLOCK();
        // 084         fcntl(fd, F_SETFL, flags);      \
        Fcntl.fcntl(fd, Fcntl.F_SETFL(), flags);
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    /* I think this was a macro because it needs a block with local variables. */
    // 087 #define SET_BLOCKING(fd) {              \
    static void SET_BLOCKING(int fd) {
        // 088         int flags = fcntl(fd, F_GETFL); \
        int flags = Fcntl.fcntl(fd, Fcntl.F_GETFL());
        // 089         flags &= ~O_NONBLOCK;           \
        flags &= ~Fcntl.O_NONBLOCK();
        // 090         fcntl(fd, F_SETFL, flags);      \
        Fcntl.fcntl(fd, Fcntl.F_SETFL(), flags);
    }
    /* @formatter:on */
}

@TargetClass(sun.net.spi.DefaultProxySelector.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_net_spi_DefaultProxySelector {

    /** Private constructor: No instances. */
    private Target_sun_net_spi_DefaultProxySelector() {
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @SuppressWarnings({"static-method", "unused"})
    /* FIXME: No proxies, yet. */
    private synchronized /* native */ Proxy getSystemProxy(String protocol, String host) {
        VMError.unsupportedFeature("Target_sun_net_spi_DefaultProxySelector.getSystemProxy");
        return null;
    }

    @Substitute
    @TargetElement(onlyWith = JDK9OrLater.class)
    @SuppressWarnings({"static-method", "unused"})
    /* FIXME: No proxies, yet. */
    private synchronized /* native */ Proxy[] getSystemProxies(String protocol, String host) {
        VMError.unsupportedFeature("Target_sun_net_spi_DefaultProxySelector.getSystemProxies");
        return null;
    }
}

@TargetClass(sun.net.sdp.SdpSupport.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_net_sdp_SdpSupport {

    /** Private constructor: No instances. */
    private Target_sun_net_sdp_SdpSupport() {
    }

    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    // 094 /**
    // 095  * Converts an existing file descriptor, that references an unbound TCP socket,
    // 096  * to SDP.
    // 097  */
    // 098 JNIEXPORT void JNICALL
    // 099 Java_sun_net_sdp_SdpSupport_convert0(JNIEnv *env, jclass cls, int fd)
    // 100 {
    @Substitute
    static void convert0(int fd) throws IOException {
        // 101     int s = create(env);
        int s = Util_sun_net_sdp_SdpSupport.create();
        // 102     if (s >= 0) {
        if (s >= 0) {
            // 103         socklen_t len;
            CIntPointer len_Pointer = StackValue.get(CIntPointer.class);
            // 104         int arg, res;
            CIntPointer arg_Pointer = StackValue.get(CIntPointer.class);
            int res;
            // 105         struct linger linger;
            Socket.linger linger = StackValue.get(Socket.linger.class);
            // 107         /* copy socket options that are relevant to SDP */
            // 108         len = sizeof(arg);
            len_Pointer.write(SizeOf.get(CIntPointer.class));
            // 109         if (getsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg, &len) == 0)
            if (Socket.getsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_REUSEADDR(), arg_Pointer, len_Pointer) == 0) {
                // 110             setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (char*)&arg, len);
                Socket.setsockopt(s, Socket.SOL_SOCKET(), Socket.SO_REUSEADDR(), arg_Pointer, len_Pointer.read());
            }
            // 111         len = sizeof(arg);
            len_Pointer.write(SizeOf.get(CIntPointer.class));
            // 112         if (getsockopt(fd, SOL_SOCKET, SO_OOBINLINE, (char*)&arg, &len) == 0)
            if (Socket.getsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_OOBINLINE(), arg_Pointer, len_Pointer) == 0) {
                // 113             setsockopt(s, SOL_SOCKET, SO_OOBINLINE, (char*)&arg, len);
                Socket.setsockopt(s, Socket.SOL_SOCKET(), Socket.SO_OOBINLINE(), arg_Pointer, len_Pointer.read());
            }
            // 114         len = sizeof(linger);
            len_Pointer.write(SizeOf.get(Socket.linger.class));
            // 115         if (getsockopt(fd, SOL_SOCKET, SO_LINGER, (void*)&linger, &len) == 0)
            if (Socket.getsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_LINGER(), linger, len_Pointer) == 0) {
                // 116             setsockopt(s, SOL_SOCKET, SO_LINGER, (char*)&linger, len);
                Socket.setsockopt(s, Socket.SOL_SOCKET(), Socket.SO_LINGER(), linger, len_Pointer.read());
            }
            // 118         RESTARTABLE(dup2(s, fd), res);
            do {
                res = Unistd.dup2(s, fd);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
            // 119         if (res < 0)
            if (res < 0 ) {
                /* FIXME: Not implementing JNU_ThrowIOExceptionWithLastError. */
                // 120             JNU_ThrowIOExceptionWithLastError(env, "dup2");
                throw new IOException("dup2");
            }
            // 121         RESTARTABLE(close(s), res);
            do {
                res = Unistd.close(fd);
            } while ((res == -1) && (Errno.errno() == Errno.EINTR()));
        }
    }
}
final class Util_sun_net_sdp_SdpSupport {

    // 030 #if defined(__solaris__)
    // 031 #if !defined(PROTO_SDP)
    // 032 #define PROTO_SDP 257
    // 033 #endif
    // 034 #elif defined(__linux__)
    // 035 #if !defined(AF_INET_SDP)
    // 036 #define AF_INET_SDP 27
    // 037 #endif
    // 038 #endif
    @Platforms({Platform.LINUX.class})
    static int AF_INET_SDP() {
        return 27;
    }

    // 044 #define RESTARTABLE(_cmd, _result) do { \
    // 045 do { \
    // 046 _result = _cmd; \
    // 047 } while((_result == -1) && (errno == EINTR)); \
    // 048 } while(0)

    /* Do not re-format commented-out code: @formatter:off */
    // 051 /**
    // 052  * Creates a SDP socket.
    // 053  */
    // 054 static int create(JNIEnv* env)
    // 055 {
    static int create() throws IOException {
        // 056     int s;
        int s;
        // 058 #if defined(__solaris__)
        // 059   #ifdef AF_INET6
        // 060     int domain = ipv6_available() ? AF_INET6 : AF_INET;
        // 061   #else
        // 062     int domain = AF_INET;
        // 063   #endif
        // 064     s = socket(domain, SOCK_STREAM, PROTO_SDP);
        // 065 #elif defined(__linux__)
        if (IsDefined.__linux__()) {
            // 066     /**
            // 067      * IPv6 not supported by SDP on Linux
            // 068      */
            // 069     if (ipv6_available()) {
            if (JavaNetNetUtil.ipv6_available()) {
                // 070         JNU_ThrowIOException(env, "IPv6 not supported");
                throw new IOException("IPv6 not supported");
                // 071         return -1;
            }

            // 073     s = socket(AF_INET_SDP, SOCK_STREAM, 0);
            s = Socket.socket(AF_INET_SDP(), Socket.SOCK_STREAM(), 0);
        } else {
            // 074 #else
            // 075     /* not supported on other platforms at this time */
            // 076     s = -1;
            s = -1;
            // 077     errno = EPROTONOSUPPORT;
            Errno.set_errno(Errno.EPROTONOSUPPORT());
        }
        // 078 #endif
        // 080     if (s < 0)
        if (s < 0) {
            /* FIXME: Not implementing JNU_ThrowIOExceptionWithLastError. */
            // 081         JNU_ThrowIOExceptionWithLastError(env, "socket");
            throw new IOException("socket");
        }
        // 082     return s;
        return s;
    }
    /* @formatter:on */
}

/** Things I need from jdk/src/share/javavm/export/jvm.h. */
class Target_jvm {

    /* Private constructor: No instances. */
    private Target_jvm() {
    }

    /* Do not re-format commented-out code: @formatter:off */
    // 1094 /* Note that the JVM IO functions are expected to return JVM_IO_ERR
    // 1095  * when there is any kind of error. The caller can then use the
    // 1096  * platform specific support (e.g., errno) to get the detailed
    // 1097  * error info.  The JVM_GetLastErrorString procedure may also be used
    // 1098  * to obtain a descriptive error string.
    // 1099  */
    // 1100 #define JVM_IO_ERR  (-1)
    static int JVM_IO_ERR() {
        return -1;
    }
    /* @formatter:on */

    /* Do not re-format commented-out code: @formatter:off */
    // 1102 /* For interruptible IO. Returning JVM_IO_INTR indicates that an IO
    // 1103  * operation has been disrupted by Thread.interrupt. There are a
    // 1104  * number of technical difficulties related to interruptible IO that
    // 1105  * need to be solved. For example, most existing programs do not handle
    // 1106  * InterruptedIOExceptions specially, they simply treat those as any
    // 1107  * IOExceptions, which typically indicate fatal errors.
    // 1108  *
    // 1109  * There are also two modes of operation for interruptible IO. In the
    // 1110  * resumption mode, an interrupted IO operation is guaranteed not to
    // 1111  * have any side-effects, and can be restarted. In the termination mode,
    // 1112  * an interrupted IO operation corrupts the underlying IO stream, so
    // 1113  * that the only reasonable operation on an interrupted stream is to
    // 1114  * close that stream. The resumption mode seems to be impossible to
    // 1115  * implement on Win32 and Solaris. Implementing the termination mode is
    // 1116  * easier, but it's not clear that's the right semantics.
    // 1117  *
    // 1118  * Interruptible IO is not supported on Win32.It can be enabled/disabled
    // 1119  * using a compile-time flag on Solaris. Third-party JVM ports do not
    // 1120  * need to implement interruptible IO.
    // 1121  */
    // 1122 #define JVM_IO_INTR (-2)
    static int JVM_IO_INTR() {
        return -2;
    }
    /* @formatter:on */
}

/** Things I need from jdk/src/share/javavm/export/jni.h. */
class Target_jni {

    /* Private constructor: No instances. */
    private Target_jni() {
    }

    // 153 #define JNI_FALSE 0
    static int JNI_FALSE() {
        return 0;
    }

    // 154 #define JNI_TRUE 1
    static int JNI_TRUE() {
        return 1;
    }
}

class Util_jni {

    /* Private constructor: No instances. */
    private Util_jni() {
    }

    /* Convenience methods. */
    static boolean JNI_FALSE() {
        return CTypeConversion.toBoolean(Target_jni.JNI_FALSE());
    }

    static boolean JNI_TRUE() {
        return CTypeConversion.toBoolean(Target_jni.JNI_TRUE());
    }

    static int booleanToJNI(boolean value) {
        return (value ? Target_jni.JNI_TRUE() : Target_jni.JNI_FALSE());
    }
}

// Checkstyle: resume
