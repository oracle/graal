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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketOptions;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Ioctl;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.NetinetTcp;
import com.oracle.svm.core.posix.headers.Poll;
import com.oracle.svm.core.posix.headers.Poll.pollfd;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Sysctl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.darwin.DarwinSysctl;
import com.oracle.svm.core.util.VMError;

//Allow methods with non-standard names: Checkstyle: stop

// TODO: This should be in some other package (svm.core.jdk?)
// but then it can not use the non-public classes in this package.
/** Native methods from jdk/src/share/native/java/net/net_util.c translated to Java. */
class JavaNetNetUtil {

    /* Private constructor: No instances. */
    private JavaNetNetUtil() {
    }

    /* Do not re-format commented-out code: @formatter:off */
    // 040 JNIEXPORT jint JNICALL
    // 041 JNI_OnLoad(JavaVM *vm, void *reserved) {
    static {
        /*
         * This will be evaluated during native image generation, because there are no system properties
         * at runtime.
         */
        // 043     JNIEnv *env;
        // 044     jclass iCls;
        // 045     jmethodID mid;
        // 046     jstring s;
        // 047     jint preferIPv4Stack;
        // 048
        // 049     if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_2) == JNI_OK) {
        // 050         if (JVM_InitializeSocketLibrary() < 0) {
        // 051             JNU_ThrowByName(env, "java/lang/UnsatisfiedLinkError",
        // 052                             "failed to initialize net library.");
        // 053             return JNI_VERSION_1_2;
        // 054         }
        // 055     }
        // 056     iCls = (*env)->FindClass(env, "java/lang/Boolean");
        // 057     CHECK_NULL_RETURN(iCls, JNI_VERSION_1_2);
        // 058     mid = (*env)->GetStaticMethodID(env, iCls, "getBoolean", "(Ljava/lang/String;)Z");
        // 059     CHECK_NULL_RETURN(mid, JNI_VERSION_1_2);
        // 060     s = (*env)->NewStringUTF(env, "java.net.preferIPv4Stack");
        // 061     CHECK_NULL_RETURN(s, JNI_VERSION_1_2);
        // 062     preferIPv4Stack = (*env)->CallStaticBooleanMethod(env, iCls, mid, s);
        boolean preferIPv4Stack = Boolean.getBoolean("java.net.preferIPv4Stack");
        // 064     /*
        // 065        Since we have initialized and loaded the Socket library we will
        // 066        check now to whether we have IPv6 on this platform and if the
        // 067        supporting socket APIs are available
        // 068     */
        // 069     IPv6_available = IPv6_supported() & (!preferIPv4Stack);
        IPv6_available = JavaNetNetUtilMD.IPv6_supported() & (!preferIPv4Stack);
        // 070     platformInit();
        JavaNetNetUtilMD.platformInit();
        // 071     parseExclusiveBindProperty(env);
        JavaNetNetUtilMD.parseExclusiveBindProperty();
        // 073 return JNI_VERSION_1_2;
    }
    /* @formatter:on */

    /* Initialization. */

    // 076 static int initialized = 0;
    static int initialized = 0;

    // 078 static void initInetAddrs(JNIEnv *env) {
    static void initInetAddrs() {
        // 079 if (!initialized) {
        if (!CTypeConversion.toBoolean(initialized)) {
            // 080 Java_java_net_InetAddress_init(env, 0);
            Util_java_net_InetAddress.Java_java_net_InetAddress_init();
            // 081 Java_java_net_Inet4Address_init(env, 0);
            Util_java_net_Inet4Address.Java_java_net_Inet4Address_init();
            // 082 Java_java_net_Inet6Address_init(env, 0);
            Util_java_net_Inet6Address.Java_java_net_Inet6Address_init();
            // 083 initialized = 1;
            initialized = 1;
            // 084 }
        }
    }

    // 033 static int IPv6_available;
    static boolean IPv6_available;

    // 035 JNIEXPORT jint JNICALL ipv6_available()
    static boolean ipv6_available() {
        // 037 return IPv6_available ;
        return IPv6_available;
    }

    // 226 JNIEXPORT jobject JNICALL
    // 227 NET_SockaddrToInetAddress(JNIEnv *env, struct sockaddr *him, int *port) {
    @SuppressWarnings({"unused"})
    static InetAddress NET_SockaddrToInetAddress(Socket.sockaddr him, CIntPointer port) {
        // 228 jobject iaObj;
        InetAddress iaObj;
        // 231 if (him->sa_family == AF_INET6) {
        if (him.sa_family() == Socket.AF_INET6()) {
            // 232 jbyteArray ipaddress;
            byte[] ipaddress;
            // 233 #ifdef WIN32
            // 234 struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
            // 235 #else
            // 236 struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
            // 237 #endif
            NetinetIn.sockaddr_in6 him6 = (NetinetIn.sockaddr_in6) him;
            // 238 jbyte *caddr = (jbyte *)&(him6->sin6_addr);
            CCharPointer caddr = (CCharPointer) him6.sin6_addr();
            // 239 if (NET_IsIPv4Mapped(caddr)) {
            if (JavaNetNetUtil.isIPv4Mapped(caddr)) {
                // 240 int address;
                int address;
                // 249 iaObj = (*env)->NewObject(env, inet4Cls, ia4_ctrID);
                iaObj = Util_java_net_Inet4Address.new_Inet4Address();
                // 250 CHECK_NULL_RETURN(iaObj, NULL);
                if (iaObj == null) {
                    return null;
                }
                // 251 address = NET_IPv4MappedToIPv4(caddr);
                address = JavaNetNetUtilMD.NET_IPv4MappedToIPv4(caddr);
                // 252 setInetAddress_addr(env, iaObj, address);
                JavaNetNetUtil.setInetAddress_addr(iaObj, address);
                // 253 setInetAddress_family(env, iaObj, IPv4);
                JavaNetNetUtil.setInetAddress_family(iaObj, Target_java_net_InetAddress.IPv4);
            } else {
                // 256 jint scope;
                int scope;
                // 257 int ret;
                int ret;
                // 265 iaObj = (*env)->NewObject(env, inet6Cls, ia6_ctrID);
                iaObj = Util_java_net_Inet6Address.new_Inet6Address();
                // 266 CHECK_NULL_RETURN(iaObj, NULL);
                if (iaObj == null) {
                    return null;
                }
                // 267 ret = setInet6Address_ipaddress(env, iaObj, (char *)&(him6->sin6_addr));
                ret = JavaNetNetUtil.setInet6Address_ipaddress((Inet6Address) iaObj, him6.sin6_addr().s6_addr());
                // 268 CHECK_NULL_RETURN(ret, NULL);
                if (ret == 0) {
                    return null;
                }
                // 269 setInetAddress_family(env, iaObj, IPv6);
                JavaNetNetUtil.setInetAddress_family(iaObj, Target_java_net_InetAddress.IPv6);
                // 270 scope = getScopeID(him);
                scope = JavaNetNetUtilMD.getScopeID(him);
                // 271 setInet6Address_scopeid(env, iaObj, scope);
                JavaNetNetUtil.setInet6Address_scopeid((Inet6Address) iaObj, scope);
            }
            // 273 *port = ntohs(him6->sin6_port);
            port.write(NetinetIn.ntohs(him6.sin6_port()));
        } else {
            // 277 struct sockaddr_in *him4 = (struct sockaddr_in *)him;
            NetinetIn.sockaddr_in him4 = (NetinetIn.sockaddr_in) him;
            // 287 iaObj = (*env)->NewObject(env, inet4Cls, ia4_ctrID);
            iaObj = Util_java_net_Inet4Address.new_Inet4Address();
            // 288 CHECK_NULL_RETURN(iaObj, NULL);
            if (iaObj == null) {
                return null;
            }
            // 289 setInetAddress_family(env, iaObj, IPv4);
            JavaNetNetUtil.setInetAddress_family(iaObj, Target_java_net_InetAddress.IPv4);
            // 290 setInetAddress_addr(env, iaObj, ntohl(him4->sin_addr.s_addr));
            JavaNetNetUtil.setInetAddress_addr(iaObj, NetinetIn.ntohl(him4.sin_addr().s_addr()));
            // 291 *port = ntohs(him4->sin_port);
            port.write(NetinetIn.ntohs(him4.sin_port()));
        }
        // 293 return iaObj;
        return iaObj;
    }

    // 255 JNIEXPORT jint JNICALL
    // 256 NET_SockaddrEqualsInetAddress(JNIEnv *env, struct sockaddr *him, jobject iaObj)
    static boolean NET_SockaddrEqualsInetAddress(Socket.sockaddr him, InetAddress iaObj) {
        // 258 jint family = AF_INET;
        int family = Socket.AF_INET();

        /* Restructured due to #ifdef mixing with either an else-branch or just a curlied block */

        // 260 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 261 family = getInetAddress_family(env, iaObj) == IPv4? AF_INET : AF_INET6;
            family = getInetAddress_family(iaObj) == Target_java_net_InetAddress.IPv4 ? Socket.AF_INET() : Socket.AF_INET6();
            // 262 JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
            // 263 if (him->sa_family == AF_INET6) {
        }

        /* This only occurs if AF_INET6 above && him is IPv6 */
        if (him.sa_family() == Socket.AF_INET6()) {
            // 264 #ifdef WIN32
            // 265 struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
            // 266 #else
            // 267 struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
            NetinetIn.sockaddr_in6 him6 = (NetinetIn.sockaddr_in6) him;
            // 268 #endif
            // 269 jbyte *caddrNew = (jbyte *)&(him6->sin6_addr);
            CCharPointer caddrNew = him6.sin6_addr().s6_addr();
            // 270 if (NET_IsIPv4Mapped(caddrNew)) {
            if (isIPv4Mapped(caddrNew)) {
                // 271 int addrNew;
                int addrNew;
                // 272 int addrCur;
                int addrCur;
                // 273 if (family == AF_INET6) {
                if (family == Socket.AF_INET6()) {
                    // 274 return JNI_FALSE;
                    return false;
                }
                // 276 addrNew = NET_IPv4MappedToIPv4(caddrNew);
                addrNew = JavaNetNetUtilMD.NET_IPv4MappedToIPv4(caddrNew);
                // 277 addrCur = getInetAddress_addr(env, iaObj);
                addrCur = JavaNetNetUtilMD.getInetAddress_addr(iaObj);
                // 278 JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
                // 279 if (addrNew == addrCur) {
                if (addrNew == addrCur) {
                    // 280 return JNI_TRUE;
                    return true;
                } else {
                    // 282 return JNI_FALSE;
                    return false;
                }
                // 284 } else {
            } else {
                // 285 jbyteArray ipaddress;
                /* Unused. */
                // 286 jbyte caddrCur[16];
                CCharPointer caddrCur = StackValue.get(16, CCharPointer.class);
                // 287 int scope;
                int scope;
                // 289 if (family == AF_INET) {
                if (family == Socket.AF_INET()) {
                    // 290 return JNI_FALSE;
                    return false;
                }
                // 292 scope = getInet6Address_scopeid(env, iaObj);
                scope = getInet6Address_scopeid((Inet6Address) iaObj);
                // 293 getInet6Address_ipaddress(env, iaObj, (char *)caddrCur);
                getInet6Address_ipAddress((Inet6Address) iaObj, caddrCur);
                // 294 if (NET_IsEqual(caddrNew, caddrCur) && cmpScopeID(scope, him)) {
                if (JavaNetNetUtilMD.NET_IsEqual(caddrCur, caddrCur) && JavaNetNetUtilMD.cmpScopeID(scope, him)) {
                    // 295 return JNI_TRUE;
                    return true;
                } else {
                    // 297 return JNI_FALSE;
                    return false;
                }
            }
            // 301 #endif /* AF_INET6 */
        }

        // 303 struct sockaddr_in *him4 = (struct sockaddr_in *)him;
        NetinetIn.sockaddr_in him4 = (NetinetIn.sockaddr_in) him;
        // 304 int addrNew, addrCur;
        int addrNew, addrCur;

        // 305 if (family != AF_INET) {
        if (family != Socket.AF_INET()) {
            // 306 return JNI_FALSE;
            return false;
        }

