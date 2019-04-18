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
package com.oracle.svm.core.posix.darwin;

import java.net.SocketException;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.posix.JavaNetNetworkInterface;
import com.oracle.svm.core.posix.JavaNetNetworkInterface.netif;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.posix.headers.Ifaddrs;
import com.oracle.svm.core.posix.headers.Ioctl;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.NetEthernet;
import com.oracle.svm.core.posix.headers.NetIf;
import com.oracle.svm.core.posix.headers.NetIfDl;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.darwin.DarwinNetinet6In6_var;

/* { Do not format quoted code: @formatter:off */
/* { Allow non-standard names: Checkstyle: stop */

@Platforms(Platform.DARWIN.class)
public class DarwinJavaNetNetworkInterface {

    /** Register the Darwin implementation. */
    @AutomaticFeature
    static class JavaNetNetworkInterfaceFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(JavaNetNetworkInterface.PlatformSupport.class, new PlatformSupportImpl());
        }
    }

    static class PlatformSupportImpl implements JavaNetNetworkInterface.PlatformSupport {

        /*
         * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
         */
        // 1926 /*
        // 1927  * Enumerates and returns all IPv4 interfaces
        // 1928  */
        // 1929 static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
        @Override
        public JavaNetNetworkInterface.netif enumIPv4Interfaces(int sock, JavaNetNetworkInterface.netif ifsParameter) throws SocketException {
            /*
             * Work around "The parameter ifs should not be assigned" waranings.
             * A down-side is that on errors I do not return a partially-built
             * chain of netif instances.
             */
            JavaNetNetworkInterface.netif ifs = ifsParameter;
            // 1930 struct ifaddrs *ifa, *origifa;
            Ifaddrs.ifaddrs ifa;
            Ifaddrs.ifaddrsPointer origifa_Pointer = StackValue.get(Ifaddrs.ifaddrsPointer.class);
            // 1931
            // 1932 if (getifaddrs(&origifa) != 0) {
            if (Ifaddrs.getifaddrs(origifa_Pointer) != 0) {
                // 1933 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                // 1934 "getifaddrs() function failed");
                throw new SocketException(PosixUtils.lastErrorString("getifaddrs() function failed"));
                // 1935 return ifs;
                /* Unreachable code. */
            }
            // 1937
            // 1938 for (ifa = origifa; ifa != NULL; ifa = ifa->ifa_next) {
            for (ifa = origifa_Pointer.read(); ifa.isNonNull(); ifa = ifa.ifa_next()) {
                // 1939
                // 1940 /*
                // 1941  * Skip non-AF_INET entries.
                // 1942  */
                // 1943 if (ifa->ifa_addr == NULL || ifa->ifa_addr->sa_family != AF_INET)
                if (ifa.ifa_addr().isNull() || (ifa.ifa_addr().sa_family() != Socket.AF_INET())) {
                    // 1944 continue;
                    continue;
                }
                // 1945
                // 1946 /*
                // 1947  * Add to the list.
                // 1948  */
                // 1949 ifs = addif(env, sock, ifa->ifa_name, ifs, ifa->ifa_addr, AF_INET, 0);
                try {
                    ifs = JavaNetNetworkInterface.addif(sock, ifa.ifa_name(), ifs, ifa.ifa_addr(), Socket.AF_INET(), (short) 0);
                    // 1950
                    // 1951 /*
                    // 1952  * If an exception occurred then free the list.
                    // 1953  */
                    // 1954 if ((*env)->ExceptionOccurred(env)) {
                } catch (Exception e) {
                    // 1955 freeifaddrs(origifa);
                    Ifaddrs.freeifaddrs(origifa_Pointer.read());
                    // 1956 freeif(ifs);
                    JavaNetNetworkInterface.freeif(ifs);
                    // 1957 return NULL;
                    return null;
                }
            }
            // 1960
            // 1961 /*
            // 1962  * Free socket and buffer
            // 1963  */
            // 1964 freeifaddrs(origifa);
            Ifaddrs.freeifaddrs(origifa_Pointer.read());
            // 1965 return ifs;
            return ifs;
        }

        /*
         * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
         */
        // 2000 /*
        // 2001  * Enumerates and returns all IPv6 interfaces on BSD
        // 2002  */
        // 2003 static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
        @Override
        public netif enumIPv6Interfaces(int sock, netif ifsParameter) throws SocketException {
            /* Work around "The parameter ifs should not be assigned" warning. */
            netif ifs = ifsParameter;
            // 2004     struct ifaddrs *ifa, *origifa;
            Ifaddrs.ifaddrs ifa;
            Ifaddrs.ifaddrsPointer origifa_Pointer = StackValue.get(Ifaddrs.ifaddrsPointer.class);
            // 2005     struct sockaddr_in6 *sin6;
            NetinetIn.sockaddr_in6 sin6;
            // 2006     struct in6_ifreq ifr6;
            DarwinNetinet6In6_var.in6_ifreq ifr6 = StackValue.get(DarwinNetinet6In6_var.in6_ifreq.class);
            // 2007
            // 2008     if (getifaddrs(&origifa) != 0) {
            if (Ifaddrs.getifaddrs(origifa_Pointer) != 0) {
                // 2009         NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                // 2010                          "getifaddrs() function failed");
                throw new SocketException(PosixUtils.lastErrorString("getifaddrs() function failed"));
                // 2011         return ifs;
                /* Unreachable code. */
            }
            // 2013
            // 2014     for (ifa = origifa; ifa != NULL; ifa = ifa->ifa_next) {
            for (ifa = origifa_Pointer.read(); ifa.isNonNull(); ifa = ifa.ifa_next()) {
                // 2015
                // 2016         /*
                // 2017          * Skip non-AF_INET6 entries.
                // 2018          */
                // 2019         if (ifa->ifa_addr == NULL || ifa->ifa_addr->sa_family != AF_INET6)
                if (ifa.ifa_addr().isNull() || ifa.ifa_addr().sa_family() != Socket.AF_INET6()) {
                    // 2020             continue;
                    continue;
                }
                // 2021
                // 2022         memset(&ifr6, 0, sizeof(ifr6));
                LibC.memset(ifr6, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(DarwinNetinet6In6_var.in6_ifreq.class)));
                // 2023         strlcpy(ifr6.ifr_name, ifa->ifa_name, sizeof(ifr6.ifr_name));
                LibC.strlcpy(ifr6.ifr_name(), ifa.ifa_name(), WordFactory.unsigned(NetIf.IF_NAMESIZE()));
                // 2024         memcpy(&ifr6.ifr_addr, ifa->ifa_addr, MIN(sizeof(ifr6.ifr_addr), ifa->ifa_addr->sa_len));
                final int minLength = Math.min(SizeOf.get(NetinetIn.sockaddr_in6.class), ifa.ifa_addr().sa_len());
                LibC.memcpy(ifr6.ifru_addr(), ifa.ifa_addr(), WordFactory.unsigned(minLength));
                // 2025
                // 2026         if (ioctl(sock, SIOCGIFNETMASK_IN6, (caddr_t)&ifr6) < 0) {
                if (Ioctl.ioctl(sock, DarwinNetinet6In6_var.SIOCGIFNETMASK_IN6(), ifr6) < 0) {
                    final int savedErrno = Errno.errno();
                    // 2027             NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException",
                    // 2028                              "ioctl SIOCGIFNETMASK_IN6 failed");
                    // 2029             freeifaddrs(origifa);
                    Ifaddrs.freeifaddrs(origifa_Pointer.read());
                    // 2030             freeif(ifs);
                    JavaNetNetworkInterface.freeif(ifs);
                    throw new SocketException(PosixUtils.errorString(savedErrno, "ioctl SIOCGIFNETMASK_IN6 failed"));
                    // 2031             return NULL;
                    /* Unreachable code. */
                }
                // 2033
                // 2034         /* Add to the list.  */
                // 2035         sin6 = (struct sockaddr_in6 *)&ifr6.ifr_addr;
                sin6 = ifr6.ifru_addr();
                try {
                    // 2036         ifs = addif(env, sock, ifa->ifa_name, ifs, ifa->ifa_addr, AF_INET6,
                    // 2037                     prefix(&sin6->sin6_addr, sizeof(struct in6_addr)));
                    final short prefix = (short) prefix(sin6.sin6_addr(), SizeOf.get(NetinetIn.in6_addr.class));
                    ifs = JavaNetNetworkInterface.addif(sock, ifa.ifa_name(), ifs, ifa.ifa_addr(), Socket.AF_INET6(), prefix);
                    // 2038
                    // 2039         /* If an exception occurred then free the list.  */
                    // 2040         if ((*env)->ExceptionOccurred(env)) {
                } catch (Exception e) {
                    // 2041             freeifaddrs(origifa);
                    Ifaddrs.freeifaddrs(origifa_Pointer.read());
                    // 2042             freeif(ifs);
                    JavaNetNetworkInterface.freeif(ifs);
                    // 2043             return NULL;
                    return null;
                }
            }
            // 2046
            // 2047     /*
            // 2048      * Free socket and ifaddrs buffer
            // 2049      */
            // 2050     freeifaddrs(origifa);
            Ifaddrs.freeifaddrs(origifa_Pointer.read());
            // 2051     return ifs;
            return ifs;
        }

        // 1974 /*
        // 1975  * Determines the prefix on BSD for IPv6 interfaces.
        // 1976  */
        // 1977 static
        // 1978 int prefix(void *val, int size) {
        /* Note: The C code uses many `for` and `if` statements without brackets. */
        public static int prefix(PointerBase val, int size) {
            // 1979     u_char *name = (u_char *)val;
            CCharPointer name = (CCharPointer) val;
            // 1980     int byte, bit, plen = 0;
            /* Note the misspelling of `byte` because it is a Java keyword. */
            int bite;
            int bit;
            int plen = 0;
            // 1981
            // 1982     for (byte = 0; byte < size; byte++, plen += 8)
            for (bite = 0; bite < size; bite++, plen += 8) {
                // 1983         if (name[byte] != 0xff)
                if (name.read(bite) != 0xff) {
                    // 1984             break;
                    break;
                }
            }
            // 1985     if (byte == size)
            if (bite == size) {
                // 1986         return (plen);
                return plen;
            }
            // 1987     for (bit = 7; bit != 0; bit--, plen++)
            for (bit = 7; bit != 0; bit--, plen++) {
                // 1988         if (!(name[byte] & (1 << bit)))
                // 1989             break;
            }
            // 1990     for (; bit != 0; bit--)
            for (; bit != 0; bit--) {
                // 1991         if (name[byte] & (1 << bit))
                if (CTypeConversion.toBoolean(name.read(bite) & (1 << bit))) {
                    // 1992             return (0);
                    return 0;
                }
            }
            // 1993     byte++;
            bite++;
            // 1994     for (; byte < size; byte++)
            for (; bite < size; bite++) {
                // 1995         if (name[byte])
                if (CTypeConversion.toBoolean(name.read(bite))) {
                    // 1996             return (0);
                    return 0;
                }
            }
            // 1997     return (plen);
            return plen;
        }

        @Override
        // 2078 /**
        // 2079  * Returns the IPv4 broadcast address of a named interface, if it exists.
        // 2080  * Returns 0 if it doesn't have one.
        // 2081  */
        // 2082 static struct sockaddr *getBroadcast(JNIEnv *env, int sock, const char *ifname, struct sockaddr *brdcast_store) {
        public Socket.sockaddr getBroadcast(int sock, CCharPointer ifname, Socket.sockaddr brdcast_store) throws SocketException {
            // 2083   struct sockaddr *ret = NULL;
            Socket.sockaddr ret = WordFactory.nullPointer();
            // 2084   struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            // 2085
            // 2086   memset((char *) &if2, 0, sizeof(if2));
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 2087   strcpy(if2.ifr_name, ifname);
            LibC.strcpy(if2.ifr_name(), ifname);
            // 2088
            // 2089   /* Let's make sure the interface does have a broadcast address */
            // 2090   if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0) {
            if (Ioctl.ioctl(sock, Socket.SIOCGIFFLAGS(), if2) < 0) {
                // 2091       NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFFLAGS failed");
                throw new SocketException(PosixUtils.lastErrorString("IOCTL SIOCGIFFLAGS failed"));
                // 2092       return ret;
                /* Unreachable code. */
            }
            // 2094
            // 2095   if (if2.ifr_flags & IFF_BROADCAST) {
            if (CTypeConversion.toBoolean(if2.ifr_flags() & NetIf.IFF_BROADCAST())) {
                // 2096       /* It does, let's retrieve it*/
                // 2097       if (ioctl(sock, SIOCGIFBRDADDR, (char *)&if2) < 0) {
                if (Ioctl.ioctl(sock,  Socket.SIOCGIFBRDADDR(), if2) < 0) {
                    // 2098           NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFBRDADDR failed");
                    throw new SocketException(PosixUtils.lastErrorString("IOCTL SIOCGIFBRDADDR failed"));
                    // 2099           return ret;
                    /* Unreachable code. */
                }
                // 2101
                // 2102       ret = brdcast_store;
                ret = brdcast_store;
                // 2103       memcpy(ret, &if2.ifr_broadaddr, sizeof(struct sockaddr));
                LibC.memcpy(ret, if2.ifr_broadaddr(), WordFactory.unsigned(SizeOf.get(Socket.sockaddr.class)));
            }
            // 2105
            // 2106   return ret;
            return ret;
        }

        @Override
        // 2109 /**
        // 2110  * Returns the IPv4 subnet prefix length (aka subnet mask) for the named
        // 2111  * interface, if it has one, otherwise return -1.
        // 2112  */
        // 2113 static short getSubnet(JNIEnv *env, int sock, const char *ifname) {
        public short getSubnet(int sock, CCharPointer ifname) throws SocketException {
            // 2114     unsigned int mask;
            int mask;
            // 2115     short ret;
            short ret;
            // 2116     struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            // 2117
            // 2118     memset((char *) &if2, 0, sizeof(if2));
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 2119     strcpy(if2.ifr_name, ifname);
            LibC.strcpy(if2.ifr_name(), ifname);
            // 2120
            // 2121     if (ioctl(sock, SIOCGIFNETMASK, (char *)&if2) < 0) {
            if (Ioctl.ioctl(sock, Socket.SIOCGIFNETMASK(), if2) < 0) {
                // 2122         NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFNETMASK failed");
                throw new SocketException(PosixUtils.lastErrorString("IOCTL SIOCGIFNETMASK failed"));
                // 2123         return -1;
                /* Unreachable code. */
            }
            // 2125
            // 2126     mask = ntohl(((struct sockaddr_in*)&(if2.ifr_addr))->sin_addr.s_addr);
            mask = NetinetIn.ntohl(((NetinetIn.sockaddr_in) (if2.ifr_addr())).sin_addr().s_addr());
            // 2127     ret = 0;
            ret = 0;
            // 2128     while (mask) {
            while (CTypeConversion.toBoolean(mask)) {
                // 2129        mask <<= 1;
                mask <<= 1;
                // 2130        ret++;
                ret++;
            }
            // 2132
            // 2133     return ret;
            return ret;
        }

        @Override
        // 2182 static int getFlags(int sock, const char *ifname, int *flags) {
        public int getFlags(int sock, CCharPointer ifname, CIntPointer flags) {
            // 2183   struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            // 2184   int ret = -1;
            /* `ret` is unused. */
            // 2185
            // 2186   memset((char *) &if2, 0, sizeof(if2));
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 2187   strcpy(if2.ifr_name, ifname);
            LibC.strcpy(if2.ifr_name(), ifname);
            // 2188
            // 2189   if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0){
            if (Ioctl.ioctl(sock, Socket.SIOCGIFFLAGS(), if2) < 0) {
                // 2190       return -1;
                return -1;
            }
            // 2192
            /* The condition on 2193 is always true. */
            // 2193   if (sizeof(if2.ifr_flags) == sizeof(short)) {
            // 2194     *flags = (if2.ifr_flags & 0xffff);
            flags.write(if2.ifr_flags() & 0xffff);
            // 2195   } else {
            // 2196     *flags = if2.ifr_flags;
            // 2197   }
            // 2198   return 0;
            return 0;
        }

        @Override
        // 2055 static int getIndex(int sock, const char *name){
        public int getIndex(int sock, CCharPointer name) {
            // 2056 #ifdef __FreeBSD__
            // 2057      /*
            // 2058       * Try to get the interface index
            // 2059       * (Not supported on Solaris 2.6 or 7)
            // 2060       */
            // 2061     struct ifreq if2;
            // 2062     strcpy(if2.ifr_name, name);
            // 2063
            // 2064     if (ioctl(sock, SIOCGIFINDEX, (char *)&if2) < 0) {
            // 2065         return -1;
            // 2066     }
            // 2067
            // 2068     return if2.ifr_index;
            // 2069 #else
            // 2070     /*
            // 2071      * Try to get the interface index using BSD specific if_nametoindex
            // 2072      */
            // 2073     int index = if_nametoindex(name);
            int index = NetIf.if_nametoindex(name);
            // 2074     return (index == 0) ? -1 : index;
            return (index == 0) ? -1 : index;
            // 2075 #endif
        }

        @Override
        // 2120 /*
        // 2121  * Gets the Hardware address (usually MAC address) for the named interface.
        // 2122  * On return puts the data in buf, and returns the length, in byte, of the
        // 2123  * MAC address. Returns -1 if there is no hardware address on that interface.
        // 2124  */
        // 2125 static int getMacAddress
        // 2126   (JNIEnv *env, const char *ifname, const struct in_addr *addr,
        // 2127    unsigned char *buf)
        // 2128 {
        public int getMacAddress(CCharPointer ifname, NetinetIn.in_addr addr, CCharPointer buf) throws SocketException {
            // 2129     struct ifaddrs *ifa0, *ifa;
            /* ifa0 is actually a `struct ifaddrs**`. See the call to `getifaddrs`. */
            Ifaddrs.ifaddrsPointer ifa0 = StackValue.get(Ifaddrs.ifaddrsPointer.class);
            Ifaddrs.ifaddrs ifa;
            // 2130     struct sockaddr *saddr;
            Socket.sockaddr saddr;
            // 2131     int i;
            /* `i` is unused. */
            // 2132
            // 2133     // grab the interface list
            // 2134     if (!getifaddrs(&ifa0)) {
            /* getifaddrs(struct ifaddrs **ifap) return 0 on success, -1 on failure, with errno set. */
            if (Ifaddrs.getifaddrs(ifa0) == 0) {
                // 2135         // cycle through the interfaces
                // 2136         for (i = 0, ifa = ifa0; ifa != NULL; ifa = ifa->ifa_next, i++) {
                for (ifa = ifa0.read(); ifa.isNonNull(); ifa = ifa.ifa_next()) {
                    // 2137             saddr = ifa->ifa_addr;
                    saddr = ifa.ifa_addr();
                    // 2138             // link layer contains the MAC address
                    // 2139             if (saddr->sa_family == AF_LINK && !strcmp(ifname, ifa->ifa_name)) {
                    if ((saddr.sa_family() == Socket.AF_LINK()) && (LibC.strcmp(ifname, ifa.ifa_name()) == 0)) {
                        // 2140                 struct sockaddr_dl *sadl = (struct sockaddr_dl *) saddr;
                        NetIfDl.sockaddr_dl sadl = (NetIfDl.sockaddr_dl) saddr;
                        // 2141                 // check the address has the correct length
                        // 2142                 if (sadl->sdl_alen == ETHER_ADDR_LEN) {
                        if (sadl.sdl_alen() == NetEthernet.ETHER_ADDR_LEN()) {
                            // 2143                     memcpy(buf, (sadl->sdl_data + sadl->sdl_nlen), ETHER_ADDR_LEN);
                            LibC.memcpy(buf, (sadl.sdl_data().addressOf(sadl.sdl_nlen())), WordFactory.unsigned(NetEthernet.ETHER_ADDR_LEN()));
                            // 2144                     freeifaddrs(ifa0);
                            Ifaddrs.freeifaddrs(ifa0.read());
                            // 2145                     return ETHER_ADDR_LEN;
                            return NetEthernet.ETHER_ADDR_LEN();
                        }
                    }
                }
                // 2149         freeifaddrs(ifa0);
                Ifaddrs.freeifaddrs(ifa0.read());
            }
            // 2151
            // 2152     return -1;
            return -1;
        }
    }
}

/* } Allow non-standard names: Checkstyle: resume */
/* } Do not format quoted code: @formatter:on */
