// Checkstyle: stop
// @formatter:off
// Class copied from JDK9
package com.oracle.svm.core.jdk8.zipfile;
/*
 * Copyright (c) 1995, 2017, Oracle and/or its affiliates. All rights reserved.
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

//package java.util.zip;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

/*
 * This interface defines the constants that are used by the classes
 * which manipulate ZIP files.
 *
 * @author      David Connelly
 * @since 1.1
 */
@SuppressWarnings("all")
@Substitute
@com.oracle.svm.core.annotate.TargetClass(className = "java.util.zip.ZipConstants", onlyWith = JDK8OrEarlier.class)
interface ZipConstants {
    /*
     * Header signatures
     */
    @Substitute
    static long LOCSIG = 0x04034b50L;   // "PK\003\004"
    @Substitute
    static long EXTSIG = 0x08074b50L;   // "PK\007\008"
    @Substitute
    static long CENSIG = 0x02014b50L;   // "PK\001\002"
    @Substitute
    static long ENDSIG = 0x06054b50L;   // "PK\005\006"

    /*
     * Header sizes in bytes (including signatures)
     */
    @Substitute
    static final int LOCHDR = 30;       // LOC header size
    @Substitute
    static final int EXTHDR = 16;       // EXT header size
    @Substitute
    static final int CENHDR = 46;       // CEN header size
    @Substitute
    static final int ENDHDR = 22;       // END header size

    /*
     * Local file (LOC) header field offsets
     */
    @Substitute
    static final int LOCVER = 4;        // version needed to extract
    @Substitute
    static final int LOCFLG = 6;        // general purpose bit flag
    @Substitute
    static final int LOCHOW = 8;        // compression method
    @Substitute
    static final int LOCTIM = 10;       // modification time
    @Substitute
    static final int LOCCRC = 14;       // uncompressed file crc-32 value
    @Substitute
    static final int LOCSIZ = 18;       // compressed size
    @Substitute
    static final int LOCLEN = 22;       // uncompressed size
    @Substitute
    static final int LOCNAM = 26;       // filename length
    @Substitute
    static final int LOCEXT = 28;       // extra field length

    /*
     * Extra local (EXT) header field offsets
     */
    @Substitute
    static final int EXTCRC = 4;        // uncompressed file crc-32 value
    @Substitute
    static final int EXTSIZ = 8;        // compressed size
    @Substitute
    static final int EXTLEN = 12;       // uncompressed size

    /*
     * Central directory (CEN) header field offsets
     */
    @Substitute
    static final int CENVEM = 4;        // version made by
    @Substitute
    static final int CENVER = 6;        // version needed to extract
    @Substitute
    static final int CENFLG = 8;        // encrypt, decrypt flags
    @Substitute
    static final int CENHOW = 10;       // compression method
    @Substitute
    static final int CENTIM = 12;       // modification time
    @Substitute
    static final int CENCRC = 16;       // uncompressed file crc-32 value
    @Substitute
    static final int CENSIZ = 20;       // compressed size
    @Substitute
    static final int CENLEN = 24;       // uncompressed size
    @Substitute
    static final int CENNAM = 28;       // filename length
    @Substitute
    static final int CENEXT = 30;       // extra field length
    @Substitute
    static final int CENCOM = 32;       // comment length
    @Substitute
    static final int CENDSK = 34;       // disk number start
    @Substitute
    static final int CENATT = 36;       // internal file attributes
    @Substitute
    static final int CENATX = 38;       // external file attributes
    @Substitute
    static final int CENOFF = 42;       // LOC header offset

    /*
     * End of central directory (END) header field offsets
     */
    @Substitute
    static final int ENDSUB = 8;        // number of entries on this disk
    @Substitute
    static final int ENDTOT = 10;       // total number of entries
    @Substitute
    static final int ENDSIZ = 12;       // central directory size in bytes
    @Substitute
    static final int ENDOFF = 16;       // offset of first CEN header
    @Substitute
    static final int ENDCOM = 20;       // zip file comment length
}