        // 308 addrNew = ntohl(him4->sin_addr.s_addr);
        addrNew = NetinetIn.ntohl(him4.sin_addr().s_addr());
        // 309 addrCur = getInetAddress_addr(env, iaObj);
        addrCur = JavaNetNetUtilMD.getInetAddress_addr(iaObj);
        // 310 JNU_CHECK_EXCEPTION_RETURN(env, JNI_FALSE);
        // 311 if (addrNew == addrCur) {
        if (addrNew == addrCur) {
            // 312 return JNI_TRUE;
            return true;
        } else {
            // 314 return JNI_FALSE;
            return false;
        }
    }

    static boolean isIPv4Mapped(CCharPointer caddr) {
        int i;
        for (i = 0; i < 10; i++) {
            if (caddr.read(i) != 0x00) {
                return false;
            }
        }
        if (((caddr.read(10) & 0xff) == 0xff) && ((caddr.read(11) & 0xff) == 0xff)) {
            return true;
        }
        return false;
    }

    // 102 jobject getInet6Address_scopeifname(JNIEnv *env, jobject iaObj) {
    static NetworkInterface getInet6Address_scopeifname(Inet6Address iaObj) {
        // 103 jobject holder;
        Target_java_net_Inet6Address_Inet6AddressHolder holder;
        // 104
        // 105 initInetAddrs(env);
        initInetAddrs();
        // 106 holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
        holder = Util_java_net_Inet6Address.from_Inet6Address(iaObj).holder6;
        // 107 CHECK_NULL_RETURN(holder, NULL);
        if (holder == null) {
            return null;
        }
        // 108 return (*env)->GetObjectField(env, holder, ia6_scopeifnameID);
        return holder.scope_ifname;
    }

    // 111 int setInet6Address_scopeifname(JNIEnv *env, jobject iaObj, jobject scopeifname) {
    static int setInet6Address_scopeifname(Inet6Address iaObj, NetworkInterface scopeifname) {
        // 112 jobject holder;
        Target_java_net_Inet6Address_Inet6AddressHolder holder;
        // 113
        // 114 initInetAddrs(env);
        initInetAddrs();
        // 115 holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
        holder = Util_java_net_Inet6Address.from_Inet6Address(iaObj).holder6;
        // 116 CHECK_NULL_RETURN(holder, JNI_FALSE);
        if (holder == null) {
            return -1;
        }
        // 117 (*env)->SetObjectField(env, holder, ia6_scopeifnameID, scopeifname);
        holder.scope_ifname = scopeifname;
        // 118 return JNI_TRUE;
        return Target_jni.JNI_TRUE();
    }

    // 130 int getInet6Address_scopeid(JNIEnv *env, jobject iaObj) {
    static int getInet6Address_scopeid(Inet6Address iaObj) {
        // 131 jobject holder;
        Target_java_net_Inet6Address_Inet6AddressHolder holder;
        // 133 initInetAddrs(env);
        initInetAddrs();
        // 134 holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
        holder = Util_java_net_Inet6Address.from_Inet6Address(iaObj).holder6;
        // 135 CHECK_NULL_RETURN(holder, -1);
        if (holder == null) {
            return -1;
        }
        // 136 return (*env)->GetIntField(env, holder, ia6_scopeidID);
        return holder.scope_id;
    }

    // 139 int setInet6Address_scopeid(JNIEnv *env, jobject iaObj, int scopeid) {
    static int setInet6Address_scopeid(Inet6Address iaObj, int scopeid) {
        // 140 jobject holder;
        Target_java_net_Inet6Address_Inet6AddressHolder holder;
        // 142 initInetAddrs(env);
        initInetAddrs();
        // 143 holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
        holder = Util_java_net_Inet6Address.from_Inet6Address(iaObj).holder6;
        // 144 CHECK_NULL_RETURN(holder, JNI_FALSE);
        if (holder == null) {
            return Target_jni.JNI_FALSE();
        }
        // 145 (*env)->SetIntField(env, holder, ia6_scopeidID, scopeid);
        holder.scope_id = scopeid;
        // 146 if (scopeid > 0) {
        if (scopeid > 0) {
            // 147 (*env)->SetBooleanField(env, holder, ia6_scopeidsetID, JNI_TRUE);
            holder.scope_id_set = Util_jni.JNI_TRUE();
        }
        // 149 return JNI_TRUE;
        return Target_jni.JNI_TRUE();
    }

    // 153 int getInet6Address_ipaddress(JNIEnv *env, jobject iaObj, char *dest) {
    static int getInet6Address_ipAddress(Inet6Address iaObj, CCharPointer dest) {
        // 154 jobject holder, addr;
        Target_java_net_Inet6Address_Inet6AddressHolder holder;
        byte[] addr;
        // 155 jbyteArray barr;
        // 157 initInetAddrs(env);
        initInetAddrs();
        // 158 holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
        holder = Util_java_net_Inet6Address.from_Inet6Address(iaObj).holder6;
        // 159 CHECK_NULL_RETURN(holder, JNI_FALSE);
        if (holder == null) {
            return Target_jni.JNI_FALSE();
        }
        // 160 addr = (*env)->GetObjectField(env, holder, ia6_ipaddressID);
        addr = holder.ipaddress;
        // 161 CHECK_NULL_RETURN(addr, JNI_FALSE);
        if (addr == null) {
            return Target_jni.JNI_FALSE();
        }
        // 162 (*env)->GetByteArrayRegion(env, addr, 0, 16, (jbyte *)dest);
        VmPrimsJNI.GetByteArrayRegion(addr, 0, 16, dest);
        // 163 return JNI_TRUE;
        return Target_jni.JNI_TRUE();
    }

    // 166 int setInet6Address_ipaddress(JNIEnv *env, jobject iaObj, char *address) {
    static int setInet6Address_ipaddress(Inet6Address iaObj, CCharPointer address) {
        // 167 jobject holder;
        Target_java_net_Inet6Address_Inet6AddressHolder holder;
        // 168 jbyteArray addr;
        byte[] addr;
        // 170 initInetAddrs(env);
        initInetAddrs();
        // 171 holder = (*env)->GetObjectField(env, iaObj, ia6_holder6ID);
        holder = Util_java_net_Inet6Address.from_Inet6Address(iaObj).holder6;
        // 172 CHECK_NULL_RETURN(holder, JNI_FALSE);
        if (holder == null) {
            return Target_jni.JNI_FALSE();
        }
        // 173 addr = (jbyteArray)(*env)->GetObjectField(env, holder, ia6_ipaddressID);
        addr = holder.ipaddress;
        // 174 if (addr == NULL) {
        if (addr == null) {
            // 175 addr = (*env)->NewByteArray(env, 16);
            addr = new byte[16];
            // 177 (*env)->SetObjectField(env, holder, ia6_ipaddressID, addr);
            holder.ipaddress = addr;
        }
        // 179 (*env)->SetByteArrayRegion(env, addr, 0, 16, (jbyte *)address);
        VmPrimsJNI.SetByteArrayRegion(addr, 0, 16, address);
        // 180 return JNI_TRUE;
        return Target_jni.JNI_TRUE();
    }

    // 183 void setInetAddress_addr(JNIEnv *env, jobject iaObj, int address) {
    static void setInetAddress_addr(InetAddress iaObj, int address) {
        // 184 jobject holder;
        Target_java_net_InetAddress_InetAddressHolder holder;
        // 185 initInetAddrs(env);
        initInetAddrs();
        // 186 holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
        holder = Util_java_net_InetAddress.from_InetAddress(iaObj).holder;
        // 187 (*env)->SetIntField(env, holder, iac_addressID, address);
        holder.address = address;
    }

    // 190 void setInetAddress_family(JNIEnv *env, jobject iaObj, int family) {
    static void setInetAddress_family(InetAddress iaObj, int family) {
        // 191 jobject holder;
        Target_java_net_InetAddress_InetAddressHolder holder;
        // 192 initInetAddrs(env);
        initInetAddrs();
        // 193 holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
        holder = Util_java_net_InetAddress.from_InetAddress(iaObj).holder;
        // 194 (*env)->SetIntField(env, holder, iac_familyID, family);
        holder.family = family;
    }

    // 197 void setInetAddress_hostName(JNIEnv *env, jobject iaObj, jobject host) {
    static void setInetAddress_hostName(InetAddress iaObj, String host) {
        // 198 jobject holder;
        Target_java_net_InetAddress_InetAddressHolder holder;
        // 199 initInetAddrs(env);
        initInetAddrs();
        // 200 holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
        holder = Util_java_net_InetAddress.from_InetAddress(iaObj).holder;
        // 201 (*env)->SetObjectField(env, holder, iac_hostNameID, host);
        holder.hostName = host;
    }

    // 211 int getInetAddress_family(JNIEnv *env, jobject iaObj) {
    static int getInetAddress_family(InetAddress iaObj) {
        // 212 jobject holder;
        Target_java_net_InetAddress_InetAddressHolder holder;
        // 214 initInetAddrs(env);
        initInetAddrs();
        // 215 holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
        holder = Util_java_net_InetAddress.from_InetAddress(iaObj).holder;
        // 216 return (*env)->GetIntField(env, holder, iac_familyID);
        return holder.family;
    }

    @Fold
    static int MAX_PACKET_LEN() {
        // from {jdk8}/share/native/java/net/net_util.h
        // 37 #define MAX_PACKET_LEN 65536
        return 65536;
    }
}

/** Native methods from jdk/src/solaris/native/java/net/net_util_md.c translated to Java. */
class JavaNetNetUtilMD {

    /* Private constructor: No instances. */
    private JavaNetNetUtilMD() {
    }

    static void platformInit() {
        /*
         * FIXME: Not doing any of the platform initialization for Linux.
         *
         * See jdk/src/solaris/native/java/net/net_util_md.c, lines 473 - 743.
         */
    }

    /* Do not re-format commented-out code: @formatter:off */
    // 760 void parseExclusiveBindProperty(JNIEnv *env) {
    static void parseExclusiveBindProperty() {
        // 761 #ifdef __solaris__
        // 762     jstring s, flagSet;
        // 763     jclass iCls;
        // 764     jmethodID mid;
        // 765
        // 766     s = (*env)->NewStringUTF(env, "sun.net.useExclusiveBind");
        // 767     CHECK_NULL(s);
        // 768     iCls = (*env)->FindClass(env, "java/lang/System");
        // 769     CHECK_NULL(iCls);
        // 770     mid = (*env)->GetStaticMethodID(env, iCls, "getProperty",
        // 771                 "(Ljava/lang/String;)Ljava/lang/String;");
        // 772     CHECK_NULL(mid);
        // 773     flagSet = (*env)->CallStaticObjectMethod(env, iCls, mid, s);
        // 774     if (flagSet != NULL) {
        // 775         useExclBind = 1;
        // 776     }
        // 777 #endif
    }
    /* @formatter:on */

    /* Do not format commented-out code: @formatter:off */
    // 954 int
    // 955 NET_IPv4MappedToIPv4(jbyte* caddr) {
    static int NET_IPv4MappedToIPv4(CCharPointer caddr) {
        // 956 return ((caddr[12] & 0xff) << 24) | ((caddr[13] & 0xff) << 16) | ((caddr[14] & 0xff) << 8)
        // 957       | (caddr[15] & 0xff);
        return ((caddr.read(12) & 0xff) << 24) | ((caddr.read(13) & 0xff) << 16) | ((caddr.read(14) & 0xff) << 8) | (caddr.read(15) & 0xff);
    }
    /* @formatter:on */

    // 963 int
    // 964 NET_IsEqual(jbyte* caddr1, jbyte* caddr2) {
    static boolean NET_IsEqual(CCharPointer caddr1, CCharPointer caddr2) {
        // 965 int i;
        int i;
        // 966 for (i = 0; i < 16; i++) {
        for (i = 0; i < 16; i++) {
            // 967 if (caddr1[i] != caddr2[i]) {
            if (caddr1.read(i) != caddr2.read(i)) {
                // 968 return 0; /* false */
                return false;
            }
        }
        // 971 return 1;
        return true;
    }

    // 251 int cmpScopeID (unsigned int scope, struct sockaddr *him) {
    static boolean cmpScopeID(int scope, Socket.sockaddr him) {
        // 252 struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
        NetinetIn.sockaddr_in6 him6 = (NetinetIn.sockaddr_in6) him;
        // 253 return him6->sin6_scope_id == scope;
        return him6.sin6_scope_id() == scope;
    }

    // 237 int getScopeID (struct sockaddr *him) {
    static int getScopeID(Socket.sockaddr him) {
        // 238 struct sockaddr_in6 *hext = (struct sockaddr_in6 *)him;
        NetinetIn.sockaddr_in6 hext = (NetinetIn.sockaddr_in6) him;
        // 239 return hext->sin6_scope_id;
        return hext.sin6_scope_id();
    }

    // 204 int getInetAddress_addr(JNIEnv *env, jobject iaObj) {
    static int getInetAddress_addr(InetAddress iaObj) {
        // 205 jobject holder;
        Target_java_net_InetAddress_InetAddressHolder holder;
        // 206 initInetAddrs(env);
        JavaNetNetUtil.initInetAddrs();
        // 207 holder = (*env)->GetObjectField(env, iaObj, ia_holderID);
        holder = Util_java_net_InetAddress.from_InetAddress(iaObj).holder;
        // 208 return (*env)->GetIntField(env, holder, iac_addressID);
        return holder.address;
    }

    static boolean IPv6_supported() {
        // TODO: Lines 305-426 of platform-dependent code elided,
        // because it looks like IPv6 is supported by Linux, MacOSX, and Solaris.
        // TODO: I am also not implementing building HotSpot with -DDONT_ENABLE_IPV6.
        return true;
    }

    // 275 void
    // 276 NET_ThrowNew(JNIEnv *env, int errorNumber, char *msg) {
    static void NET_ThrowNew(int errorNumber, String msgArg) throws SocketException, InterruptedException {
        /* Do not modify argument! */
        String msg = msgArg;
        // 277 char fullMsg[512];
        // 278 if (!msg) {
        if (msg == null) {
            // 279 msg = "no further information";
            msg = "no further information";
        }
        /* Translation of a switch on errno values. */
        // 281 switch(errorNumber) {
        if (errorNumber == Errno.EBADF()) {
            // 282 case EBADF:
            // 283 jio_snprintf(fullMsg, sizeof(fullMsg), "socket closed: %s", msg);
            // 284 JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", fullMsg);
            // 285 break;
            throw new SocketException("socket closed: " + msg);
        } else if (errorNumber == Errno.EINTR()) {
            // 286 case EINTR:
            // 287 JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException", msg);
            // 288 break;
            throw new InterruptedException(msg);
        } else {
            // 290 errno = errorNumber;
            Errno.set_errno(errorNumber);
            // 291 JNU_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", msg);
            throw new SocketException(PosixUtils.lastErrorString(msg));
        }
    }

