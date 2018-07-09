/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.NetIf;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.PointerUtils;

/* { Do not format quoted code: @formatter:off */
/* { Allow non-standard names: Checkstyle: stop */

/*
 * jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10 has code
 * for Linux, Solaris, AIX, and BSD.  This file has the common parts.
 * The Linux parts are in LinuxNetworkInterface and the Darwin parts are
 * in DarwinNetworkInterface.
 */

// The #ifdef structure of NetworkInterface.c is
//
// 1078 /** Linux, AIX **/
// 1079 #if defined(__linux__) || defined(_AIX)
// 1505 #endif
// 1507 /** Solaris **/
// 1508 #ifdef __solaris__
// 1890 #endif
// 1893 /** BSD **/
// 1894 #ifdef _ALLBSD_SOURCE
// 2201 #endif
//
// So one can look at the quoted line numbers on the C code to see which platform
// the code applies to.

public class JavaNetNetworkInterface {

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10 to a
     * Java class.
     */
    // 083 typedef struct _netaddr  {
    public static class netaddr {
        // 084     struct sockaddr *addr;
        public Socket.sockaddr addr;
        // 085     struct sockaddr *brdcast;
        public Socket.sockaddr brdcast;
        // 086     short mask;
        public short mask;
        // 087     int family; /* to make searches simple */
        public int family;
        // 088     struct _netaddr *next;
        public netaddr next;
        // 089 } netaddr;

        /** Additional space allocated when the netaddr is allocated. */
        Socket.sockaddr addrSpace;
        Socket.sockaddr brdcastSpace;

        /** Copy the ordinary fields. */
        public netaddr copyFields(netaddr that) {
            this.addr = that.addr;
            this.brdcast = that.brdcast;
            this.mask = that.mask;
            this.family = that.family;
            this.next = that.next;
            return this;
        }

        /** Allocate a netaddr from the Java heap, and the two sockaddrs from the C heap. */
        public static netaddr checked_malloc(int addr_size) {
            final netaddr result = new netaddr();
            result.addrSpace = LibC.malloc(WordFactory.unsigned(addr_size));
            JavaNetNetworkInterface.checkMalloc(result.addrSpace);
            result.brdcastSpace = LibC.malloc(WordFactory.unsigned(addr_size));
            JavaNetNetworkInterface.checkMalloc(result.brdcastSpace);
            return result;
        }

        /** Free a netaddr. */
        public static void free(netaddr that) {
            LibC.free(that.addrSpace);
            LibC.free(that.brdcastSpace);
        }

        void toLog(Log log) {
            log.string("[").object(this)
            .newline().string("  .addr: ").hex(addr)
            .newline().string("  .brdcast: ").hex(brdcast)
            .newline().string("  .mask: ").signed(mask)
            .newline().string("  .family: ").signed(family)
            .newline().string("  .next: ").object(next)
            .newline().string("  .addrSpace: ").hex(addrSpace)
            .newline().string("  .brdcastSpace: ").hex(brdcastSpace)
            .string("]");
        }
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10 to a
     * Java class.
     */
    // 091 typedef struct _netif {
    public static class netif {
        // 092     char *name;
        CCharPointer name;
        // 093     int index;
        int index;
        // 094     char virtual;
        byte virtual;
        // 095     netaddr *addr;
        netaddr addr;
        // 096     struct _netif *childs;
        netif childs;
        // 097     struct _netif *next;
        netif next;
        // 098 } netif;

        public static netif checked_malloc(int nameSize) {
            final netif result = new netif();
            result.name = LibC.malloc(WordFactory.unsigned(nameSize));
            JavaNetNetworkInterface.checkMalloc(result.name);
            return result;
        }

        public static void free(netif that) {
            LibC.free(that.name);
        }

