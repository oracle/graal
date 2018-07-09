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
package com.oracle.svm.core.posix.linux;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.JavaNetNetworkInterface;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.ArpaInet;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Ioctl;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.NetIf;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.PosixDirectives;
import com.oracle.svm.core.posix.headers.Socket;

/* { Do not format quoted code: @formatter:off */
/* { Allow non-standard names: Checkstyle: stop */

@Platforms(Platform.LINUX.class)
@CContext(PosixDirectives.class)
public class LinuxJavaNetNetworkInterface {

    /** Register the implementation. */
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
        @Override
        // 1114 static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
        public JavaNetNetworkInterface.netif enumIPv4Interfaces(int sock, JavaNetNetworkInterface.netif ifsParameter) throws SocketException {
            /*
             * Work around "The parameter ifs should not be assigned" waranings.
             * A down-side is that on errors I do not return a partially-built
             * chain of netif instances.
             */
            JavaNetNetworkInterface.netif ifs = ifsParameter;
            // 1115 struct ifconf ifc;
            NetIf.ifconf ifc = StackValue.get(NetIf.ifconf.class);
            // 1116 struct ifreq *ifreqP;
            NetIf.ifreq ifreqP;
            // 1117 char *buf = NULL;
            CCharPointer buf = WordFactory.nullPointer();
            // 1118 int numifs;
            /* `numifs` is unused. */
            // 1119 unsigned i;
            int i;
            // 1120 long siocgifconfRequest = SIOCGIFCONF;
            long siocgifconfRequest = Socket.SIOCGIFCONF();
            // 1121
            // 1122
            // 1123 #if defined(__linux__)
            if (IsDefined.__linux__()) {
                // 1124 /* need to do a dummy SIOCGIFCONF to determine the buffer size.
                // 1125 * SIOCGIFCOUNT doesn't work
                // 1126 */
                // 1127 ifc.ifc_buf = NULL;
                ifc.ifc_buf(WordFactory.nullPointer());
                // 1128 if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
                if (Ioctl.ioctl(sock, Socket.SIOCGIFCONF(), ifc) < 0) {
                    // 1129 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl
                    // SIOCGIFCONF failed");
                    throw new SocketException(PosixUtils.lastErrorString("ioctl SIOCGIFCONF failed"));
                    // 1130 return ifs;
                }
                // 1132 #elif defined(_AIX)
            } else if (IsDefined._AIX()) {
                /* Not translating the AIX branch. */
                // 1133 ifc.ifc_buf = NULL;
                // 1134 if (ioctl(sock, SIOCGSIZIFCONF, &(ifc.ifc_len)) < 0) {
                // 1135 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl
                // SIOCGSIZIFCONF failed");
                // 1136 return ifs;
                // 1137 }
            }
            // 1138 #endif /* __linux__ */
            // 1139
            // 1140 CHECKED_MALLOC3(buf,char *, ifc.ifc_len);
            /* Expands CHECKED_MALLOC3 inline. */
            // 843 #define CHECKED_MALLOC3(_pointer,_type,_size) \
            // 844 do{ \
            do {
                // 845 _pointer = (_type)malloc( _size ); \
                buf = LibC.malloc(WordFactory.unsigned(ifc.ifc_len()));
                // 846 if (_pointer == NULL) { \
                if (buf.isNull()) {
                    // 847 JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed"); \
                    throw new OutOfMemoryError("Native heap allocation failed");
                    // 848 return ifs; /* return untouched list */ \
                }
                // 850 } while(0)
            } while (false);
            // 1141
            // 1142 ifc.ifc_buf = buf;
            ifc.ifc_buf(buf);
            // 1143 #if defined(_AIX)
            // 1144 siocgifconfRequest = CSIOCGIFCONF;
            // 1145 #endif
            // 1146 if (ioctl(sock, siocgifconfRequest, (char *)&ifc) < 0) {
            if (Ioctl.ioctl(sock, siocgifconfRequest, ifc) < 0) {
                final int savedErrno = Errno.errno();
                // 1147 NET_ThrowByNameWithLastError(env , JNU_JAVANETPKG "SocketException", "ioctl
                // SIOCGIFCONF failed");
                // 1148 (void) free(buf);
                // 1149 return ifs;
                LibC.free(buf);
                throw new SocketException(PosixUtils.errorString(savedErrno, "ioctl SIOCGIFCONF failed"));
            }
            // 1151
            // 1152 /*
            // 1153 * Iterate through each interface
            // 1154 */
            // 1155 ifreqP = ifc.ifc_req;
            ifreqP = ifc.ifc_req();
            // 1156 for (i=0; i<ifc.ifc_len/sizeof (struct ifreq); i++, ifreqP++) {
            /* Using `i` as a index, and *indexing* into ifreqP. */
            for (i = 0; i < (ifc.ifc_len() / SizeOf.get(NetIf.ifreq.class)); i++) {
                // 1157 #if defined(_AIX)
                // 1158 if (ifreqP->ifr_addr.sa_family != AF_INET) continue;
                // 1159 #endif
                // 1160 /*
                // 1161  * Add to the list
                // 1162  */
                // 1163 ifs = addif(env, sock, ifreqP->ifr_name, ifs, (struct sockaddr *) &
                // (ifreqP->ifr_addr), AF_INET, 0);
                try {
                    ifs = JavaNetNetworkInterface.addif(sock, ifreqP.addressOf(i).ifr_name(), ifs, ifreqP.addressOf(i).ifr_addr(), Socket.AF_INET(), (short) 0);
                    // 1164
                    // 1165 /*
                    // 1166  * If an exception occurred then free the list
                    // 1167  */
                    // 1168 if ((*env)->ExceptionOccurred(env)) {
                } catch (Exception so) {
                    // 1169 free(buf);
                    LibC.free(buf);
                    // 1170 freeif(ifs);
                    JavaNetNetworkInterface.freeif(ifs);
                    // 1171 return NULL;
                    return null;
                }
            }
            // 1174
            // 1175 /*
            // 1176  * Free socket and buffer
            // 1177  */
            // 1178 free(buf);
            LibC.free(buf);
            // 1179 return ifs;
            return ifs;
        }