    /* Do not re-wrap comments: @formatter:off */
    // 780 /* In the case of an IPv4 Inetaddress this method will return an
    // 781 * IPv4 mapped address where IPv6 is available and v4MappedAddress is TRUE.
    // 782 * Otherwise it will return a sockaddr_in structure for an IPv4 InetAddress.
    // 783 */
    // 784 JNIEXPORT int JNICALL
    // 785 NET_InetAddressToSockaddr(JNIEnv *env, jobject iaObj, int port, struct sockaddr *him,
    // 786 int *len, jboolean v4MappedAddress) {
    static int NET_InetAddressToSockaddr(InetAddress iaObj, int port, Socket.sockaddr him, CIntPointer len, boolean v4MappedAddress) throws SocketException {
        // 787 jint family;
        int family;
        // 788 family = getInetAddress_family(env, iaObj);
        family = JavaNetNetUtil.getInetAddress_family(iaObj);
        // 789 #ifdef AF_INET6
        // 790 /* needs work. 1. family 2. clean up him6 etc deallocate memory */
        // 791 if (ipv6_available() && !(family == IPv4 && v4MappedAddress == JNI_FALSE)) {
        if (JavaNetNetUtil.ipv6_available() &&
                        (!((family == Target_java_net_InetAddress.IPv4) && (v4MappedAddress == Util_jni.JNI_FALSE())))) {
            // 792 struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
            NetinetIn.sockaddr_in6 him6 = (NetinetIn.sockaddr_in6) him;
            // 793 jbyte caddr[16];
            CCharPointer caddr = StackValue.get(16, CCharPointer.class);
            // 794 jint address;
            int address;
            // 797 if (family == IPv4) { /* will convert to IPv4-mapped address */
            if (family == Target_java_net_InetAddress.IPv4) {
                // 798 memset((char *) caddr, 0, 16);
                LibC.memset(caddr, WordFactory.zero(), WordFactory.unsigned(16));
                // 799 address = getInetAddress_addr(env, iaObj);
                address = getInetAddress_addr(iaObj);
                // 800 if (address == INADDR_ANY) {
                if (address == NetinetIn.INADDR_ANY()) {
                    /* we would always prefer IPv6 wildcard address
                     * 802 caddr[10] = 0xff;
                     * 803 caddr[11] = 0xff;
                     */
                } else {
                    // 805 caddr[10] = 0xff;
                    caddr.write(10, (byte) 0xff);
                    // 806 caddr[11] = 0xff;
                    caddr.write(11, (byte) 0xff);
                    // 807 caddr[12] = ((address >> 24) & 0xff);
                    caddr.write(12, (byte) ((address >> 24) & 0xff));
                    // 808 caddr[13] = ((address >> 16) & 0xff);
                    caddr.write(13, (byte) ((address >> 16) & 0xff));
                    // 809 caddr[14] = ((address >> 8) & 0xff);
                    caddr.write(14, (byte) ((address >> 8) & 0xff));
                    // 810 caddr[15] = (address & 0xff);
                    caddr.write(15, (byte) (address & 0xff));
                }
            } else {
                // 813 getInet6Address_ipaddress(env, iaObj, (char *)caddr);
                JavaNetNetUtil.getInet6Address_ipAddress((Inet6Address) iaObj, caddr);
            }
            // 815 memset((char *)him6, 0, sizeof(struct sockaddr_in6));
            LibC.memset(him6,  WordFactory.zero(), SizeOf.unsigned(NetinetIn.sockaddr_in6.class));
            // 816 him6->sin6_port = htons(port);
            him6.set_sin6_port(NetinetIn.htons(port));
            // 817 memcpy((void *)&(him6->sin6_addr), caddr, sizeof(struct in6_addr) );
            LibC.memcpy(him6.sin6_addr(), caddr, SizeOf.unsigned(NetinetIn.in6_addr.class));
            // 818 him6->sin6_family = AF_INET6;
            him6.set_sin6_family(Socket.AF_INET6());
            // 819 *len = sizeof(struct sockaddr_in6) ;
            len.write(SizeOf.get(NetinetIn.sockaddr_in6.class));
            // 821 #if defined(_ALLBSD_SOURCE) && defined(_AF_INET6)
            if (IsDefined._ALLBSD_SOURCE() && IsDefined.socket_AF_INET6()) {
                // 822 // XXXBSD: should we do something with scope id here ? see below linux comment
                // 823 /* MMM: Come back to this! */
            }
            // 824 #endif
            // 825
            /* 826 /*
             * 827 * On Linux if we are connecting to a link-local address
             * 828 * we need to specify the interface in the scope_id (2.4 kernel only)
             * 829 *
             * 830 * If the scope was cached the we use the cached value. If not cached but
             * 831 * specified in the Inet6Address we use that, but we first check if the
             * 832 * address needs to be routed via the loopback interface. In this case,
             * 833 * we override the specified value with that of the loopback interface.
             * 834 * If no cached value exists and no value was specified by user, then
             * 835 * we try to determine a value from the routing table. In all these
             * 836 * cases the used value is cached for further use.
             * 837 */
            // 838 #ifdef __linux__
            if (IsDefined.__linux__()) {
                // 839 if (IN6_IS_ADDR_LINKLOCAL(&(him6->sin6_addr))) {
                if (JavaNetNetUtilMD.IN6_IS_ADDR_LINKLOCAL(him6.sin6_addr())) {
                    // 840 int cached_scope_id = 0, scope_id = 0;
                    int cached_scope_id = 0;
                    int scope_id = 0;
                    /* I am assuming that the field "Inet6Address.cached_scope_id" always exists. */
                    final boolean ia6_cachedscopeidID = true;
                    // 842 if (ia6_cachedscopeidID) {
                    if (ia6_cachedscopeidID) {
                        // 843     cached_scope_id = (int)(*env)->GetIntField(env, iaObj, ia6_cachedscopeidID);
                        cached_scope_id = Util_java_net_Inet6Address.from_Inet6Address((Inet6Address) iaObj).cached_scope_id;
                        // 844     /* if cached value exists then use it. Otherwise, check
                        // 845      * if scope is set in the address.
                        // 846      */
                        // 847 if (!cached_scope_id) {
                        if (!CTypeConversion.toBoolean(cached_scope_id)) {
                            /* I am assuming that the field "Inet6Address.scope_id" exists. */
                            final boolean ia6_scopeidID = true;
                            // 848 if (ia6_scopeidID) {
                            if (ia6_scopeidID) {
                                // 849     scope_id = getInet6Address_scopeid(env, iaObj);
                                scope_id = JavaNetNetUtil.getInet6Address_scopeid((Inet6Address) iaObj);
                            }
                            // 851 if (scope_id != 0) {
                            if (scope_id != 0) {
                                // 852 /* check user-specified value for loopback case
                                // 853  * that needs to be overridden
                                // 854  */
                                // 855 if (kernelIsV24() && needsLoopbackRoute (&him6->sin6_addr)) {
                                if (JavaNetNetUtilMD.kernelIsV24() && JavaNetNetUtilMD.needsLoopbackRoute(him6.sin6_addr())) {
                                    // 856 cached_scope_id = lo_scope_id;
                                    cached_scope_id = lo_scope_id;
                                    // 857 (*env)->SetIntField(env, iaObj, ia6_cachedscopeidID, cached_scope_id);
                                    Util_java_net_Inet6Address.from_Inet6Address((Inet6Address) iaObj).cached_scope_id = cached_scope_id;
                                }
                            } else {
                                // 860 /*
                                // 861  * Otherwise consult the IPv6 routing tables to
                                // 862  * try determine the appropriate interface.
                                // 863  */
                                // 864 if (kernelIsV24()) {
                                if (JavaNetNetUtilMD.kernelIsV24()) {
                                    // 865 cached_scope_id = getDefaultIPv6Interface( &(him6->sin6_addr) );
                                    cached_scope_id = JavaNetNetUtilMD.getDefaultIPv6Interface(him6.sin6_addr());
                                } else {
                                    // 867 cached_scope_id = getLocalScopeID( (char *)&(him6->sin6_addr) );
                                    cached_scope_id = JavaNetNetUtilMD.getLocalScopeID(him6.sin6_addr());
                                    // 868 if (cached_scope_id == 0) {
                                    if (cached_scope_id == 0) {
                                        // 869 cached_scope_id = getDefaultIPv6Interface( &(him6->sin6_addr) );
                                        cached_scope_id = getDefaultIPv6Interface(him6.sin6_addr());
                                    }
                                }
                                // 872 (*env)->SetIntField(env, iaObj, ia6_cachedscopeidID, cached_scope_id);
                                Util_java_net_Inet6Address.from_Inet6Address((Inet6Address) iaObj).cached_scope_id = cached_scope_id;
                            }
                        }
                    }
                    // 877 /*
                    // 878  * If we have a scope_id use the extended form
                    // 879  * of sockaddr_in6.
                    // 880  */
                    // 882 struct sockaddr_in6 *him6 =
                    // 883         (struct sockaddr_in6 *)him;
                    NetinetIn.sockaddr_in6 him6_l882 = (NetinetIn.sockaddr_in6) him;
                    // 884 him6->sin6_scope_id = cached_scope_id != 0 ?
                    // 885 cached_scope_id : scope_id;
                    him6_l882.set_sin6_scope_id((cached_scope_id != 0) ? cached_scope_id : scope_id);
                    // 886 *len = sizeof(struct sockaddr_in6);
                    len.write(SizeOf.get(NetinetIn.sockaddr_in6.class));
                }
                // 888 #else
            } else {
                // 889 /* handle scope_id for solaris */
                // 891 if (family != IPv4) {
                if (family != Target_java_net_InetAddress.IPv4) {
                    /* I am assuming that the field "Inet6Address.scopeid" exists. */
                    final boolean ia6_scopeidID = true;
                    // 892 if (ia6_scopeidID) {
                    if (ia6_scopeidID) {
                        // 893 him6->sin6_scope_id = getInet6Address_scopeid(env, iaObj);
                        him6.set_sin6_scope_id(JavaNetNetUtil.getInet6Address_scopeid((Inet6Address) iaObj));
                    }
                }
            }
            // 896 #endif /* __linux__ */
        }
            // 898 #endif /* AF_INET6 */
        else {
            // 900 struct sockaddr_in *him4 = (struct sockaddr_in*)him;
            NetinetIn.sockaddr_in him4 = (NetinetIn.sockaddr_in) him;
            // 901 jint address;
            int address;
            // 902 if (family == IPv6) {
            if (family == Target_java_net_InetAddress.IPv6) {
                // 903 JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Protocol family unavailable");
                throw new SocketException("Protocol family unavailable");
                // 904 return -1;
            }
            // 906 memset((char *) him4, 0, sizeof(struct sockaddr_in));
            LibC.memset(him4, WordFactory.zero(), SizeOf.unsigned(NetinetIn.sockaddr_in.class));
            // 907 address = getInetAddress_addr(env, iaObj);
            address = getInetAddress_addr(iaObj);
            // 908 him4->sin_port = htons((short) port);
            him4.set_sin_port(NetinetIn.htons((short) port));
            // 909 him4->sin_addr.s_addr = (uint32_t) htonl(address);
            him4.sin_addr().set_s_addr(NetinetIn.htonl(address));
            // 910 him4->sin_family = AF_INET;
            him4.set_sin_family(Socket.AF_INET());
            // 911 *len = sizeof(struct sockaddr_in);
            len.write(SizeOf.get(NetinetIn.sockaddr_in.class));
        }
        // 913 return 0;
        return 0;
    }
    /* @formatter:on */

    /* Do not re-wrap comments.  @formatter:off */
    // 149 WS2TCPIP_INLINE int
    // 150 IN6_IS_ADDR_LINKLOCAL(const struct in6_addr *a) {
    static boolean IN6_IS_ADDR_LINKLOCAL(NetinetIn.in6_addr a) {
        // 152 return (a->s6_bytes[0] == 0xfe
        // 153         && a->s6_bytes[1] == 0x80);
        return ((a.s6_addr().read(0) == (byte) 0xfe) && (a.s6_addr().read(1) == (byte) 0x80));
    }
    /* @formatter:on */

    @Platforms(Platform.LINUX.class)//
    /* TODO: This will be evaluated during native image generation. */
    // 219 static int vinit = 0;
    static int vinit = 0;

    @Platforms(Platform.LINUX.class)//
    /* TODO: This will be evaluated during native image generation. */
    // 220 static int kernelV24 = 0;
    static int kernelV24 = 0;

    @Platforms(Platform.LINUX.class)//
    /* TODO: This will be evaluated during native image generation. */
    // 221 static int vinit24 = 0;
    static int vinit24 = 0;

    /* Do not re-wrap comments.  @formatter:off */
    @Platforms(Platform.LINUX.class)
    // 223 int kernelIsV24 () {
    static boolean kernelIsV24() {
        // 224 if (!vinit24) {
        if (!CTypeConversion.toBoolean(vinit24)) {
            /* FIXME: I have not implemented sys/utsname.h::uname, so I can not run on a Linux 2.4 kernel. */
            // 225 struct utsname sysinfo;
            // 226 if (uname(&sysinfo) == 0) {
            // 227     sysinfo.release[3] = '\0';
            // 228     if (strcmp(sysinfo.release, "2.4") == 0) {
            // 229         kernelV24 = JNI_TRUE;
            // 230     }
            // 231 }
            // 232 vinit24 = 1;
        }
        vinit24 = 1;
        // 234 return kernelV24;
        return CTypeConversion.toBoolean(kernelV24);
    }
    /* @formatter:on */

    @Platforms(Platform.LINUX.class)//
    /* TODO: This will be evaluated during native image generation. */
    // 492 static int lo_scope_id = 0;
    static int lo_scope_id = 0;

    /* Do not re-wrap comments.  @formatter:off */
    @Platforms(Platform.LINUX.class)
    // 504 static jboolean needsLoopbackRoute (struct in6_addr* dest_addr) {
    static boolean needsLoopbackRoute(@SuppressWarnings("unused") NetinetIn.in6_addr dest_addr) {
        VMError.unimplemented();
            // 505     int byte_count;
            // 506     int extra_bits, i;
            // 507     struct loopback_route *ptr;
            // 508
            // 509     if (loRoutes == 0) {
            // 510         initLoopbackRoutes();
            // 511     }
            // 512
            // 513     for (ptr = loRoutes, i=0; i<nRoutes; i++, ptr++) {
            // 514         struct in6_addr *target_addr=&ptr->addr;
            // 515         int dest_plen = ptr->plen;
            // 516         byte_count = dest_plen >> 3;
            // 517         extra_bits = dest_plen & 0x3;
            // 518
            // 519         if (byte_count > 0) {
            // 520             if (memcmp(target_addr, dest_addr, byte_count)) {
            // 521                 continue;  /* no match */
            // 522             }
            // 523         }
            // 524
            // 525         if (extra_bits > 0) {
            // 526             unsigned char c1 = ((unsigned char *)target_addr)[byte_count];
            // 527             unsigned char c2 = ((unsigned char *)&dest_addr)[byte_count];
            // 528             unsigned char mask = 0xff << (8 - extra_bits);
            // 529             if ((c1 & mask) != (c2 & mask)) {
            // 530                 continue;
            // 531             }
            // 532         }
            // 533         return JNI_TRUE;
            // 534     }
            // 535     return JNI_FALSE;
        return Util_jni.JNI_FALSE();
    }
    /* @formatter:on */

