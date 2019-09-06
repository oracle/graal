/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;

/**
 * The untethered version of {@link CodeInfo}, which can be released by the GC at <b>ANY</b>
 * safepoint. Before it is possible to access any data, it is necessary to convert a pointer of this
 * type to a {@link CodeInfo} pointer using {@link CodeInfoAccess#acquireTether} and
 * {@link CodeInfoAccess#convert}. For more details, refer to the documentation of
 * {@link CodeInfoAccess}.
 * <p>
 * <b>NEVER</b> do a direct cast from {@link UntetheredCodeInfo} to {@link CodeInfo}.
 * <p>
 * If it is necessary to access data without acquiring the tether, then call the static methods of
 * the class {@link UntetheredCodeInfoAccess} from uninterruptible code.
 */
@RawStructure
public interface UntetheredCodeInfo extends PointerBase {
}