        /*
         * Translated from jdk/src/solaris/native/java/net/NetworkInterface.c?v=Java_1.8.0_40_b10
         *
         * A re-write to deal with the fact that Java does not have an `fscanf` method.
         * This method uses a mixture of Java and C code, and Java and C types.
         */
        // 1183 /*
        // 1184  * Enumerates and returns all IPv6 interfaces on Linux
        // 1185  */
        // 1186
        // 1187 #if defined(AF_INET6) && defined(__linux__)
        // 1188 static netif *enumIPv6Interfaces(JNIEnv *env, int sock, netif *ifs) {
        @Override
        public JavaNetNetworkInterface.netif enumIPv6Interfaces(int sock, JavaNetNetworkInterface.netif ifsParameter) throws SocketException {
            /* Work around "The parameter ifs should not be assigned". */
            JavaNetNetworkInterface.netif ifs = ifsParameter;
            // 1189     FILE *f;
            BufferedReader f;
            /* These variables are part of an IfInet6Info. */
            // 1190     char addr6[40], devname[21];
            // 1191     char addr6p[8][5];
            // 1192     int plen, scope, dad_status, if_idx;
            // 1193     uint8_t ipv6addr[16];
            CCharPointer ipv6addr = StackValue.get(16, CCharPointer.class);
            // 1196         while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s\n",
            // 1197                          addr6p[0], addr6p[1], addr6p[2], addr6p[3], addr6p[4], addr6p[5], addr6p[6], addr6p[7],
            // 1198                          &if_idx, &plen, &scope, &dad_status, devname) != EOF) {
            // 1194
            // 063 #define _PATH_PROCNET_IFINET6           "/proc/net/if_inet6"
            final String _PATH_PROCNET_IFINET6 = "/proc/net/if_inet6";
            // 1195     if ((f = fopen(_PATH_PROCNET_IFINET6, "r")) != NULL) {
            try { /* Guard against IOExceptions. */
                try { /* Guard against FileNotFoundException. */
                    f = new BufferedReader(new FileReader(_PATH_PROCNET_IFINET6));
                } catch (FileNotFoundException fnfe) {
                    return ifs;
                }
                try { /* Remember to close f. */
                    for (String line = f.readLine(); line != null; line = f.readLine()) {
                        IfInet6Info info = IfInet6Info.parse(line);
                        if (info != null) {
                            // 1199
                            // 1200             struct netif *ifs_ptr = NULL;
                            /* `ifs_ptr` is unused. */
                            // 1201             struct netif *last_ptr = NULL;
                            /* `last_ptr` is unused. */
                            // 1202             struct sockaddr_in6 addr;
                            NetinetIn.sockaddr_in6 addr = StackValue.get(NetinetIn.sockaddr_in6.class);
                            // 1203
                            // 1204             sprintf(addr6, "%s:%s:%s:%s:%s:%s:%s:%s",
                            // 1205                            addr6p[0], addr6p[1], addr6p[2], addr6p[3], addr6p[4], addr6p[5], addr6p[6], addr6p[7]);
                            // 1206             inet_pton(AF_INET6, addr6, ipv6addr);
                            try (CCharPointerHolder addr6Holder = CTypeConversion.toCString(info.getIPv6())) {
                                ArpaInet.inet_pton(Socket.AF_INET6(), addr6Holder.get(), ipv6addr);
                            }
                            // 1207
                            // 1208             memset(&addr, 0, sizeof(struct sockaddr_in6));
                            LibC.memset(addr, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetinetIn.sockaddr_in6.class)));
                            // 1209             memcpy((void*)addr.sin6_addr.s6_addr, (const void*)ipv6addr, 16);
                            LibC.memcpy(addr.sin6_addr().s6_addr(), ipv6addr, WordFactory.unsigned(16));
                            // 1210
                            // 1211             addr.sin6_scope_id = if_idx;
                            addr.set_sin6_scope_id(info.getIndex());
                            // 1212
                            // 1213             ifs = addif(env, sock, devname, ifs, (struct sockaddr *)&addr, AF_INET6, plen);
                            try (CCharPointerHolder devnameHolder = CTypeConversion.toCString(info.getDevice())) {
                                ifs = JavaNetNetworkInterface.addif(sock, devnameHolder.get(), ifs, (Socket.sockaddr) addr, Socket.AF_INET6(), (short) info.getPrefixLength());
                            }
                            // 1214
                            // 1215
                            // 1216             /*
                            // 1217              * If an exception occurred then return the list as is.
                            // 1218              */
                            // 1219             if ((*env)->ExceptionOccurred(env)) {
                            // 1220                 fclose(f);
                            // 1221                 return ifs;
                            // 1222             }
                        }
                    }
                } finally {
                    // 1224        fclose(f);
                    f.close();
                }
            } catch (IOException ioe) {
                /* Nothing to do. */
            }
            // 1226     return ifs;
            return ifs;
        }

        @Override
        // 1348 static struct sockaddr *getBroadcast(JNIEnv *env, int sock, const char *ifname, struct sockaddr *brdcast_store) {
        public Socket.sockaddr getBroadcast(int sock, CCharPointer ifname, Socket.sockaddr brdcast_store) throws SocketException {
            // 1349   struct sockaddr *ret = NULL;
            Socket.sockaddr ret = WordFactory.nullPointer();
            // 1350   struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            // 1351
            // 1352   memset((char *) &if2, 0, sizeof(if2));
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 1353   strcpy(if2.ifr_name, ifname);
            LibC.strcpy(if2.ifr_name(), ifname);
            // 1354
            // 1355   /* Let's make sure the interface does have a broadcast address */
            // 1356   if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2)  < 0) {
            if (Ioctl.ioctl(sock, Socket.SIOCGIFFLAGS(), if2) < 0) {
                // 1357       NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL  SIOCGIFFLAGS failed");
                throw new SocketException(PosixUtils.lastErrorString("IOCTL  SIOCGIFFLAGS failed"));
                // 1358       return ret;
                /* Unreachable code. */
            }
            // 1360
            // 1361   if (if2.ifr_flags & IFF_BROADCAST) {
            if (CTypeConversion.toBoolean(if2.ifr_flags() & NetIf.IFF_BROADCAST())) {
                // 1362       /* It does, let's retrieve it*/
                // 1363       if (ioctl(sock, SIOCGIFBRDADDR, (char *)&if2) < 0) {
                if (Ioctl.ioctl(sock,  Socket.SIOCGIFBRDADDR(), if2) < 0) {
                    // 1364           NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFBRDADDR failed");
                    throw new SocketException(PosixUtils.lastErrorString("IOCTL SIOCGIFBRDADDR failed"));
                    // 1365           return ret;
                    /* Unreachable code. */
                }
                // 1367
                // 1368       ret = brdcast_store;
                ret = brdcast_store;
                // 1369       memcpy(ret, &if2.ifr_broadaddr, sizeof(struct sockaddr));
                LibC.memcpy(ret, if2.ifr_broadaddr(), WordFactory.unsigned(SizeOf.get(Socket.sockaddr.class)));
            }
            // 1371
            // 1372   return ret;
            return ret;
        }

        @Override
        // 1375 /**
        // 1376  * Returns the IPv4 subnet prefix length (aka subnet mask) for the named
        // 1377  * interface, if it has one, otherwise return -1.
        // 1378  */
        // 1379 static short getSubnet(JNIEnv *env, int sock, const char *ifname) {
        public short getSubnet(int sock, CCharPointer ifname) throws SocketException {
            // 1380     unsigned int mask;
            int mask;
            // 1381     short ret;
            short ret;
            // 1382     struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            // 1383
            // 1384     memset((char *) &if2, 0, sizeof(if2));
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 1385     strcpy(if2.ifr_name, ifname);
            LibC.strcpy(if2.ifr_name(), ifname);
            // 1386
            // 1387     if (ioctl(sock, SIOCGIFNETMASK, (char *)&if2) < 0) {
            if (Ioctl.ioctl(sock, Socket.SIOCGIFNETMASK(), if2) < 0) {
                // 1388         NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", "IOCTL SIOCGIFNETMASK failed");
                throw new SocketException(PosixUtils.lastErrorString("IOCTL SIOCGIFNETMASK failed"));
                // 1389         return -1;
                /* Unreachable code. */
            }
            // 1391
            // 1392     mask = ntohl(((struct sockaddr_in*)&(if2.ifr_addr))->sin_addr.s_addr);
            mask = NetinetIn.ntohl(((NetinetIn.sockaddr_in) (if2.ifr_addr())).sin_addr().s_addr());
            // 1393     ret = 0;
            ret = 0;
            // 1394     while (mask) {
            while (CTypeConversion.toBoolean(mask)) {
                // 1395        mask <<= 1;
                mask <<= 1;
                // 1396        ret++;
                ret++;
            }
            // 1398
            // 1399     return ret;
            return ret;
        }

        @Override
        // 1487 static int getFlags(int sock, const char *ifname, int *flags) {
        public int getFlags(int sock, CCharPointer ifname, CIntPointer flags) {
            // 1488   struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            // 1489
            // 1490   memset((char *) &if2, 0, sizeof(if2));
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 1491   strcpy(if2.ifr_name, ifname);
            LibC.strcpy(if2.ifr_name(), ifname);
            // 1492
            // 1493   if (ioctl(sock, SIOCGIFFLAGS, (char *)&if2) < 0){
            if (Ioctl.ioctl(sock, Socket.SIOCGIFFLAGS(), if2) < 0) {
                // 1494       return -1;
                return -1;
            }
            // 1496
            /* The condition on 1497 is always true. */
            // 1497   if (sizeof(if2.ifr_flags) == sizeof(short)) {
            // 1498       *flags = (if2.ifr_flags & 0xffff);
            flags.write(if2.ifr_flags() & 0xffff);
            // 1499   } else {
            // 1500       *flags = if2.ifr_flags;
            // 1501   }
            // 1502   return 0;
            return 0;
        }

        @Override
        // 1326 static int getIndex(int sock, const char *name){
        public int getIndex(int sock, CCharPointer name) {
            // 1327      /*
            // 1328       * Try to get the interface index
            // 1329       */
            // 1330 #if defined(_AIX)
            // 1331     return if_nametoindex(name);
            // 1332 #else
            // 1333     struct ifreq if2;
            NetIf.ifreq if2 = StackValue.get(NetIf.ifreq.class);
            LibC.memset(if2, WordFactory.signed(0), WordFactory.unsigned(SizeOf.get(NetIf.ifreq.class)));
            // 1334     strcpy(if2.ifr_name, name);
            LibC.strcpy(if2.ifr_name(), name);
            // 1335
            // 1336     if (ioctl(sock, SIOCGIFINDEX, (char *)&if2) < 0) {
            if (Ioctl.ioctl(sock, Socket.SIOCGIFINDEX(), if2) < 0) {
                // 1337         return -1;
                return -1;
            }
            // 1339
            // 1340     return if2.ifr_ifindex;
            return if2.ifr_ifindex();
            // 1341 #endif
        }

        /** A Java representation of the information parsed from a line from /proc/net/if_inet6. */
        static class IfInet6Info {

            /** A pattern for "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s". */
            static final String s32 = "(.{32})";
            static final String x8 = "([0-9a-fA-F]{1,8})";
            static final String x2 = "([0-9a-fA-F]{1,2})";
            static final String s20 = "(.{1,20})";
            static final String blank = "[ \t]+";
            static final String patternString = s32 + blank + x8 + blank + x2 + blank + x2 + blank + x2 + blank + s20;
            static final Pattern pattern = Pattern.compile(patternString);

            /** Instance fields. */
            String ipv6;
            String index;
            String prefixLength;
            String scope;
            String flags;
            String device;

            public static IfInet6Info parse(String line) {
                Matcher matcher = pattern.matcher(line);
                final IfInet6Info result;
                if (matcher.matches()) {
                    result = new IfInet6Info(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5), matcher.group(6));
                } else {
                    result = null;
                }
                return result;
            }

            private IfInet6Info(String ipv6, String index, String prefix, String scope, String flags, String device) {
                this.ipv6 = ipv6;
                this.index = index;
                this.prefixLength = prefix;
                this.scope = scope;
                this.flags = flags;
                this.device = device;
            }

            /** Return one element of the IPv6 address. */
            String getIPv6(int which) {
                return ipv6.substring((which * 4), ((which + 1) * 4));
            }

            /** Return the IPv6 address as a colon-separated String. */
            String getIPv6() {
                return getIPv6(0) + ":" + getIPv6(1) + ":" + getIPv6(2) + ":" + getIPv6(3) + ":" +
                                getIPv6(4) + ":" + getIPv6(5) + ":" + getIPv6(6) + ":" + getIPv6(7);
            }

            /** Return the index. */
            int getIndex() {
                return Integer.parseInt(index, 16);
            }

            /** Return the prefix length. */
            int getPrefixLength() {
                return Integer.parseInt(prefixLength, 16);
            }

            /** Return the scope. */
            int getScope() {
                return Integer.parseInt(scope, 16);
            }

            /** Return the flags. */
            int getflags() {
                return Integer.parseInt(flags, 16);
            }

            /** Return the device name. */
            String getDevice() {
                return device;
            }

            void toLog(Log trace) {
                trace.string("  .ipv6:   ").string(getIPv6()).newline();
                trace.string("  .index:  ").hex(getIndex()).newline();
                trace.string("  .prefix: ").hex(getPrefixLength()).newline();
                trace.string("  .scope:  ").hex(getScope()).newline();
                trace.string("  .flags:  ").hex(getflags()).newline();
                trace.string("  .device: ").string(getDevice()).newline();
            }
        }

    }
}

/* } Allow non-standard names: Checkstyle: resume */
/* } Do not format quoted code: @formatter:on */