    /* Do not re-wrap comments.  @formatter:off */
    @Platforms(Platform.LINUX.class)
    // 539 static void initLoopbackRoutes() {
    static void initLoopbackRoutes() {
        VMError.unimplemented();
            // 540     FILE *f;
            // 541     char srcp[8][5];
            // 542     char hopp[8][5];
            // 543     int dest_plen, src_plen, use, refcnt, metric;
            // 544     unsigned long flags;
            // 545     char dest_str[40];
            // 546     struct in6_addr dest_addr;
            // 547     char device[16];
            // 548     struct loopback_route *loRoutesTemp;
            // 549
            // 550     if (loRoutes != 0) {
            // 551         free (loRoutes);
            // 552     }
            // 553     loRoutes = calloc (loRoutes_size, sizeof(struct loopback_route));
            // 554     if (loRoutes == 0) {
            // 555         return;
            // 556     }
            // 557     /*
            // 558      * Scan /proc/net/ipv6_route looking for a matching
            // 559      * route.
            // 560      */
            // 561     if ((f = fopen("/proc/net/ipv6_route", "r")) == NULL) {
            // 562         return ;
            // 563     }
            // 564     while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
            // 565                      "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
            // 566                      "%4s%4s%4s%4s%4s%4s%4s%4s "
            // 567                      "%08x %08x %08x %08lx %8s",
            // 568                      dest_str, &dest_str[5], &dest_str[10], &dest_str[15],
            // 569                      &dest_str[20], &dest_str[25], &dest_str[30], &dest_str[35],
            // 570                      &dest_plen,
            // 571                      srcp[0], srcp[1], srcp[2], srcp[3],
            // 572                      srcp[4], srcp[5], srcp[6], srcp[7],
            // 573                      &src_plen,
            // 574                      hopp[0], hopp[1], hopp[2], hopp[3],
            // 575                      hopp[4], hopp[5], hopp[6], hopp[7],
            // 576                      &metric, &use, &refcnt, &flags, device) == 31) {
            // 577
            // 578         /*
            // 579          * Some routes should be ignored
            // 580          */
            // 581         if ( (dest_plen < 0 || dest_plen > 128)  ||
            // 582              (src_plen != 0) ||
            // 583              (flags & (RTF_POLICY | RTF_FLOW)) ||
            // 584              ((flags & RTF_REJECT) && dest_plen == 0) ) {
            // 585             continue;
            // 586         }
            // 587
            // 588         /*
            // 589          * Convert the destination address
            // 590          */
            // 591         dest_str[4] = ':';
            // 592         dest_str[9] = ':';
            // 593         dest_str[14] = ':';
            // 594         dest_str[19] = ':';
            // 595         dest_str[24] = ':';
            // 596         dest_str[29] = ':';
            // 597         dest_str[34] = ':';
            // 598         dest_str[39] = '\0';
            // 599
            // 600         if (inet_pton(AF_INET6, dest_str, &dest_addr) < 0) {
            // 601             /* not an Ipv6 address */
            // 602             continue;
            // 603         }
            // 604         if (strcmp(device, "lo") != 0) {
            // 605             /* Not a loopback route */
            // 606             continue;
            // 607         } else {
            // 608             if (nRoutes == loRoutes_size) {
            // 609                 loRoutesTemp = realloc (loRoutes, loRoutes_size *
            // 610                                         sizeof (struct loopback_route) * 2);
            // 611
            // 612                 if (loRoutesTemp == 0) {
            // 613                     free(loRoutes);
            // 614                     fclose (f);
            // 615                     return;
            // 616                 }
            // 617                 loRoutes=loRoutesTemp;
            // 618                 loRoutes_size *= 2;
            // 619             }
            // 620             memcpy (&loRoutes[nRoutes].addr,&dest_addr,sizeof(struct in6_addr));
            // 621             loRoutes[nRoutes].plen = dest_plen;
            // 622             nRoutes ++;
            // 623         }
            // 624     }
            // 625
            // 626     fclose (f);
            // 627     {
            // 628         /* now find the scope_id for "lo" */
            // 629
            // 630         char devname[21];
            // 631         char addr6p[8][5];
            // 632         int plen, scope, dad_status, if_idx;
            // 633
            // 634         if ((f = fopen("/proc/net/if_inet6", "r")) != NULL) {
            // 635             while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s\n",
            // 636                       addr6p[0], addr6p[1], addr6p[2], addr6p[3],
            // 637                       addr6p[4], addr6p[5], addr6p[6], addr6p[7],
            // 638                   &if_idx, &plen, &scope, &dad_status, devname) == 13) {
            // 639
            // 640                 if (strcmp(devname, "lo") == 0) {
            // 641                     /*
            // 642                      * Found - so just return the index
            // 643                      */
            // 644                     fclose(f);
            // 645                     lo_scope_id = if_idx;
            // 646                     return;
            // 647                 }
            // 648             }
            // 649             fclose(f);
            // 650         }
        return;
    }
    /* @formatter:on */

    /* Do not re-wrap comments.  @formatter:off */
    @Platforms(Platform.LINUX.class)
    // 721 /* return the scope_id (interface index) of the
    // 722  * interface corresponding to the given address
    // 723  * returns 0 if no match found
    // 724  */
    // 725
    // 726 static int getLocalScopeID (char *addr) {
    @SuppressWarnings({"unused"})
    static int getLocalScopeID(NetinetIn.in6_addr addr) {
        VMError.unimplemented();
            // 727     struct localinterface *lif;
            // 728     int i;
            // 729     if (localifs == 0) {
            // 730         initLocalIfs();
            // 731     }
            // 732     for (i=0, lif=localifs; i<nifs; i++, lif++) {
            // 733         if (memcmp (addr, lif->localaddr, 16) == 0) {
            // 734             return lif->index;
            // 735         }
            // 736     }
            // 737     return 0;
        return 0;
    }
    /* @formatter:on */