        public void toLog(Log log) {
            log.string("[").object(this)
            .newline().string("  .name: ").string(name)
            .newline().string("  .index: ").signed(index)
            .newline().string("  .virtual: ").signed(virtual)
            .newline().string("  .addr: ").object(addr)
            .newline().string("  .childs: ").object(childs)
            .newline().string("  .next: ").object(next)
            .string("]");
        }
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
     */
    // 630 /*
    // 631  * Create a NetworkInterface object, populate the name and index, and
    // 632  * populate the InetAddress array based on the IP addresses for this
    // 633  * interface.
    // 634  */
    // 635 jobject createNetworkInterface(JNIEnv *env, netif *ifs) {
    static NetworkInterface createNetworkInterface(netif ifs) {
        // 636     jobject netifObj;
        NetworkInterface netifObj;
        Target_java_net_NetworkInterface netif_TJNNI;
        // 637     jobject name;
        String name;
        // 638     jobjectArray addrArr;
        InetAddress[] addrArr;
        // 639     jobjectArray bindArr;
        InterfaceAddress[] bindArr;
        // 640     jobjectArray childArr;
        NetworkInterface[] childArr;
        // 641     netaddr *addrs;
        /* `addrs` is unused. */
        // 642     jint addr_index, addr_count, bind_index;
        int addr_index;
        int addr_count;
        int bind_index;
        // 643     jint child_count, child_index;
        int child_count;
        int child_index;
        // 644     netaddr *addrP;
        netaddr addrP;
        // 645     netif *childP;
        netif childP;
        // 646     jobject tmp;
        NetworkInterface tmp;
        // 647
        // 648     /*
        // 649      * Create a NetworkInterface object and populate it
        // 650      */
        // 651     netifObj = (*env)->NewObject(env, ni_class, ni_ctrID);
        netif_TJNNI = new Target_java_net_NetworkInterface();
        netifObj = Util_java_net_NetworkInterface.toNetworkInterface(netif_TJNNI);
        // 652     CHECK_NULL_RETURN(netifObj, NULL);
        // 653     name = (*env)->NewStringUTF(env, ifs->name);
        name = CTypeConversion.toJavaString(ifs.name);
        // 654     CHECK_NULL_RETURN(name, NULL);
        // 655     (*env)->SetObjectField(env, netifObj, ni_nameID, name);
        netif_TJNNI.name = name;
        // 656     (*env)->SetObjectField(env, netifObj, ni_descID, name);
        netif_TJNNI.displayName = name;
        // 657     (*env)->SetIntField(env, netifObj, ni_indexID, ifs->index);
        netif_TJNNI.index = ifs.index;
        // 658     (*env)->SetBooleanField(env, netifObj, ni_virutalID, ifs->virtual ? JNI_TRUE : JNI_FALSE);
        netif_TJNNI.virtual = CTypeConversion.toBoolean(ifs.virtual);
        // 659
        // 660     /*
        // 661      * Count the number of address on this interface
        // 662      */
        // 663     addr_count = 0;
        addr_count = 0;
        // 664     addrP = ifs->addr;
        addrP = ifs.addr;
        // 665     while (addrP != NULL) {
        while (addrP != null) {
            // 666         addr_count++;
            addr_count++;
            // 667         addrP = addrP->next;
            addrP = addrP.next;
        }
        // 669
        // 670     /*
        // 671      * Create the array of InetAddresses
        // 672      */
        // 673     addrArr = (*env)->NewObjectArray(env, addr_count,  ni_iacls, NULL);
        addrArr = new InetAddress[addr_count];
        /* `new` never returns null. */
        // 674     if (addrArr == NULL) {
        // 675         return NULL;
        // 676     }
        // 677
        // 678     bindArr = (*env)->NewObjectArray(env, addr_count, ni_ibcls, NULL);
        bindArr = new InterfaceAddress[addr_count];
        /* `new` never returns null. */
        // 679     if (bindArr == NULL) {
        // 680        return NULL;
        // 681     }
        // 682     addrP = ifs->addr;
        addrP = ifs.addr;
        // 683     addr_index = 0;
        addr_index = 0;
        // 684     bind_index = 0;
        bind_index = 0;
        // 685     while (addrP != NULL) {
        while (addrP != null) {
            // 686         jobject iaObj = NULL;
            Object iaObj = null;
            // 687         jobject ibObj = NULL;
            Object ibObj = null;
            // 688
            // 689         if (addrP->family == AF_INET) {
            if (addrP.family == Socket.AF_INET()) {
                // 690             iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
                final Inet4Address ia_I4A = Util_java_net_Inet4Address.new_Inet4Address();
                iaObj = ia_I4A;
                // 691             if (iaObj) {
                if (iaObj != null) {
                    // 692                  setInetAddress_addr(env, iaObj, htonl(((struct sockaddr_in*)addrP->addr)->sin_addr.s_addr));
                    JavaNetNetUtil.setInetAddress_addr(ia_I4A, NetinetIn.htonl(((NetinetIn.sockaddr_in) addrP.addr).sin_addr().s_addr()));
                } else {
                    // 694                 return NULL;
                    return null;
                }
                // 696             ibObj = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
                final Target_java_net_InterfaceAddress ib_TJNIA = new Target_java_net_InterfaceAddress();
                final InterfaceAddress ib_IA = Util_java_net_InterfaceAddress.toInterfaceAddress(ib_TJNIA);
                ibObj = ib_IA;
                // 697             if (ibObj) {
                if (ibObj != null) {
                    // 698                  (*env)->SetObjectField(env, ibObj, ni_ibaddressID, iaObj);
                    ib_TJNIA.address = ia_I4A;
                    // 699                  if (addrP->brdcast) {
                    if (CTypeConversion.toBoolean(addrP.brdcast)) {
                        // 700                     jobject ia2Obj = NULL;
                        Inet4Address ia2Obj = null;
                        // 701                     ia2Obj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
                        ia2Obj = Util_java_net_Inet4Address.new_Inet4Address();
                        /* `new` never returns null. */
                        // 702                     if (ia2Obj) {
                        // 703                        setInetAddress_addr(env, ia2Obj, htonl(((struct sockaddr_in*)addrP->brdcast)->sin_addr.s_addr));
                        JavaNetNetUtil.setInetAddress_addr(ia2Obj, NetinetIn.htonl(((NetinetIn.sockaddr_in) addrP.brdcast).sin_addr().s_addr()));
                        // 704                        (*env)->SetObjectField(env, ibObj, ni_ib4broadcastID, ia2Obj);
                        ib_TJNIA.broadcast = ia2Obj;
                        // 705                     } else {
                        // 706                         return NULL;
                        // 707                     }
                    }
                    // 709                  (*env)->SetShortField(env, ibObj, ni_ib4maskID, addrP->mask);
                    ib_TJNIA.maskLength = addrP.mask;
                    // 710                  (*env)->SetObjectArrayElement(env, bindArr, bind_index++, ibObj);
                    bindArr[bind_index++] = ib_IA;
                } else {
                    // 712                 return NULL;
                    return null;
                }
            }
            // 715
            // 716 #ifdef AF_INET6
            if (IsDefined.socket_AF_INET6()) {
                // 717         if (addrP->family == AF_INET6) {
                if (addrP.family == Socket.AF_INET6()) {
                    // 718             int scope=0;
                    int scope = 0;
                    // 719             iaObj = (*env)->NewObject(env, ni_ia6cls, ni_ia6ctrID);
                    final Inet6Address ia_I6A = Util_java_net_Inet6Address.new_Inet6Address();
                    iaObj = ia_I6A;
                    // 720             if (iaObj) {
                    if (iaObj != null) {
                        // 721                 int ret = setInet6Address_ipaddress(env, iaObj, (char *)&(((struct sockaddr_in6*)addrP->addr)->sin6_addr));
                        final CCharPointer address = (CCharPointer) ((NetinetIn.sockaddr_in6) addrP.addr).sin6_addr();
                        int ret = JavaNetNetUtil.setInet6Address_ipaddress(ia_I6A, address);
                        // 722                 if (ret == JNI_FALSE) {
                        if (ret == Target_jni.JNI_FALSE()) {
                            // 723                     return NULL;
                            return null;
                        }
                        // 725
                        // 726                 scope = ((struct sockaddr_in6*)addrP->addr)->sin6_scope_id;
                        scope = ((NetinetIn.sockaddr_in6) addrP.addr).sin6_scope_id();
                        // 727
                        // 728                 if (scope != 0) { /* zero is default value, no need to set */
                        if (scope != 0) {
                            // 729                     setInet6Address_scopeid(env, iaObj, scope);
                            JavaNetNetUtil.setInet6Address_scopeid(ia_I6A, scope);
                            // 730                     setInet6Address_scopeifname(env, iaObj, netifObj);
                            JavaNetNetUtil.setInet6Address_scopeifname(ia_I6A, netifObj);
                        }
                    } else {
                        // 733                 return NULL;
                        return null;
                    }
                    // 735             ibObj = (*env)->NewObject(env, ni_ibcls, ni_ibctrID);
                    Target_java_net_InterfaceAddress ib_TJNIA = new Target_java_net_InterfaceAddress();
                    InterfaceAddress ib_IA = Util_java_net_InterfaceAddress.toInterfaceAddress(ib_TJNIA);
                    ibObj = ib_IA;
                    // 736             if (ibObj) {
                    if (ibObj != null) {
                        // 737                 (*env)->SetObjectField(env, ibObj, ni_ibaddressID, iaObj);
                        ib_TJNIA.address = (InetAddress) iaObj;
                        // 738                 (*env)->SetShortField(env, ibObj, ni_ib4maskID, addrP->mask);
                        ib_TJNIA.maskLength = addrP.mask;
                        // 739                 (*env)->SetObjectArrayElement(env, bindArr, bind_index++, ibObj);
                        bindArr[bind_index++] = ib_IA;
                    } else {
                        // 741                 return NULL;
                        return null;
                    }
                }
            }
            // 744 #endif
            // 745
            // 746         (*env)->SetObjectArrayElement(env, addrArr, addr_index++, iaObj);
            addrArr[addr_index++] = (InetAddress) iaObj;
            // 747         addrP = addrP->next;
            addrP = addrP.next;
        }
        // 749
        // 750     /*
        // 751      * See if there is any virtual interface attached to this one.
        // 752      */
        // 753     child_count = 0;
        child_count = 0;
        // 754     childP = ifs->childs;
        childP = ifs.childs;
        // 755     while (childP) {
        while (childP != null) {
            // 756         child_count++;
            child_count++;
            // 757         childP = childP->next;
            childP = childP.next;
        }
        // 759
        // 760     childArr = (*env)->NewObjectArray(env, child_count, ni_class, NULL);
        childArr = new NetworkInterface[child_count];
        // 761     if (childArr == NULL) {
        /* Dead code. */
        // 762         return NULL;
        // 763     }
        // 764
        // 765     /*
        // 766      * Create the NetworkInterface instances for the sub-interfaces as
        // 767      * well.
        // 768      */
        // 769     child_index = 0;
        child_index = 0;
        // 770     childP = ifs->childs;
        childP = ifs.childs;
        // 771     while(childP) {
        while (childP != null) {
            // 772       tmp = createNetworkInterface(env, childP);
            tmp = createNetworkInterface(childP);
            // 773       if (tmp == NULL) {
            if (tmp == null) {
                // 774          return NULL;
                return null;
            }
            // 776       (*env)->SetObjectField(env, tmp, ni_parentID, netifObj);
            Util_java_net_NetworkInterface.fromNetworkInterface(tmp).parent = netifObj;
            // 777       (*env)->SetObjectArrayElement(env, childArr, child_index++, tmp);
            childArr[child_index++] = tmp;
            // 778       childP = childP->next;
            childP = childP.next;
        }
        // 780     (*env)->SetObjectField(env, netifObj, ni_addrsID, addrArr);
        netif_TJNNI.addrs = addrArr;
        // 781     (*env)->SetObjectField(env, netifObj, ni_bindsID, bindArr);
        netif_TJNNI.bindings = bindArr;
        // 782     (*env)->SetObjectField(env, netifObj, ni_childsID, childArr);
        netif_TJNNI.childs = childArr;
        // 783
        // 784     /* return the NetworkInterface */
        // 785     return netifObj;
        return netifObj;
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
     */
    // 788 /*
    // 789  * Enumerates all interfaces
    // 790  */
    // 791 static netif *enumInterfaces(JNIEnv *env) {
    public static netif enumInterfaces() {
        /* Prepare for platform-specific code. */
        final PlatformSupport platformSupport = ImageSingletons.lookup(JavaNetNetworkInterface.PlatformSupport.class);
        // 792     netif *ifs;
        netif ifs;
        // 793     int sock;
        int sock;
        // 794
        // 795     /*
        // 796      * Enumerate IPv4 addresses
        // 797      */
        // 798
        // 799     sock = openSocket(env, AF_INET);
        try {
            sock = openSocket(Socket.AF_INET());
            // 800     if (sock < 0 && (*env)->ExceptionOccurred(env)) {
            if (sock < 0) {
                // 801         return NULL;
                return null;
            }
        } catch (SocketException eo) {
            return null;
        }
        // 803
        try {
            // 804     ifs = enumIPv4Interfaces(env, sock, NULL);
            ifs = platformSupport.enumIPv4Interfaces(sock, null);
            // 805     close(sock);
            Unistd.close(sock);
            // 806
            // 807     if (ifs == NULL && (*env)->ExceptionOccurred(env)) {
            if (ifs == null) {
                // 808         return NULL;
                return null;
            }
        } catch (SocketException se) {
            return null;
        }
        // 810
        // 811     /* return partial list if an exception occurs in the middle of process ???*/
        // 812
        // 813     /*
        // 814      * If IPv6 is available then enumerate IPv6 addresses.
        // 815      */
        // 816 #ifdef AF_INET6
        /* Socket.AF_INET6 is always defined to have some value on our platforms. */
        // 817
        // 818         /* User can disable ipv6 explicitly by -Djava.net.preferIPv4Stack=true,
        // 819          * so we have to call ipv6_available()
        // 820          */
        // 821         if (ipv6_available()) {
        if (JavaNetNetUtil.ipv6_available()) {
            // 822
            try {
                // 823            sock =  openSocket(env, AF_INET6);
                sock = openSocket(Socket.AF_INET6());
                // 824            if (sock < 0 && (*env)->ExceptionOccurred(env)) {
                if (sock < 0) {
                    /* `ifs` is heap-allocated. */
                    // 825                freeif(ifs);
                    // 826                return NULL;
                    return null;
                }
            } catch (SocketException so) {
                return null;
            }
            // 828
            try {
                // 829            ifs = enumIPv6Interfaces(env, sock, ifs);
                ifs = platformSupport.enumIPv6Interfaces(sock, ifs);
                // 830            close(sock);
                Unistd.close(sock);
                // 831
            } catch (SocketException se) {
                // 832            if ((*env)->ExceptionOccurred(env)) {
                // 833               freeif(ifs);
                /* `ifs` is heap-allocated. */
                // 834               return NULL;
                return null;
            }
            // 836
        }
        // 838 #endif
        // 839
        // 840     return ifs;
        return ifs;
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
     */
    // 843 #define CHECKED_MALLOC3(_pointer,_type,_size) \
    // 844         do{ \
    // 845             _pointer = (_type)malloc( _size ); \
    // 846             if (_pointer == NULL) { \
    // 847                 JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed"); \
    // 848                 return ifs; /* return untouched list */ \
    // 849             } \
    // 850         } while(0)
    /** Just translating the null check. This *does not* return the untouched `ifs` in the case of failure. */
    public static void checkMalloc(PointerBase pointer) {
        if (pointer.isNull()) {
            throw new OutOfMemoryError("Native heap allocation failed");
        }
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
     */
    // 853 /*
    // 854  * Free an interface list (including any attached addresses)
    // 855  */
    // 856 void freeif(netif *ifs) {
    /* This is somewhat different than the original, because `netif` and `netaddr` are heap-allocated Java objects,
     * but I still have to free any C data that was allocated with the Java objects.
     */
    public static void freeif(netif ifsParameter) {
        /* Work around "The parameter ifs should not be assigned" warning. */
        netif ifs = ifsParameter;
        // 857     netif *currif = ifs;
        netif currif = ifs;
        // 858     netif *child = NULL;
        /* `child` is unused. */
        // 859
        // 860     while (currif != NULL) {
        while (currif != null) {
            // 861         netaddr *addrP = currif->addr;
            netaddr addrP = currif.addr;
            // 862         while (addrP != NULL) {
            while (addrP != null) {
                // 863             netaddr *next = addrP->next;
                netaddr next = addrP.next;
                // 864             free(addrP);
                netaddr.free(addrP);
                // 865             addrP = next;
                addrP = next;
            }
            // 867
            // 868             /*
            // 869              * Don't forget to free the sub-interfaces.
            // 870              */
            // 871           if (currif->childs != NULL) {
            if (currif.childs != null) {
                // 872                 freeif(currif->childs);
                freeif(currif.childs);
            }
            // 874
            // 875           ifs = currif->next;
            ifs = currif.next;
            // 876           free(currif);
            netif.free(currif);
            // 877           currif = ifs;
            currif = ifs;
        }
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
     */
    // 881 netif *addif(JNIEnv *env, int sock, const char * if_name,
    // 882              netif *ifs, struct sockaddr* ifr_addrP, int family,
    // 883              short prefix)
    // 884 {
    public static netif addif(int             sock,
                              CCharPointer    if_name,
                              netif           ifsParameter,
                              Socket.sockaddr ifr_addrP,
                              int             family,
                              short           prefix) {
        /* Work around "The parameter ifs should not be assigned" warning. */
        netif ifs = ifsParameter;
        /* Prepare for platform-specific code. */
        final PlatformSupport platformSupport = ImageSingletons.lookup(JavaNetNetworkInterface.PlatformSupport.class);
        // 885     netif *currif = ifs, *parent;
        netif currif;
        currif = ifs;
        netif parent;
        // 886     netaddr *addrP;
        netaddr addrP;
        // 887
        // 888 #ifdef LIFNAMSIZ
        /* LIFNAMSIZ is not defined on Linux or Darwin. */
        // 889     int ifnam_size = LIFNAMSIZ;
        // 890     char name[LIFNAMSIZ], vname[LIFNAMSIZ];
        // 891 #else
        // 892     int ifnam_size = IFNAMSIZ;
        int ifnam_size = NetIf.IFNAMSIZ();
        // 893     char name[IFNAMSIZ], vname[IFNAMSIZ];
        CCharPointer name = StackValue.get(NetIf.IFNAMSIZ(), CCharPointer.class);
        CCharPointer vname = StackValue.get(NetIf.IFNAMSIZ(), CCharPointer.class);
        // 894 #endif
        // 895
        // 896     char  *name_colonP;
        CCharPointer name_colonP;
        // 897     int mask;
        int mask;
        // 898     int isVirtual = 0;
        int isVirtual = 0;
        // 899     int addr_size;
        int addr_size;
        // 900     int flags = 0;
        CIntPointer flags_Pointer = StackValue.get(CIntPointer.class);
        flags_Pointer.write(0);
        // 901
        // 902     /*
        // 903      * If the interface name is a logical interface then we
        // 904      * remove the unit number so that we have the physical
        // 905      * interface (eg: hme0:1 -> hme0). NetworkInterface
        // 906      * currently doesn't have any concept of physical vs.
        // 907      * logical interfaces.
        // 908      */
        // 909     strncpy(name, if_name, ifnam_size);
        LibC.strncpy(name, if_name, WordFactory.unsigned(ifnam_size));
        // 910     name[ifnam_size - 1] = '\0';
        name.write(ifnam_size - 1, (byte) 0);
        // 911     *vname = 0;
        vname.write(0, (byte) 0);
        // 912
        // 913     /*
        // 914      * Create and populate the netaddr node. If allocation fails
        // 915      * return an un-updated list.
        // 916      */
        // 917     /*Allocate for addr and brdcast at once*/
        // 918
        // 919 #ifdef AF_INET6
        if (IsDefined.socket_AF_INET6()) {
            // 920     addr_size = (family == AF_INET) ? sizeof(struct sockaddr_in) : sizeof(struct sockaddr_in6);
            addr_size = (family == Socket.AF_INET()) ? SizeOf.get(NetinetIn.sockaddr_in.class) : SizeOf.get(NetinetIn.sockaddr_in6.class);
            // 921 #else
        } else {
            // 922     addr_size = sizeof(struct sockaddr_in);
            addr_size = SizeOf.get(NetinetIn.sockaddr_in.class);
        }
        // 923 #endif
        // 924
        // 925     CHECKED_MALLOC3(addrP, netaddr *, sizeof(netaddr)+2*addr_size);
        addrP = netaddr.checked_malloc(addr_size);
        // 926     addrP->addr = (struct sockaddr *)( (char *) addrP+sizeof(netaddr) );
        addrP.addr = addrP.addrSpace;
        // 927     memcpy(addrP->addr, ifr_addrP, addr_size);
        LibC.memcpy(addrP.addr, ifr_addrP, WordFactory.unsigned(addr_size));
        // 928
        // 929     addrP->family = family;
        addrP.family = family;
        // 930     addrP->brdcast = NULL;
        addrP.brdcast = WordFactory.nullPointer();
        // 931     addrP->mask = prefix;
        addrP.mask = prefix;
        // 932     addrP->next = 0;
        addrP.next = null;
        // 933     if (family == AF_INET) {
        if (family == Socket.AF_INET()) {
            // 934        // Deal with broadcast addr & subnet mask
            // 935        struct sockaddr * brdcast_to = (struct sockaddr *) ((char *) addrP + sizeof(netaddr) + addr_size);
            Socket.sockaddr brdcast_to = addrP.brdcastSpace;
            // 936        addrP->brdcast = getBroadcast(env, sock, name,  brdcast_to );
            try {
                addrP.brdcast = platformSupport.getBroadcast(sock, name, brdcast_to);
                // 937        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
            } catch (SocketException se) {
                // 938            return ifs;
                return ifs;
            }
            // 940        if ((mask = getSubnet(env, sock, name)) != -1) {
            try {
                if ((mask = platformSupport.getSubnet(sock, name)) != -1) {
                    // 941            addrP->mask = mask;
                    addrP.mask = (short) mask;
                    // 942        } else if((*env)->ExceptionCheck(env)) {
                }
            } catch (SocketException se) {
                // 943            return ifs;
                return ifs;
            }
        }
        // 946
        // 947     /**
        // 948      * Deal with virtual interface with colon notation e.g. eth0:1
        // 949      */
        // 950     name_colonP = strchr(name, ':');
        name_colonP = LibC.strchr(name, ':');
        // 951     if (name_colonP != NULL) {
        if (name_colonP.isNonNull()) {
            // 952       /**
            // 953        * This is a virtual interface. If we are able to access the parent
            // 954        * we need to create a new entry if it doesn't exist yet *and* update
            // 955        * the 'parent' interface with the new records.
            // 956        */
            // 957         *name_colonP = 0;
            name_colonP.write((byte) 0);
            // 958         if (getFlags(sock, name, &flags) < 0 || flags < 0) {
            if (platformSupport.getFlags(sock, name, flags_Pointer) < 0 | flags_Pointer.read() < 0) {
                // 959             // failed to access parent interface do not create parent.
                // 960             // We are a virtual interface with no parent.
                // 961             isVirtual = 1;
                isVirtual = 1;
                // 962             *name_colonP = ':';
                name_colonP.write((byte) ':');
            } else{
                // 965            // Got access to parent, so create it if necessary.
                // 966            // Save original name to vname and truncate name by ':'
                // 967             memcpy(vname, name, sizeof(vname) );
                LibC.memcpy(vname, name, WordFactory.unsigned(ifnam_size));
                // 968             vname[name_colonP - name] = ':';
                vname.write((int) PointerUtils.absoluteDifference(name_colonP, name).rawValue(), (byte) ':');
            }
        }
        // 971
        // 972     /*
        // 973      * Check if this is a "new" interface. Use the interface
        // 974      * name for matching because index isn't supported on
        // 975      * Solaris 2.6 & 7.
        // 976      */
        // 977     while (currif != NULL) {
        while (currif != null) {
            // 978         if (strcmp(name, currif->name) == 0) {
            if (LibC.strcmp(name, currif.name) == 0) {
                // 979             break;
                break;
            }
            // 981         currif = currif->next;
            currif = currif.next;
        }
        // 983
        // 984     /*
        // 985      * If "new" then create an netif structure and
        // 986      * insert it onto the list.
        // 987      */
        // 988     if (currif == NULL) {
        if (currif == null) {
            // 989          CHECKED_MALLOC3(currif, netif *, sizeof(netif) + ifnam_size);
            currif = netif.checked_malloc(ifnam_size);
            // 990          currif->name = (char *) currif+sizeof(netif);
            /* Done as part of the checked_malloc. */
            // 991          strncpy(currif->name, name, ifnam_size);
            LibC.strncpy(currif.name, name, WordFactory.unsigned(ifnam_size));
            // 992          currif->name[ifnam_size - 1] = '\0';
            currif.name.write(ifnam_size - 1, (byte) 0);
            // 993          currif->index = getIndex(sock, name);
            currif.index = platformSupport.getIndex(sock, name);
            // 994          currif->addr = NULL;
            currif.addr = null;
            // 995          currif->childs = NULL;
            currif.addr = null;
            // 996          currif->virtual = isVirtual;
            currif.virtual = (byte) isVirtual;
            // 997          currif->next = ifs;
            currif.next = ifs;
            // 998          ifs = currif;
            ifs = currif;
        }
        // 1000
        // 1001     /*
        // 1002      * Finally insert the address on the interface
        // 1003      */
        // 1004     addrP->next = currif->addr;
        addrP.next = currif.addr;
        // 1005     currif->addr = addrP;
        currif.addr = addrP;
        // 1006
        // 1007     parent = currif;
        parent = currif;
        // 1008
        // 1009     /**
        // 1010      * Let's deal with the virtual interface now.
        // 1011      */
        // 1012     if (vname[0]) {
        if (CTypeConversion.toBoolean(vname.read())) {
            // 1013         netaddr *tmpaddr;
            netaddr tmpaddr;
            // 1014
            // 1015         currif = parent->childs;
            currif = parent.childs;
            // 1016
            // 1017         while (currif != NULL) {
            while (currif != null) {
                // 1018             if (strcmp(vname, currif->name) == 0) {
                if (LibC.strcmp(vname,  currif.name) == 0) {
                    // 1019                 break;
                    break;
                }
                // 1021             currif = currif->next;
                currif = currif.next;
            }
            // 1023
            // 1024         if (currif == NULL) {
            if (currif == null) {
                // 1025             CHECKED_MALLOC3(currif, netif *, sizeof(netif) + ifnam_size);
                /* Translating as two separate allocations, one Java one C. */
                currif = new netif();
                CCharPointer currifName = LibC.malloc(WordFactory.unsigned(ifnam_size));
                checkMalloc(currifName);
                // 1026             currif->name = (char *) currif + sizeof(netif);
                currif.name = currifName;
                // 1027             strncpy(currif->name, vname, ifnam_size);
                LibC.strncpy(currif.name, vname, WordFactory.unsigned(ifnam_size));
                // 1028             currif->name[ifnam_size - 1] = '\0';
                currif.name.write(ifnam_size - 1, (byte) 0);
                // 1029             currif->index = getIndex(sock, vname);
                currif.index = platformSupport.getIndex(sock, vname);
                // 1030             currif->addr = NULL;
                currif.addr = null;
                // 1031            /* Need to duplicate the addr entry? */
                // 1032             currif->virtual = 1;
                currif.virtual = (byte) 1;
                // 1033             currif->childs = NULL;
                currif.childs = null;
                // 1034             currif->next = parent->childs;
                currif.next = parent.childs;
                // 1035             parent->childs = currif;
                parent.childs = currif;
            }
            // 1037
            // 1038         CHECKED_MALLOC3(tmpaddr, netaddr *, sizeof(netaddr)+2*addr_size);
            /* Allocate a populated netaddr, and free and null the sockaddrs */
            tmpaddr = netaddr.checked_malloc(addr_size);
            // 1039         memcpy(tmpaddr, addrP, sizeof(netaddr));
            tmpaddr.copyFields(addrP);
            // 1040         if (addrP->addr != NULL) {
            if (addrP.addr.isNonNull()) {
                // 1041             tmpaddr->addr = (struct sockaddr *) ( (char*)tmpaddr + sizeof(netaddr) ) ;
                // 1042             memcpy(tmpaddr->addr, addrP->addr, addr_size);
                LibC.memcpy(tmpaddr.addr, addrP.addr, WordFactory.unsigned(addr_size));
            } else {
                LibC.free(tmpaddr.addr);
                tmpaddr.addr = WordFactory.nullPointer();
            }
            // 1044
            // 1045         if (addrP->brdcast != NULL) {
            if (addrP.brdcast.isNonNull()) {
                // 1046             tmpaddr->brdcast = (struct sockaddr *) ((char *) tmpaddr + sizeof(netaddr)+addr_size);
                // 1047             memcpy(tmpaddr->brdcast, addrP->brdcast, addr_size);
                LibC.memcpy(tmpaddr.brdcast, addrP.brdcast, WordFactory.unsigned(addr_size));
            } else {
                LibC.free(tmpaddr.brdcast);
                tmpaddr.brdcast = WordFactory.nullPointer();
            }
            // 1049
            // 1050         tmpaddr->next = currif->addr;
            tmpaddr.next = currif.addr;
            // 1051         currif->addr = tmpaddr;
            currif.addr = tmpaddr;
        }
        // 1053
        // 1054     return ifs;
        return ifs;
    }

    /*
     * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
     */
    // 1057 /* Open socket for further ioct calls
    // 1058  * proto is AF_INET/AF_INET6
    // 1059  */
    // 1060 static int  openSocket(JNIEnv *env, int proto){
    static int openSocket(int proto) throws SocketException {
        // 1061     int sock;
        int sock;
        // 1062
        // 1063     if ((sock = JVM_Socket(proto, SOCK_DGRAM, 0)) < 0) {
        if ((sock = VmPrimsJVM.JVM_Socket(proto, Socket.SOCK_DGRAM(), 0)) < 0) {
            // 1064         /*
            // 1065          * If EPROTONOSUPPORT is returned it means we don't have
            // 1066          * support  for this proto so don't throw an exception.
            // 1067          */
            // 1068         if (errno != EPROTONOSUPPORT) {
            if (Errno.errno() != Errno.EPROTONOSUPPORT()) {
                // 1069             NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "Socket creation failed");
                throw new SocketException(PosixUtils.lastErrorString("Socket creation failed"));
            }
            // 1071         return -1;
        }
        // 1073
        // 1074     return sock;
        return sock;
    }

    /**
     * Access to platform-dependent code.
     * Platforms register implementations of this interface in the VMConfiguration.
     */
    public interface PlatformSupport {
        netif enumIPv4Interfaces(int sock, netif ifs) throws SocketException;
        netif enumIPv6Interfaces(int sock, netif ifs) throws SocketException;
        Socket.sockaddr getBroadcast(int sock, CCharPointer ifname, Socket.sockaddr brdcast_store) throws SocketException;
        short getSubnet(int sock, CCharPointer ifname) throws SocketException;
        int getFlags(int sock, CCharPointer ifname, CIntPointer flags);
        int getIndex(int sock, CCharPointer name);
    }
}

/* } Allow non-standard names: Checkstyle: resume */
/* } Do not format quoted code: @formatter:on */