    /* Do not re-wrap comments.  @formatter:off */
    @Platforms(Platform.LINUX.class)
    // 1043 /*
    // 1044  * Determine the default interface for an IPv6 address.
    // 1045  *
    // 1046  * 1. Scans /proc/net/ipv6_route for a matching route
    // 1047  *    (eg: fe80::/10 or a route for the specific address).
    // 1048  *    This will tell us the interface to use (eg: "eth0").
    // 1049  *
    // 1050  * 2. Lookup /proc/net/if_inet6 to map the interface
    // 1051  *    name to an interface index.
    // 1052  *
    // 1053  * Returns :-
    // 1054  *      -1 if error
    // 1055  *       0 if no matching interface
    // 1056  *      >1 interface index to use for the link-local address.
    // 1057  */
    // 1058 #if defined(__linux__) && defined(AF_INET6)
    // 1059 int getDefaultIPv6Interface(struct in6_addr *target_addr) {
    @SuppressWarnings({"unused"})
    static int getDefaultIPv6Interface(NetinetIn.in6_addr target_addr) {
        VMError.unimplemented();
            // 1060     FILE *f;
            // 1061     char srcp[8][5];
            // 1062     char hopp[8][5];
            // 1063     int dest_plen, src_plen, use, refcnt, metric;
            // 1064     unsigned long flags;
            // 1065     char dest_str[40];
            // 1066     struct in6_addr dest_addr;
            // 1067     char device[16];
            // 1068     jboolean match = JNI_FALSE;
            // 1069
            // 1070     /*
            // 1071      * Scan /proc/net/ipv6_route looking for a matching
            // 1072      * route.
            // 1073      */
            // 1074     if ((f = fopen("/proc/net/ipv6_route", "r")) == NULL) {
            // 1075         return -1;
            // 1076     }
            // 1077     while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
            // 1078                      "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
            // 1079                      "%4s%4s%4s%4s%4s%4s%4s%4s "
            // 1080                      "%08x %08x %08x %08lx %8s",
            // 1081                      dest_str, &dest_str[5], &dest_str[10], &dest_str[15],
            // 1082                      &dest_str[20], &dest_str[25], &dest_str[30], &dest_str[35],
            // 1083                      &dest_plen,
            // 1084                      srcp[0], srcp[1], srcp[2], srcp[3],
            // 1085                      srcp[4], srcp[5], srcp[6], srcp[7],
            // 1086                      &src_plen,
            // 1087                      hopp[0], hopp[1], hopp[2], hopp[3],
            // 1088                      hopp[4], hopp[5], hopp[6], hopp[7],
            // 1089                      &metric, &use, &refcnt, &flags, device) == 31) {
            // 1090
            // 1091         /*
            // 1092          * Some routes should be ignored
            // 1093          */
            // 1094         if ( (dest_plen < 0 || dest_plen > 128)  ||
            // 1095              (src_plen != 0) ||
            // 1096              (flags & (RTF_POLICY | RTF_FLOW)) ||
            // 1097              ((flags & RTF_REJECT) && dest_plen == 0) ) {
            // 1098             continue;
            // 1099         }
            // 1100
            // 1101         /*
            // 1102          * Convert the destination address
            // 1103          */
            // 1104         dest_str[4] = ':';
            // 1105         dest_str[9] = ':';
            // 1106         dest_str[14] = ':';
            // 1107         dest_str[19] = ':';
            // 1108         dest_str[24] = ':';
            // 1109         dest_str[29] = ':';
            // 1110         dest_str[34] = ':';
            // 1111         dest_str[39] = '\0';
            // 1112
            // 1113         if (inet_pton(AF_INET6, dest_str, &dest_addr) < 0) {
            // 1114             /* not an Ipv6 address */
            // 1115             continue;
            // 1116         } else {
            // 1117             /*
            // 1118              * The prefix len (dest_plen) indicates the number of bits we
            // 1119              * need to match on.
            // 1120              *
            // 1121              * dest_plen / 8    => number of bytes to match
            // 1122              * dest_plen % 8    => number of additional bits to match
            // 1123              *
            // 1124              * eg: fe80::/10 => match 1 byte + 2 additional bits in the
            // 1125              *                  the next byte.
            // 1126              */
            // 1127             int byte_count = dest_plen >> 3;
            // 1128             int extra_bits = dest_plen & 0x3;
            // 1129
            // 1130             if (byte_count > 0) {
            // 1131                 if (memcmp(target_addr, &dest_addr, byte_count)) {
            // 1132                     continue;  /* no match */
            // 1133                 }
            // 1134             }
            // 1135
            // 1136             if (extra_bits > 0) {
            // 1137                 unsigned char c1 = ((unsigned char *)target_addr)[byte_count];
            // 1138                 unsigned char c2 = ((unsigned char *)&dest_addr)[byte_count];
            // 1139                 unsigned char mask = 0xff << (8 - extra_bits);
            // 1140                 if ((c1 & mask) != (c2 & mask)) {
            // 1141                     continue;
            // 1142                 }
            // 1143             }
            // 1144
            // 1145             /*
            // 1146              * We have a match
            // 1147              */
            // 1148             match = JNI_TRUE;
            // 1149             break;
            // 1150         }
            // 1151     }
            // 1152     fclose(f);
            // 1153
            // 1154     /*
            // 1155      * If there's a match then we lookup the interface
            // 1156      * index.
            // 1157      */
            // 1158     if (match) {
            // 1159         char devname[21];
            // 1160         char addr6p[8][5];
            // 1161         int plen, scope, dad_status, if_idx;
            // 1162
            // 1163         if ((f = fopen("/proc/net/if_inet6", "r")) != NULL) {
            // 1164             while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s\n",
            // 1165                       addr6p[0], addr6p[1], addr6p[2], addr6p[3],
            // 1166                       addr6p[4], addr6p[5], addr6p[6], addr6p[7],
            // 1167                   &if_idx, &plen, &scope, &dad_status, devname) == 13) {
            // 1168
            // 1169                 if (strcmp(devname, device) == 0) {
            // 1170                     /*
            // 1171                      * Found - so just return the index
            // 1172                      */
            // 1173                     fclose(f);
            // 1174                     return if_idx;
            // 1175                 }
            // 1176             }
            // 1177             fclose(f);
            // 1178         } else {
            // 1179             /*
            // 1180              * Couldn't open /proc/net/if_inet6
            // 1181              */
            // 1182             return -1;
            // 1183         }
            // 1184     }
            // 1185
            // 1186     /*
            // 1187      * If we get here it means we didn't there wasn't any
            // 1188      * route or we couldn't get the index of the interface.
            // 1189      */
            // 1190     return 0;
        return 0;
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 088 void setDefaultScopeID(JNIEnv *env, struct sockaddr *him)
    static void setDefaultScopeID(Socket.sockaddr him) {
        // 090 #ifdef MACOSX
        if (Platform.includedIn(Platform.DARWIN.class)) {
            // 091     static jclass ni_class = NULL;
            // 092     static jfieldID ni_defaultIndexID;
            // 093     if (ni_class == NULL) {
            // 094         jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
            // 095         CHECK_NULL(c);
            // 096         c = (*env)->NewGlobalRef(env, c);
            // 097         CHECK_NULL(c);
            // 098         ni_defaultIndexID = (*env)->GetStaticFieldID(
            // 099             env, c, "defaultIndex", "I");
            // 100         ni_class = c;
            // 101     }
            // 102     int defaultIndex;
            int defaultIndex;
            // 103     struct sockaddr_in6 *sin6 = (struct sockaddr_in6 *)him;
            NetinetIn.sockaddr_in6 sin6 = (NetinetIn.sockaddr_in6) him;
            // 104     if (sin6->sin6_family == AF_INET6 && (sin6->sin6_scope_id == 0)) {
            if ((sin6.sin6_family() == Socket.AF_INET6()) && (sin6.sin6_scope_id() == 0)) {
                // 105         defaultIndex = (*env)->GetStaticIntField(env, ni_class,
                // 106                                                  ni_defaultIndexID);
                defaultIndex = Target_java_net_NetworkInterface.defaultIndex;
                // 107         sin6->sin6_scope_id = defaultIndex;
                sin6.set_sin6_scope_id(defaultIndex);
            }
        }
        // 109 #endif
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 916 void
    // 917 NET_SetTrafficClass(struct sockaddr *him, int trafficClass) {
    static void NET_SetTrafficClass(Socket.sockaddr him, int trafficClass) {
        // 918 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 919     if (him->sa_family == AF_INET6) {
            if (him.sa_family() == Socket.AF_INET6()) {
                // 920         struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
                NetinetIn.sockaddr_in6 him6 = (NetinetIn.sockaddr_in6) him;
                // 921         him6->sin6_flowinfo = htonl((trafficClass & 0xff) << 20);
                him6.set_sin6_flowinfo(NetinetIn.htonl((trafficClass & 0xff) << 20));
            }
        }
        // 923 #endif /* AF_INET6 */
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 074 #define NET_Timeout     JVM_Timeout
    static int NET_Timeout(int fd, long timeout) {
        return Target_os.timeout(fd, timeout);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 075 #define NET_Read        JVM_Read
    static int NET_Read(int fd, CCharPointer bufP, int len) {
        return (int) Target_os.restartable_read(fd, bufP, len);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 081 #define NET_Connect     JVM_Connect
    static int NET_Connect(int fd, Socket.sockaddr him, int len) {
        return VmPrimsJVM.JVM_Connect(fd, him, len);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 082 #define NET_Accept      JVM_Accept
    static int NET_Accept(int fd, Socket.sockaddr him, CIntPointer len_Pointer) {
        return VmPrimsJVM.JVM_Accept(fd, him, len_Pointer);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 086 #define NET_Poll        poll
    static int NET_Poll(pollfd fds, int nfds, int timeout) {
        return Poll.poll(fds, nfds, timeout);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 078 #define NET_Send        JVM_Send
    static int NET_Send(int fd, CCharPointer buf, int nBytes, int flags) {
        return VmPrimsJVM.JVM_Send(fd, buf, nBytes, flags);
    }
    /* @formatter:on */

    static int NET_SendTo(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, int addr_len) {
        return VmPrimsJVM.JVM_SendTo(fd, buf, n, flags, addr, addr_len);
    }

    static int NET_RecvFrom(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, CIntPointer addr_len) {
        return VmPrimsJVM.JVM_RecvFrom(fd, buf, n, flags, addr, addr_len);
    }

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 926 JNIEXPORT jint JNICALL
    // 927 NET_GetPortFromSockaddr(struct sockaddr *him) {
    static int NET_GetPortFromSockaddr(Socket.sockaddr him) {
        // 928 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 929     if (him->sa_family == AF_INET6) {
            if (him.sa_family() == Socket.AF_INET6()) {
                // 930         return ntohs(((struct sockaddr_in6*)him)->sin6_port);
                return NetinetIn.ntohs(((NetinetIn.sockaddr_in6) him).sin6_port());
            }
        }
        // 932         } else
        // 933 #endif /* AF_INET6 */
        // 934             {
        // 935                 return ntohs(((struct sockaddr_in*)him)->sin_port);
        return NetinetIn.ntohs(((NetinetIn.sockaddr_in) him).sin_port());
        // 936             }
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 084 #define NET_Dup2        dup2
    static int NET_Dup2(int fd, int fd2) {
        return Unistd.dup2(fd, fd2);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 083 #define NET_SocketClose JVM_SocketClose
    static int NET_SocketClose(int fd) {
        return VmPrimsJVM.JVM_SocketClose(fd);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 457 void
    // 458 NET_AllocSockaddr(struct sockaddr **him, int *len) {
    static void NET_AllocSockaddr(Socket.sockaddrPointer him, CIntPointer len) {
    // 459 #ifdef AF_INET6
    // 460     if (ipv6_available()) {
        if (IsDefined.socket_AF_INET6() && JavaNetNetUtil.ipv6_available()) {
            // 461         struct sockaddr_in6 *him6 = (struct sockaddr_in6*)malloc(sizeof(struct sockaddr_in6));
            Socket.sockaddr him6 = (Socket.sockaddr) LibC.malloc(WordFactory.unsigned(SizeOf.get(NetinetIn.sockaddr_in6.class)));
            // 462         *him = (struct sockaddr*)him6;
            him.write(him6);
            // 463         *len = sizeof(struct sockaddr_in6);
            len.write(SizeOf.get(NetinetIn.sockaddr_in6.class));
        } else {
            // 467             struct sockaddr_in *him4 = (struct sockaddr_in*)malloc(sizeof(struct sockaddr_in));
            Socket.sockaddr him4 = (Socket.sockaddr) LibC.malloc(WordFactory.unsigned(SizeOf.get(NetinetIn.sockaddr_in.class)));
            // 468             *him = (struct sockaddr*)him4;
            him.write(him4);
            // 469             *len = sizeof(struct sockaddr_in);
            len.write(SizeOf.get(NetinetIn.sockaddr_in.class));
        }
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    /* Do I ever need a class for this union, or just the size? */
    // 180 #define SOCKADDR        union { \
    // 181                             struct sockaddr_in him4; \
    // 182                             struct sockaddr_in6 him6; \
    // 183                         }
    // 184
    // 185 #define SOCKADDR_LEN    (ipv6_available() ? sizeof(SOCKADDR) : \
    // 186                          sizeof(struct sockaddr_in))
    /* sizeof-like method. */
    @Fold
    static int SOCKADDR_LEN() {
        final int sizeof_sockaddr_in = SizeOf.get(NetinetIn.sockaddr_in.class);
        final int sizeof_sockaddr_in6 = SizeOf.get(NetinetIn.sockaddr_in6.class);
        final int sizeof_SOCKADDR = Integer.max(sizeof_sockaddr_in, sizeof_sockaddr_in6);
        return (JavaNetNetUtil.ipv6_available() ? sizeof_SOCKADDR : sizeof_sockaddr_in);
    }
    /* @formatter:on */

    // 171 #define MAX_BUFFER_LEN 65536
    /* sizeof-like method. */
    @Fold
    static int MAX_BUFFER_LEN() {
        return 65536;
    }

    // 172 #define MAX_HEAP_BUFFER_LEN 131072
    static int MAX_HEAP_BUFFER_LEN() {
        return 131072;
    }

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 987     static struct {
    // 988         jint cmd;
    // 989         int level;
    // 990         int optname;
    // 991     } const opts[] = {
    private static class OptEntry {

        private final int cmd;
        private final int level;
        private final int optname;

        public OptEntry(int cmd, int level, int optname) {
            this.cmd = cmd;
            this.level = level;
            this.optname = optname;
        }
    }
    /* @formatter:on */

    /** Lazily-initialized map of socket options. */
    private static OptEntry[] opts = null;

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 981 /*
    // 982  * Map the Java level socket option to the platform specific
    // 983  * level and option name.
    // 984  */
    // 985 int
    // 986 NET_MapSocketOption(jint cmd, int *level, int *optname) {
    static int NET_MapSocketOption(int cmd, CIntPointer level_Pointer, CIntPointer optname_Pointer) {
        // 992         { java_net_SocketOptions_TCP_NODELAY,           IPPROTO_TCP,    TCP_NODELAY },
        // 993         { java_net_SocketOptions_SO_OOBINLINE,          SOL_SOCKET,     SO_OOBINLINE },
        // 994         { java_net_SocketOptions_SO_LINGER,             SOL_SOCKET,     SO_LINGER },
        // 995         { java_net_SocketOptions_SO_SNDBUF,             SOL_SOCKET,     SO_SNDBUF },
        // 996         { java_net_SocketOptions_SO_RCVBUF,             SOL_SOCKET,     SO_RCVBUF },
        // 997         { java_net_SocketOptions_SO_KEEPALIVE,          SOL_SOCKET,     SO_KEEPALIVE },
        // 998         { java_net_SocketOptions_SO_REUSEADDR,          SOL_SOCKET,     SO_REUSEADDR },
        // 999         { java_net_SocketOptions_SO_BROADCAST,          SOL_SOCKET,     SO_BROADCAST },
        // 1000        { java_net_SocketOptions_IP_TOS,                IPPROTO_IP,     IP_TOS },
        // 1001        { java_net_SocketOptions_IP_MULTICAST_IF,       IPPROTO_IP,     IP_MULTICAST_IF },
        // 1002        { java_net_SocketOptions_IP_MULTICAST_IF2,      IPPROTO_IP,     IP_MULTICAST_IF },
        // 1003        { java_net_SocketOptions_IP_MULTICAST_LOOP,     IPPROTO_IP,     IP_MULTICAST_LOOP },
        // 1004     };
        if (opts == null) {
            opts = new OptEntry[] {
                            new OptEntry(SocketOptions.TCP_NODELAY,        NetinetIn.IPPROTO_TCP(),  NetinetTcp.TCP_NODELAY()),
                            new OptEntry(SocketOptions.SO_OOBINLINE,       Socket.SOL_SOCKET(),      Socket.SO_OOBINLINE()),
                            new OptEntry(SocketOptions.SO_LINGER,          Socket.SOL_SOCKET(),      Socket.SO_LINGER()),
                            new OptEntry(SocketOptions.SO_SNDBUF,          Socket.SOL_SOCKET(),      Socket.SO_SNDBUF()),
                            new OptEntry(SocketOptions.SO_RCVBUF,          Socket.SOL_SOCKET(),      Socket.SO_RCVBUF()),
                            new OptEntry(SocketOptions.SO_KEEPALIVE,       Socket.SOL_SOCKET(),      Socket.SO_KEEPALIVE()),
                            new OptEntry(SocketOptions.SO_REUSEADDR,       Socket.SOL_SOCKET(),      Socket.SO_REUSEADDR()),
                            new OptEntry(SocketOptions.SO_BROADCAST,       Socket.SOL_SOCKET(),      Socket.SO_BROADCAST()),
                            new OptEntry(SocketOptions.IP_TOS,             NetinetIn.IPPROTO_IP(),   NetinetIn.IP_TOS()),
                            new OptEntry(SocketOptions.IP_MULTICAST_IF,    NetinetIn.IPPROTO_IP(),   NetinetIn.IP_MULTICAST_IF()),
                            new OptEntry(SocketOptions.IP_MULTICAST_LOOP,  NetinetIn.IPPROTO_IP(),   NetinetIn.IP_MULTICAST_LOOP()),
            };
        }
        // 1005
        // 1006     int i;
        // 1007
        // 1008     /*
        // 1009      * Different multicast options if IPv6 is enabled
        // 1010      */
        // 1011 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 1012     if (ipv6_available()) {
            if (JavaNetNetUtil.ipv6_available()) {
                // 1013         switch (cmd) {
                switch (cmd) {
                    // 1014             case java_net_SocketOptions_IP_MULTICAST_IF:
                    // 1015             case java_net_SocketOptions_IP_MULTICAST_IF2:
                    case SocketOptions.IP_MULTICAST_IF:
                    case SocketOptions.IP_MULTICAST_IF2:
                        // 1016                 *level = IPPROTO_IPV6;
                        level_Pointer.write(NetinetIn.IPPROTO_IPV6());
                        // 1017                 *optname = IPV6_MULTICAST_IF;
                        optname_Pointer.write(NetinetIn.IPV6_MULTICAST_IF());
                        // 1018                 return 0;
                        return 0;
                        // 1019
                        // 1020             case java_net_SocketOptions_IP_MULTICAST_LOOP:
                    case SocketOptions.IP_MULTICAST_LOOP:
                        // 1021                 *level = IPPROTO_IPV6;
                        level_Pointer.write(NetinetIn.IPPROTO_IPV6());
                        // 1022                 *optname = IPV6_MULTICAST_LOOP;
                        optname_Pointer.write(NetinetIn.IPV6_MULTICAST_LOOP());
                        // 1023                 return 0;
                        return 0;
                }
            }
        }
        // 1026 #endif
        // 1027
        // 1028     /*
        // 1029      * Map the Java level option to the native level
        // 1030      */
        // 1031     for (i=0; i<(int)(sizeof(opts) / sizeof(opts[0])); i++) {
        for (int i = 0; i < opts.length; i += 1) {
            // 1032         if (cmd == opts[i].cmd) {
            if (cmd == opts[i].cmd) {
                // 1033             *level = opts[i].level;
                level_Pointer.write(opts[i].level);
                // 1034             *optname = opts[i].optname;
                optname_Pointer.write(opts[i].optname);
                // 1035             return 0;
                return 0;
            }
        }
        // 1038
        // 1039     /* not found */
        // 1040     return -1;
        return -1;
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    //     1195 /*
    //     1196  * Wrapper for getsockopt system routine - does any necessary
    //     1197  * pre/post processing to deal with OS specific oddities :-
    //     1198  *
    //     1199  * IP_TOS is a no-op with IPv6 sockets as it's setup when
    //     1200  * the connection is established.
    //     1201  *
    //     1202  * On Linux the SO_SNDBUF/SO_RCVBUF values must be post-processed
    //     1203  * to compensate for an incorrect value returned by the kernel.
    //     1204  */
    //     1205 int
    //     1206 NET_GetSockOpt(int fd, int level, int opt, void *result,
    //     1207                int *len)
    //     1208 {
    static int NET_GetSockOpt(int fd, int level, int opt, PointerBase result_Pointer, CIntPointer len_Pointer) {
        //     1209     int rv;
        int rv;
        //     1210
        //     1211 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            //     1212     if ((level == IPPROTO_IP) && (opt == IP_TOS)) {
            if ((level == NetinetIn.IPPROTO_IP()) && (opt == NetinetIn.IP_TOS())) {
                //     1213         if (ipv6_available()) {
                if (JavaNetNetUtil.ipv6_available()) {
                    //     1214
                    //     1215             /*
                    //     1216              * For IPv6 socket option implemented at Java-level
                    //     1217              * so return -1.
                    //     1218              */
                    //     1219             int *tc = (int *)result;
                    //     1220             *tc = -1;
                    CIntPointer tc = (CIntPointer) result_Pointer;
                    tc.write(-1);
                    //     1221             return 0;
                    return 0;
                }
            }
        }
        //     1224 #endif
        //     1225
        //     1226 #ifdef __solaris__
        if (IsDefined.__solaris__()) {
        //     1227     rv = getsockopt(fd, level, opt, result, len);
        rv = Socket.getsockopt(fd, level, opt, result_Pointer, len_Pointer);
        //     1228 #else
        } else {
            //     1230         socklen_t socklen = *len;
            CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
            socklen_Pointer.write(len_Pointer.read());
            //     1231         rv = getsockopt(fd, level, opt, result, &socklen);
            rv = Socket.getsockopt(fd, level, opt, result_Pointer, socklen_Pointer);
            //     1232         *len = socklen;
            len_Pointer.write(socklen_Pointer.read());
        }
        //     1234 #endif
        //     1235
        //     1236     if (rv < 0) {
        if (rv < 0) {
        //     1237         return rv;
            return rv;
        }
        //     1239
        //     1240 #ifdef __linux__
        if (IsDefined.__linux__()) {
            //     1241     /*
            //     1242      * On Linux SO_SNDBUF/SO_RCVBUF aren't symmetric. This
            //     1243      * stems from additional socket structures in the send
            //     1244      * and receive buffers.
            //     1245      */
            //     1246     if ((level == SOL_SOCKET) && ((opt == SO_SNDBUF)
            //     1247                                   || (opt == SO_RCVBUF))) {
            if ((level == Socket.SOL_SOCKET()) && ((opt == Socket.SO_SNDBUF()) || (opt == Socket.SO_RCVBUF()))) {
                //     1248         int n = *((int *)result);
                int n = ((CIntPointer) result_Pointer).read();
                //     1249         n /= 2;
                n /= 2;
                //     1250         *((int *)result) = n;
                ((CIntPointer) result_Pointer).write(n);
            }
        }
        //     1252 #endif
        //     1253
        //     1254 /* Workaround for Mac OS treating linger value as
        //     1255  *  signed integer
        //     1256  */
        //     1257 #ifdef MACOSX
        if (IsDefined.MACOSX()) {
            //     1258     if (level == SOL_SOCKET && opt == SO_LINGER) {
            if (level == Socket.SOL_SOCKET() && opt == Socket.SO_LINGER()) {
                //     1259         struct linger* to_cast = (struct linger*)result;
                Socket.linger to_cast = (Socket.linger) result_Pointer;
                //     1260         to_cast->l_linger = (unsigned short)to_cast->l_linger;
                to_cast.set_l_linger(to_cast.l_linger() & 0xFF);
            }
        }
        //     1262 #endif
        //     1263     return rv;
        return rv;
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    //     1266 /*
    //     1267  * Wrapper for setsockopt system routine - performs any
    //     1268  * necessary pre/post processing to deal with OS specific
    //     1269  * issue :-
    //     1270  *
    //     1271  * On Solaris need to limit the suggested value for SO_SNDBUF
    //     1272  * and SO_RCVBUF to the kernel configured limit
    //     1273  *
    //     1274  * For IP_TOS socket option need to mask off bits as this
    //     1275  * aren't automatically masked by the kernel and results in
    //     1276  * an error. In addition IP_TOS is a NOOP with IPv6 as it
    //     1277  * should be setup as connection time.
    //     1278  */
    //     1279 int
    //     1280 NET_SetSockOpt(int fd, int level, int  opt, const void *arg,
    //     1281                int len)
    //     1282 {
    static int NET_SetSockOpt(int fd, int level, int opt, WordPointer arg, int len) {
        //     1283
        //     1284 #ifndef IPTOS_TOS_MASK
        //     1285 #define IPTOS_TOS_MASK 0x1e
        final int IPTOS_TOS_MASK = 0x1e;
        //     1286 #endif
        //     1287 #ifndef IPTOS_PREC_MASK
        //     1288 #define IPTOS_PREC_MASK 0xe0
        final int IPTOS_PREC_MASK = 0xe0;
        //     1289 #endif
        //     1290
        //     1291 #if defined(_ALLBSD_SOURCE)
        /* Hoist declarations out of "#if defined" block". */
        CIntPointer mib = null;
        CLongPointer rlen_Pointer = null;
        CIntPointer bufsize = null;
        /* maxsockbuf should be static. */
        /* Declaring maxsockbuf to be a CIntPointer because it is only used inside _ALLBSD_SOURCE. */
        CIntPointer maxsockbuf_Pointer = StackValue.get(CIntPointer.class);
        int maxsockbuf_size = SizeOf.get(CIntPointer.class);
        if (IsDefined._ALLBSD_SOURCE()) {
            //     1292 #if defined(KIPC_MAXSOCKBUF)
            if (IsDefined.sysctl_KIPC_MAXSOCKBUF()) {
                //     1293     int mib[3];
                mib = StackValue.get(3, CIntPointer.class);
                //     1294     size_t rlen;
                rlen_Pointer = StackValue.get(CLongPointer.class);
            }
            //     1295 #endif
            //     1296
            //     1297     int *bufsize;
            bufsize = StackValue.get(CIntPointer.class);
            //     1298
            //     1299 #ifdef __APPLE__
            if (IsDefined.__APPLE__()) {
                //     1300     static int maxsockbuf = -1;
                maxsockbuf_Pointer.write(-1);
                maxsockbuf_size = SizeOf.get(CIntPointer.class);
                //     1301 #else
            } else {
                //     1302     static long maxsockbuf = -1;
                VMError.unimplemented();
            }
            //     1303 #endif
        } /* _ALLBSD_SOURCE */
        //     1304 #endif
        //     1305
        //     1306     /*
        //     1307      * IPPROTO/IP_TOS :-
        //     1308      * 1. IPv6 on Solaris/Mac OS: NOOP and will be set
        //     1309      *    in flowinfo field when connecting TCP socket,
        //     1310      *    or sending UDP packet.
        //     1311      * 2. IPv6 on Linux: By default Linux ignores flowinfo
        //     1312      *    field so enable IPV6_FLOWINFO_SEND so that flowinfo
        //     1313      *    will be examined. We also set the IPv4 TOS option in this case.
        //     1314      * 3. IPv4: set socket option based on ToS and Precedence
        //     1315      *    fields (otherwise get invalid argument)
        //     1316      */
        //     1317     if (level == IPPROTO_IP && opt == IP_TOS) {
        if (level == NetinetIn.IPPROTO_IP() && opt == NetinetIn.IP_TOS()) {
            //     1318         int *iptos;
            CIntPointer iptos = StackValue.get(CIntPointer.class);
            //     1319
            //     1320 #if defined(AF_INET6) && (defined(__solaris__) || defined(MACOSX))
            if (IsDefined.socket_AF_INET6() && (IsDefined.__solaris__() || IsDefined.MACOSX())) {
                //     1321         if (ipv6_available()) {
                if (JavaNetNetUtil.ipv6_available()) {
                    //     1322             return 0;
                    return 0;
                }
                //     1324 #endif
            }
            //     1325
            //     1326 #if defined(AF_INET6) && defined(__linux__)
            if (IsDefined.socket_AF_INET6() && IsDefined.__linux__()) {
                VMError.unimplemented();
                //     1327         if (ipv6_available()) {
                //     1328             int optval = 1;
                //     1329             if (setsockopt(fd, IPPROTO_IPV6, IPV6_FLOWINFO_SEND,
                //     1330                            (void *)&optval, sizeof(optval)) < 0) {
                //     1331                 return -1;
                //     1332             }
                //     1332         }
            }
            //     1334 #endif
            //     1335
            //     1336         iptos = (int *)arg;
            iptos = (CIntPointer) arg;
            //     1337         *iptos &= (IPTOS_TOS_MASK | IPTOS_PREC_MASK);
            iptos.write(iptos.read() & (IPTOS_TOS_MASK | IPTOS_PREC_MASK));
        }
        //     1339
        //     1340     /*
        //     1341      * SOL_SOCKET/{SO_SNDBUF,SO_RCVBUF} - On Solaris we may need to clamp
        //     1342      * the value when it exceeds the system limit.
        //     1343      */
        //     1344 #ifdef __solaris__
        if (IsDefined.__solaris__()) {
            VMError.unimplemented();
            //     1345     if (level == SOL_SOCKET) {
            //     1346         if (opt == SO_SNDBUF || opt == SO_RCVBUF) {
            //     1347             int sotype=0, arglen;
            //     1348             int *bufsize, maxbuf;
            //     1349             int ret;
            //     1350
            //     1351             /* Attempt with the original size */
            //     1352             ret = setsockopt(fd, level, opt, arg, len);
            //     1353             if ((ret == 0) || (ret == -1 && errno != ENOBUFS))
            //     1354                 return ret;
            //     1355
            //     1356             /* Exceeded system limit so clamp and retry */
            //     1357
            //     1358             arglen = sizeof(sotype);
            //     1359             if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (void *)&sotype,
            //     1360                            &arglen) < 0) {
            //     1361                 return -1;
            //     1362             }
            //     1363
            //     1364             /*
            //     1365              * We try to get tcp_maxbuf (and udp_max_buf) using
            //     1366              * an ioctl() that isn't available on all versions of Solaris.
            //     1367              * If that fails, we use the search algorithm in findMaxBuf()
            //     1368              */
            //     1369             if (!init_tcp_max_buf && sotype == SOCK_STREAM) {
            //     1370                 tcp_max_buf = net_getParam("/dev/tcp", "tcp_max_buf");
            //     1371                 if (tcp_max_buf == -1) {
            //     1372                     tcp_max_buf = findMaxBuf(fd, opt, SOCK_STREAM);
            //     1373                     if (tcp_max_buf == -1) {
            //     1374                         return -1;
            //     1375                     }
            //     1376                 }
            //     1377                 init_tcp_max_buf = 1;
            //     1378             } else if (!init_udp_max_buf && sotype == SOCK_DGRAM) {
            //     1379                 udp_max_buf = net_getParam("/dev/udp", "udp_max_buf");
            //     1380                 if (udp_max_buf == -1) {
            //     1381                     udp_max_buf = findMaxBuf(fd, opt, SOCK_DGRAM);
            //     1382                     if (udp_max_buf == -1) {
            //     1383                         return -1;
            //     1384                     }
            //     1385                 }
            //     1386                 init_udp_max_buf = 1;
            //     1387             }
            //     1388
            //     1389             maxbuf = (sotype == SOCK_STREAM) ? tcp_max_buf : udp_max_buf;
            //     1390             bufsize = (int *)arg;
            //     1391             if (*bufsize > maxbuf) {
            //     1392                 *bufsize = maxbuf;
            //     1393             }
            //     1394         }
            //     1395     }
        }
        //     1396 #endif
        //     1397
        //     1398 #ifdef _AIX
        //     1399     if (level == SOL_SOCKET) {
        //     1400         if (opt == SO_SNDBUF || opt == SO_RCVBUF) {
        //     1401             /*
        //     1402              * Just try to set the requested size. If it fails we will leave the
        //     1403              * socket option as is. Setting the buffer size means only a hint in
        //     1404              * the jse2/java software layer, see javadoc. In the previous
        //     1405              * solution the buffer has always been truncated to a length of
        //     1406              * 0x100000 Byte, even if the technical limit has not been reached.
        //     1407              * This kind of absolute truncation was unexpected in the jck tests.
        //     1408              */
        //     1409             int ret = setsockopt(fd, level, opt, arg, len);
        //     1410             if ((ret == 0) || (ret == -1 && errno == ENOBUFS)) {
        //     1411                 // Accept failure because of insufficient buffer memory resources.
        //     1412                 return 0;
        //     1413             } else {
        //     1414                 // Deliver all other kinds of errors.
        //     1415                 return ret;
        //     1416             }
        //     1417         }
        //     1418     }
        //     1419 #endif
        //     1420
        //     1421     /*
        //     1422      * On Linux the receive buffer is used for both socket
        //     1423      * structures and the packet payload. The implication
        //     1424      * is that if SO_RCVBUF is too small then small packets
        //     1425      * must be discard.
        //     1426      */
        //     1427 #ifdef __linux__
        if (IsDefined.__linux__()) {
            //     1428     if (level == SOL_SOCKET && opt == SO_RCVBUF) {
            if (level == Socket.SOL_SOCKET() && opt == Socket.SO_RCVBUF()) {
                //     1429         int *bufsize = (int *)arg;
                bufsize = (CIntPointer) arg;
                //     1430         if (*bufsize < 1024) {
                if (bufsize.read() < 1024) {
                    //     1431             *bufsize = 1024;
                    bufsize.write(1024);
                }
            }
        }
        //     1434 #endif
        //     1435
        //     1436 #if defined(_ALLBSD_SOURCE)
        if (IsDefined._ALLBSD_SOURCE()) {
            //     1437     /*
            //     1438      * SOL_SOCKET/{SO_SNDBUF,SO_RCVBUF} - On FreeBSD need to
            //     1439      * ensure that value is <= kern.ipc.maxsockbuf as otherwise we get
            //     1440      * an ENOBUFS error.
            //     1441      */
            //     1442     if (level == SOL_SOCKET) {
            if (level == Socket.SOL_SOCKET()) {
                //     1443         if (opt == SO_SNDBUF || opt == SO_RCVBUF) {
                if (opt == Socket.SO_SNDBUF() || opt == Socket.SO_RCVBUF()) {
                    //     1444 #ifdef KIPC_MAXSOCKBUF
                    if (IsDefined.sysctl_KIPC_MAXSOCKBUF()) {
                        //     1445             if (maxsockbuf == -1) {
                        if (maxsockbuf_Pointer.read() == -1) {
                            //     1446                mib[0] = CTL_KERN;
                            mib.write(0, DarwinSysctl.CTL_KERN());
                            //     1447                mib[1] = KERN_IPC;
                            mib.write(1, DarwinSysctl.KERN_IPC());
                            //     1448                mib[2] = KIPC_MAXSOCKBUF;
                            mib.write(2, DarwinSysctl.KIPC_MAXSOCKBUF());
                            //     1449                rlen = sizeof(maxsockbuf);
                            rlen_Pointer.write(maxsockbuf_size);
                            //     1450                if (sysctl(mib, 3, &maxsockbuf, &rlen, NULL, 0) == -1)
                            if (Sysctl.sysctl(mib, 3, maxsockbuf_Pointer, (WordPointer) rlen_Pointer, WordFactory.nullPointer(), 0) == -1) {
                                //     1451                    maxsockbuf = 1024;
                                maxsockbuf_Pointer.write(1024);
                            }
                            //     1452
                            //     1453 #if 1
                            if (true) {
                                //     1454                /* XXXBSD: This is a hack to workaround mb_max/mb_max_adj
                                //     1455                   problem.  It should be removed when kern.ipc.maxsockbuf
                                //     1456                   will be real value. */
                                //     1457                maxsockbuf = (maxsockbuf/5)*4;
                                maxsockbuf_Pointer.write((maxsockbuf_Pointer.read() / 5) * 4);
                            }
                            //     1458 #endif
                        }
                        //     1460 #elif defined(__OpenBSD__)
                    } else if (IsDefined.__OpenBSD__()) {
                        VMError.unimplemented();
                        //     1461            maxsockbuf = SB_MAX;
                        //     1462 #else
                    } else {
                        VMError.unimplemented();
                        //     1463            maxsockbuf = 64 * 1024;      /* XXX: NetBSD */
                    }
                    //     1464 #endif
                    //     1465
                    //     1466            bufsize = (int *)arg;
                    bufsize = (CIntPointer) arg;
                    //     1467            if (*bufsize > maxsockbuf) {
                    if (bufsize.read() > maxsockbuf_Pointer.read()) {
                        //     1468                *bufsize = maxsockbuf;
                        bufsize.write(maxsockbuf_Pointer.read());
                    }
                    //     1470
                    //     1471            if (opt == SO_RCVBUF && *bufsize < 1024) {
                    if (opt == Socket.SO_RCVBUF() && bufsize.read() < 1024) {
                        //     1472                 *bufsize = 1024;
                        bufsize.write(1024);
                    }
                    //     1474
                }
            }
        }
        //     1477 #endif
        //     1478
        //     1479 #if defined(_ALLBSD_SOURCE) || defined(_AIX)
        if (IsDefined._AIX()) {
            VMError.unimplemented();
            //     1480     /*
            //     1481      * On Solaris, SO_REUSEADDR will allow multiple datagram
            //     1482      * sockets to bind to the same port. The network jck tests check
            //     1483      * for this "feature", so we need to emulate it by turning on
            //     1484      * SO_REUSEPORT as well for that combination.
            //     1485      */
            //     1486     if (level == SOL_SOCKET && opt == SO_REUSEADDR) {
            //     1487         int sotype;
            //     1488         socklen_t arglen;
            //     1489
            //     1490         arglen = sizeof(sotype);
            //     1491         if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (void *)&sotype, &arglen) < 0) {
            //     1492             return -1;
            //     1493         }
            //     1494
            //     1495         if (sotype == SOCK_DGRAM) {
            //     1496             setsockopt(fd, level, SO_REUSEPORT, arg, len);
            //     1497         }
            //     1498     }
        }
        //     1499 #endif
        //     1500
        //     1501     return setsockopt(fd, level, opt, arg, len);
        return Socket.setsockopt(fd, level, opt, arg, len);
    }
    /* @formatter:on */

    /* Do not re-wrap commented-out code.  @formatter:off */
    // 1504 /*
    // 1505  * Wrapper for bind system call - performs any necessary pre/post
    // 1506  * processing to deal with OS specific issues :-
    // 1507  *
    // 1508  * Linux allows a socket to bind to 127.0.0.255 which must be
    // 1509  * caught.
    // 1510  *
    // 1511  * On Solaris with IPv6 enabled we must use an exclusive
    // 1512  * bind to guarantee a unique port number across the IPv4 and
    // 1513  * IPv6 port spaces.
    // 1514  *
    // 1515  */
    // 1516 int
    // 1517 NET_Bind(int fd, struct sockaddr *him, int len)
    // 1518 {
    static int NET_Bind(int fd, Socket.sockaddr him, int len) {
        /* I am assuming that __solaris__ is not defined, so I am not implementing the exclusive bind. */
        // 1519 #if defined(__solaris__) && defined(AF_INET6)
        // 1520     int level = -1;
        // 1521     int exclbind = -1;
        // 1522 #endif
        // 1523     int rv;
        int rv;
        // 1525 #ifdef __linux__
        if (IsDefined.__linux__()) {
            // 1526     /*
            // 1527      * ## get bugId for this issue - goes back to 1.2.2 port ##
            // 1528      * ## When IPv6 is enabled this will be an IPv4-mapped
            // 1529      * ## with family set to AF_INET6
            // 1530      */
            // 1531     if (him->sa_family == AF_INET) {
            if (him.sa_family() == Socket.AF_INET()) {
                // 1532         struct sockaddr_in *sa = (struct sockaddr_in *)him;
                NetinetIn.sockaddr_in sa = (NetinetIn.sockaddr_in) him;
                // 1533         if ((ntohl(sa->sin_addr.s_addr) & 0x7f0000ff) == 0x7f0000ff) {
                if ((NetinetIn.htonl(sa.sin_addr().s_addr()) & 0x7f0000ff) == 0x7f0000ff) {
                    // 1534             errno = EADDRNOTAVAIL;
                    Errno.set_errno(Errno.EADDRNOTAVAIL());
                    // 1535             return -1;
                    return -1;
                }
            }
        }
        // 1538 #endif
        // 1540 #if defined(__solaris__) && defined(AF_INET6)
        if (IsDefined.__solaris__() && IsDefined.socket_AF_INET6()) {
            VMError.unimplemented();
            // 1541     /*
            // 1542      * Solaris has separate IPv4 and IPv6 port spaces so we
            // 1543      * use an exclusive bind when SO_REUSEADDR is not used to
            // 1544      * give the illusion of a unified port space.
            // 1545      * This also avoids problems with IPv6 sockets connecting
            // 1546      * to IPv4 mapped addresses whereby the socket conversion
            // 1547      * results in a late bind that fails because the
            // 1548      * corresponding IPv4 port is in use.
            // 1549      */
            // 1550     if (ipv6_available()) {
            // 1551         int arg, len;
            // 1552
            // 1553         len = sizeof(arg);
            // 1554         if (useExclBind || getsockopt(fd, SOL_SOCKET, SO_REUSEADDR,
            // 1555                        (char *)&arg, &len) == 0) {
            // 1556             if (useExclBind || arg == 0) {
            // 1557                 /*
            // 1558                  * SO_REUSEADDR is disabled or sun.net.useExclusiveBind
            // 1559                  * property is true so enable TCP_EXCLBIND or
            // 1560                  * UDP_EXCLBIND
            // 1561                  */
            // 1562                 len = sizeof(arg);
            // 1563                 if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (char *)&arg,
            // 1564                                &len) == 0) {
            // 1565                     if (arg == SOCK_STREAM) {
            // 1566                         level = IPPROTO_TCP;
            // 1567                         exclbind = TCP_EXCLBIND;
            // 1568                     } else {
            // 1569                         level = IPPROTO_UDP;
            // 1570                         exclbind = UDP_EXCLBIND;
            // 1571                     }
            // 1572                 }
            // 1573
            // 1574                 arg = 1;
            // 1575                 setsockopt(fd, level, exclbind, (char *)&arg,
            // 1576                            sizeof(arg));
            // 1577             }
            // 1578         }
            // 1579     }
            // 1580
        }
        // 1581 #endif
        // 1583     rv = bind(fd, him, len);
        rv = Socket.bind(fd, him, len);
        /* I am assuming that __solaris__ is not defined. */
        // 1585 #if defined(__solaris__) && defined(AF_INET6)
        if (IsDefined.__solaris__() && IsDefined.socket_AF_INET6()) {
            VMError.unimplemented();
            // 1586     if (rv < 0) {
            // 1587         int en = errno;
            // 1588         /* Restore *_EXCLBIND if the bind fails */
            // 1589         if (exclbind != -1) {
            // 1590             int arg = 0;
            // 1591             setsockopt(fd, level, exclbind, (char *)&arg,
            // 1592                        sizeof(arg));
            // 1593         }
            // 1594         errno = en;
            // 1595     }
        }
        // 1596 #endif
        // 1598     return rv;
        return rv;
    }
    /* @formatter:on */
}

/** Native methods (and macros) from src/share/vm/prims/jvm.cpp translated to Java. */
class VmPrimsJVM {
    /* Do not re-wrap commented-out code.  @formatter:off */

    /* Private constructor: No instances. */
    private VmPrimsJVM() {
    }

    // 3719 JVM_LEAF(jint, JVM_Socket(jint domain, jint type, jint protocol))
    static int JVM_Socket(int domain, int type, int protocol) {
        // 3720   JVMWrapper("JVM_Socket");
        // 3721   return os::socket(domain, type, protocol);
        return Socket.socket(domain, type, protocol);
        // 3722 JVM_END
    }

    // 3767 JVM_LEAF(jint, JVM_Connect(jint fd, struct sockaddr *him, jint len))
    static int JVM_Connect(int fd, Socket.sockaddr him, int len) {
        // 3768   JVMWrapper2("JVM_Connect (0x%x)", fd);
        // 3769   //%note jvm_r6
        // 3770   return os::connect(fd, him, (socklen_t)len);
        return Socket.connect(fd, him, len);
        // 3771 JVM_END
    }

    // 3732 JVM_LEAF(jint, JVM_SocketShutdown(jint fd, jint howto))
    static int JVM_SocketShutdown(int fd, int howto) {
        // 3733   JVMWrapper2("JVM_SocketShutdown (0x%x)", fd);
        // 3734   //%note jvm_r6
        // 3735   return os::socket_shutdown(fd, howto);
        return Socket.shutdown(fd, howto);
        // 3736 JVM_END
    }

    // 3781 JVM_LEAF(jint, JVM_Accept(jint fd, struct sockaddr *him, jint *len))
    static int JVM_Accept(int fd, Socket.sockaddr him, CIntPointer len_Pointer) {
        // 3782   JVMWrapper2("JVM_Accept (0x%x)", fd);
        // 3783   //%note jvm_r6
        // 3784   socklen_t socklen = (socklen_t)(*len);
        CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
        socklen_Pointer.write(len_Pointer.read());
        // 3785   jint result = os::accept(fd, him, &socklen);
        int result = Socket.accept(fd, him, socklen_Pointer);
        // 3786   *len = (jint)socklen;
        len_Pointer.write(socklen_Pointer.read());
        // 3787   return result;
        return result;
        // 3788 JVM_END
    }

    // 3825 JVM_LEAF(jint, JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen))
    static int JVM_GetSockOpt(int fd, int level, int optname, CCharPointer optval, CIntPointer optlen) {
        // 3826   JVMWrapper2("JVM_GetSockOpt (0x%x)", fd);
        // 3827   //%note jvm_r6
        /* typedef u_int socklen_t; */
        // 3828   socklen_t socklen = (socklen_t)(*optlen);
        CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
        socklen_Pointer.write(optlen.read());
         // 3829   jint result = os::get_sock_opt(fd, level, optname, optval, &socklen);
        int result = Socket.getsockopt(fd, level, optname, optval, optlen);
        // 3830   *optlen = (int)socklen;
        optlen.write(socklen_Pointer.read());
        // 3831   return result;
        return result;
        // 3832 JVM_END    /* @formatter:on */
    }

    /* Do not re-format commented out code: @formatter:off */
    // 3794 JVM_LEAF(jint, JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen))
    // 3794 JVMWrapper2("JVM_GetSockOpt (0x%x)", fd);
    static int JVM_SetSockOpt(int fd, int level, int optname, CCharPointer optval, int optlen) {
        // 3794 //%note jvm_r6
        // 3794 return os::set_sock_opt(fd, level, optname, optval, (socklen_t)optlen);
        // 3794 JVM_END
        return Socket.setsockopt(fd, level, optname, optval, optlen);
    }
    /* Do not re-format commented out code: @formatter:on */

    // 3801 JVM_LEAF(jint, JVM_GetSockName(jint fd, struct sockaddr *him, int *len))
    static int JVM_GetSockName(int fd, Socket.sockaddr him, CIntPointer len_Pointer) {
        // 3802 JVMWrapper2("JVM_GetSockName (0x%x)", fd);
        // 3803 //%note jvm_r6
        // 3804 socklen_t socklen = (socklen_t)(*len);
        CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
        socklen_Pointer.write(len_Pointer.read());
        // 3805 jint result = os::get_sock_name(fd, him, &socklen);
        int result = Target_os.get_sock_name(fd, him, socklen_Pointer);
        // 3806 *len = (int)socklen;
        len_Pointer.write(socklen_Pointer.read());
        // 3807 return result;
        return result;
        // 3808 JVM_END
    }

    // 2725 JVM_LEAF(jint, JVM_Read(jint fd, char *buf, jint nbytes))
    static int JVM_Read(int fd, CCharPointer buf, int nbytes) {
        // 2726 JVMWrapper2("JVM_Read (0x%x)", fd);
        // 2727
        // 2728 //%note jvm_r6
        // 2729 return (jint)os::restartable_read(fd, buf, nbytes);
        return (int) Target_os.restartable_read(fd, buf, nbytes);
        // 2730 JVM_END
    }

    // 3818 JVM_LEAF(jint, JVM_SocketAvailable(jint fd, jint *pbytes))
    static int JVM_SocketAvailable(int fd, CIntPointer pbytes) {
        // 3819 JVMWrapper2("JVM_SocketAvailable (0x%x)", fd);
        // 3820 //%note jvm_r6
        // 3821 return os::socket_available(fd, pbytes);
        return Target_os.socket_available(fd, pbytes);
        // 3822 JVM_END
    }

    // 3746 JVM_LEAF(jint, JVM_Send(jint fd, char *buf, jint nBytes, jint flags))
    static int JVM_Send(int fd, CCharPointer buf, int nBytes, int flags) {
        // 3747 JVMWrapper2("JVM_Send (0x%x)", fd);
        // 3748 //%note jvm_r6
        // 3749 return os::send(fd, buf, (size_t)nBytes, (uint)flags);
        return Target_os.send(fd, buf, nBytes, flags);
        // 3750 JVM_END
    }

    static int JVM_SendTo(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, int addr_len) {
        return Target_os.sendto(fd, buf, n, flags, addr, addr_len);
    }

    static int JVM_RecvFrom(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, CIntPointer addr_len) {
        return Target_os.recvfrom(fd, buf, n, flags, addr, addr_len);
    }

    // 3725 JVM_LEAF(jint, JVM_SocketClose(jint fd))
    static int JVM_SocketClose(int fd) {
        // 3726 JVMWrapper2("JVM_SocketClose (0x%x)", fd);
        // 3727 //%note jvm_r6
        // 3728 return os::socket_close(fd);
        return Target_os.socket_close(fd);
        // 3729 JVM_END
    }

    // 3842 JVM_LEAF(int, JVM_GetHostName(char* name, int namelen))
    static int JVM_GetHostName(CCharPointer name, int namelen) {
        // 3843 JVMWrapper("JVM_GetHostName");
        // 3844 return os::get_host_name(name, namelen);
        return Target_os.get_host_name(name, namelen);
        // 3845 JVM_END
    }

    // 3760 JVM_LEAF(jint, JVM_Listen(jint fd, jint count))
    static int JVM_Listen(int fd, int count) {
        // 3761 JVMWrapper2("JVM_Listen (0x%x)", fd);
        // 3762 //%note jvm_r6
        // 3763 return os::listen(fd, count);
        return Target_os.listen(fd, count);
        // 3764 JVM_END
    }

    /* @formatter:on */
}

/** Native methods (and macros) from src/share/vm/prims/jni.cpp translated to Java. */
class VmPrimsJNI {
    /* Do not re-format commented-out code: @formatter:off */

    /* Private constructor: No instances. */
    private VmPrimsJNI() {
    }

    /*
     * A family of functions that copy a primitive array from or to a buffer.
     */

    // 3772 #ifndef USDT2
    // 3773 #define DEFINE_GETSCALARARRAYREGION(ElementTag,ElementType,Result, Tag) \
    // 3774   DT_VOID_RETURN_MARK_DECL(Get##Result##ArrayRegion);\
    // 3775 \
    // 3776 JNI_ENTRY(void, \
    // 3777 jni_Get##Result##ArrayRegion(JNIEnv *env, ElementType##Array array, jsize start, \
    // 3778              jsize len, ElementType *buf)) \
    // 3779   JNIWrapper("Get" XSTR(Result) "ArrayRegion"); \
    // 3780   DTRACE_PROBE5(hotspot_jni, Get##Result##ArrayRegion__entry, env, array, start, len, buf);\
    // 3781   DT_VOID_RETURN_MARK(Get##Result##ArrayRegion); \
    // 3782   typeArrayOop src = typeArrayOop(JNIHandles::resolve_non_null(array)); \
    // 3783   if (start < 0 || len < 0 || ((unsigned int)start + (unsigned int)len > (unsigned int)src->length())) { \
    // 3784     THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException()); \
    // 3785   } else { \
    // 3786     if (len > 0) { \
    // 3787       int sc = TypeArrayKlass::cast(src->klass())->log2_element_size(); \
    // 3788       memcpy((u_char*) buf, \
    // 3789              (u_char*) src->Tag##_at_addr(start), \
    // 3790              len << sc);                          \
    // 3791     } \
    // 3792   } \
    // 3793 JNI_END

    static void GetByteArrayRegion(byte[] array, int start, int len, CCharPointer buf) {
        if ((start < 0) || (len < 0) || ((((long) start + (long) len)) > array.length)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (len > 0) {
            try (PinnedObject pin = PinnedObject.create(array)) {
                LibC.memcpy(buf, pin.addressOfArrayElement(start), WordFactory.unsigned(len));
            }
        }
    }

    // 3856 #ifndef USDT2
    // 3857 #define DEFINE_SETSCALARARRAYREGION(ElementTag,ElementType,Result, Tag) \
    // 3858   DT_VOID_RETURN_MARK_DECL(Set##Result##ArrayRegion);\
    // 3859 \
    // 3860 JNI_ENTRY(void, \
    // 3861 jni_Set##Result##ArrayRegion(JNIEnv *env, ElementType##Array array, jsize start, \
    // 3862              jsize len, const ElementType *buf)) \
    // 3863   JNIWrapper("Set" XSTR(Result) "ArrayRegion"); \
    // 3864   DTRACE_PROBE5(hotspot_jni, Set##Result##ArrayRegion__entry, env, array, start, len, buf);\
    // 3865   DT_VOID_RETURN_MARK(Set##Result##ArrayRegion); \
    // 3866   typeArrayOop dst = typeArrayOop(JNIHandles::resolve_non_null(array)); \
    // 3867   if (start < 0 || len < 0 || ((unsigned int)start + (unsigned int)len > (unsigned int)dst->length())) { \
    // 3868     THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException()); \
    // 3869   } else { \
    // 3870     if (len > 0) { \
    // 3871       int sc = TypeArrayKlass::cast(dst->klass())->log2_element_size(); \
    // 3872       memcpy((u_char*) dst->Tag##_at_addr(start), \
    // 3873              (u_char*) buf, \
    // 3874              len << sc);    \
    // 3875     } \
    // 3876   } \
    // 3877 JNI_END

    static void SetByteArrayRegion(byte[] array, int start, int len, CCharPointer buf) {
        if ((start < 0) || (len < 0) || ((((long) start + (long) len)) > array.length)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (len > 0) {
            try (PinnedObject pin = PinnedObject.create(array)) {
                LibC.memcpy(pin.addressOfArrayElement(start), buf, WordFactory.unsigned(len));
            }
        }
    }

    /* @formater:on */
}

/** Translations of methods from src/os/bsd/vm/os_bsd.inline.hpp or src/os/bsd/vm/os_bsd.cpp. */
// TODO: Maybe this should be Target_bsd_vm_os?
class Target_os {
    /* Do not re-format commented-out code: @formatter:off */

    /* Private constructor: No instances. */
    private Target_os() {
    }

    // 198 inline int os::timeout(int fd, long timeout) {
    static int timeout(int fd, long timeoutArg) {
        /*
         * Local copy of argument because it is modified.
         * Also, convert from long to int to pass to Poll.poll.
         */
        int timeout = (int) timeoutArg;
        // 199   julong prevtime,newtime;
        /* Using System.currentTimeMillis() instead of gettimeofday(struct timeval*) */
        long prevtime;
        long newtime;
        // 200   struct timeval t;
        // 201
        // 202   gettimeofday(&t, NULL);
        // 203   prevtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;
        prevtime = System.currentTimeMillis();
        // 204
        // 205   for(;;) {
        // 206     struct pollfd pfd;
        Poll.pollfd pfd = StackValue.get(Poll.pollfd.class);
        for (;;) {
            // 207
            // 208     pfd.fd = fd;
            pfd.set_fd(fd);
            // 209     pfd.events = POLLIN | POLLERR;
            pfd.set_events(Poll.POLLIN() | Poll.POLLERR());
            // 210
            // 211     int res = ::poll(&pfd, 1, timeout);
            /* { FIXME: Limited timeout. */
            int res;
            final boolean limitedTimeout = false;
            if (limitedTimeout) {
                /* This is a compromise between frequent poll requests and promptness of interruption. */
                final int limitedTimeoutMillis = 2_000;
                res = Poll.poll(pfd, 1, limitedTimeoutMillis);
                if (Thread.interrupted()) {
                    return VmRuntimeOS.OSReturn.OS_OK();
                }
            } else {
                res = Poll.poll(pfd, 1, timeout);
            }
            /* } FIXME: Limited timeout. */
            // 212
            // 213     if (res == OS_ERR && errno == EINTR) {
            if (res == VmRuntimeOS.OSReturn.OS_ERR() && Errno.errno() == Errno.EINTR()) {
                // 214
                // 215       // On Bsd any value < 0 means "forever"
                // 216
                // 217       if(timeout >= 0) {
                if (timeout >= 0) {
                    // 218         gettimeofday(&t, NULL);
                    // 219         newtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;
                    newtime = System.currentTimeMillis();
                    // 220         timeout -= newtime - prevtime;
                    timeout -= newtime - prevtime;
                    // 221         if(timeout <= 0)
                    if (timeout <= 0) {
                        // 222           return OS_OK;
                        return VmRuntimeOS.OSReturn.OS_OK();
                    }
                    // 223         prevtime = newtime;
                    prevtime = newtime;
                }
            } else {
                // 226       return res;
                return res;
            }
        }
    }

    // 149 #define RESTARTABLE(_cmd, _result) do { \
    // 150     _result = _cmd; \
    // 151   } while(((int)_result == OS_ERR) && (errno == EINTR))

    // 153 #define RESTARTABLE_RETURN_INT(_cmd) do { \
    // 154   int _result; \
    // 155   RESTARTABLE(_cmd, _result); \
    // 156   return _result; \
    // 157 } while(false)

    // 162 inline size_t os::restartable_read(int fd, void *buf, unsigned int nBytes) {
    /* FIXME: What is the translation of size_t? */
    static long restartable_read(int fd, CCharPointer buf, int nBytes) {
        // 163   size_t res;
        long res = 0;
        // 164   RESTARTABLE( (size_t) ::read(fd, buf, (size_t) nBytes), res);
        do {
            res = Unistd.read(fd, buf, WordFactory.unsigned(nBytes)).rawValue();
        } while ((res == VmRuntimeOS.OSReturn.OS_ERR()) || (Errno.errno() == Errno.EINTR()));
        // 165   return res;
        return res;
    }

    // 4089 int os::socket_available(int fd, jint *pbytes) {
    static int socket_available(int fd, CIntPointer pbytes) {
        // 4090 if (fd < 0)
        if (fd < 0) {
            // 4091 return OS_OK;
            return VmRuntimeOS.OSReturn.OS_OK();
        }
        // 4093 int ret;
        int ret;
        // 4095 RESTARTABLE(::ioctl(fd, FIONREAD, pbytes), ret);
        do {
            ret = Ioctl.ioctl(fd, Ioctl.FIONREAD(), pbytes);
        } while ((ret == VmRuntimeOS.OSReturn.OS_ERR()) || (Errno.errno() == Errno.EINTR()));
        // 4096
        // 4097 //%% note ioctl can return 0 when successful, JVM_SocketAvailable
        // 4098 // is expected to return 0 on failure and 1 on success to the jdk.
        // 4099
        // 4100 return (ret == OS_ERR) ? 0 : 1;
        return ((ret == VmRuntimeOS.OSReturn.OS_ERR()) ? 0 : 1);
    }

    // 190 inline int os::send(int fd, char* buf, size_t nBytes, uint flags) {
    static int send(int fd, CCharPointer buf, long nBytes, int flags) {
        // 191   RESTARTABLE_RETURN_INT(::send(fd, buf, nBytes, flags));
        do {
            int _result;
            do {
                _result = (int) Socket.send(fd, buf, WordFactory.unsigned(nBytes), flags).rawValue();
            } while ((_result == VmRuntimeOS.OSReturn.OS_ERR()) || (Errno.errno() == Errno.EINTR()));
            return _result;
        } while (false);
    }

    // 248 inline int os::sendto(int fd, char* buf, size_t len, uint flags, struct sockaddr *to, socklen_t tolen) {
    static int sendto(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, int addr_len) {
        // 250   RESTARTABLE_RETURN_INT((int)::sendto(fd, buf, len, flags, to, tolen));
        do {
            int _result;
            do {
                _result = (int) Socket.sendto(fd, buf, WordFactory.unsigned(n), flags, addr, addr_len).rawValue();
            } while ((_result == VmRuntimeOS.OSReturn.OS_ERR()) || (Errno.errno() == Errno.EINTR()));
            return _result;
        } while (false);
    }

    // 243 inline int os::recvfrom(int fd, char* buf, size_t nBytes, uint flags, sockaddr* from, socklen_t* fromlen) {
    static int recvfrom(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, CIntPointer addr_len) {
        // 245   RESTARTABLE_RETURN_INT((int)::recvfrom(fd, buf, nBytes, flags, from, fromlen));
        do {
            int _result;
            do {
                _result = (int) Socket.recvfrom(fd, buf, WordFactory.unsigned(n), flags, addr, addr_len).rawValue();
            } while ((_result == VmRuntimeOS.OSReturn.OS_ERR()) || (Errno.errno() == Errno.EINTR()));
            return _result;
        } while (false);
    }

    //     178 inline int os::socket_close(int fd) {
    static int socket_close(int fd) {
        //         179   return ::close(fd);
        return Unistd.close(fd);
    }

    // 261 inline int os::get_sock_name(int fd, struct sockaddr* him, socklen_t* len) {
    static int get_sock_name(int fd, Socket.sockaddr him, CIntPointer len_Pointer) {
        // 262   return ::getsockname(fd, him, len);
        return Socket.getsockname(fd, him, len_Pointer);
    }

    // 265 inline int os::get_host_name(char* name, int namelen) {
    static int get_host_name(CCharPointer name, int namelen) {
        // 266   return ::gethostname(name, namelen);
        return Unistd.gethostname(name, WordFactory.unsigned(namelen));
    }

    // 226 inline int    os::listen(int fd, int count) {
    static int listen(int fd, int count) {
        // 227   if (fd < 0) return OS_ERR;
        if (fd < 0) {
            return VmRuntimeOS.OSReturn.OS_ERR();
        }
        // 229   return ::listen(fd, count);
        return Socket.listen(fd, count);
    }

    /* formatter:on */
}

/** Translations from src/share/vm/runtime/os.hpp. */
class VmRuntimeOS {

    /* Do not re-format commented-out code: @formatter:off */
    // 075 // Platform-independent error return values from OS functions
    // 076 enum OSReturn {
    // 077   OS_OK         =  0,        // Operation was successful
    // 078   OS_ERR        = -1,        // Operation failed
    // 079   OS_INTRPT     = -2,        // Operation was interrupted
    // 080   OS_TIMEOUT    = -3,        // Operation timed out
    // 081   OS_NOMEM      = -5,        // Operation failed for lack of memory
    // 082   OS_NORESOURCE = -6         // Operation failed for lack of nonmemory resource
    // 083 };
    /* @formatter:on */
    static class OSReturn {

        static int OS_OK() {
            return 0;
        }

        static int OS_ERR() {
            return -1;
        }

        static int OS_INTRPT() {
            return -2;
        }

        static int OS_TIMEOUT() {
            return -3;
        }

        static int OS_NOMEM() {
            return -5;
        }

        static int OS_NORESOURCE() {
            return -6;
        }
    }
}

/** Translations from src/share/javavm/export/jvm.h. */
class JavavmExportJvm {

    // 1100 #define JVM_IO_ERR (-1)
    static class JvmIoErrorCode {

        static int JVM_IO_ERR() {
            return -1;
        }

        // 1122 #define JVM_IO_INTR (-2)
        static int JVM_IO_INTR() {
            return -2;
        }

        // 1142 #define JVM_EEXIST -100
        static int JVM_EEXIST() {
            return -100;
        }
    }
}

// Checkstyle: resume
