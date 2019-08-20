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
package com.oracle.svm.core.threadlocal;

import java.util.Arrays;
import java.util.Collection;

import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;

public class VMThreadLocalInfos {

    private VMThreadLocalInfo[] infos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setInfos(Collection<VMThreadLocalInfo> infos) {
        VMThreadLocalInfos singleton = ImageSingletons.lookup(VMThreadLocalInfos.class);
        VMThreadLocalInfo[] array = infos.toArray(new VMThreadLocalInfo[infos.size()]);
        if (!Arrays.equals(singleton.infos, array)) {
            singleton.infos = array;
            return true;
        } else {
            return false;
        }
    }

    public static void dumpToLog(Log log, IsolateThread thread) {
        for (VMThreadLocalInfo info : ImageSingletons.lookup(VMThreadLocalInfos.class).infos) {
            log.signed(info.offset).string(" (").signed(info.sizeInBytes).string(" bytes): ").string(info.name).string(" = ");
            if (info.threadLocalClass == FastThreadLocalInt.class) {
                int value = primitiveData(thread).readInt(WordFactory.signed(info.offset));
                log.string("(int) ").signed(value).string("  ").zhex(value);
            } else if (info.threadLocalClass == FastThreadLocalLong.class) {
                long value = primitiveData(thread).readLong(WordFactory.signed(info.offset));
                log.string("(long) ").signed(value).string("  ").zhex(value);
            } else if (info.threadLocalClass == FastThreadLocalWord.class) {
                WordBase value = primitiveData(thread).readWord(WordFactory.signed(info.offset));
                log.string("(Word) ").signed(value).string("  ").zhex(value.rawValue());
            } else if (info.threadLocalClass == FastThreadLocalObject.class) {
                Object value = ObjectAccess.readObject(objectData(thread), WordFactory.signed(info.offset));
                log.string("(Object) ");
                if (value == null) {
                    log.string("null");
                } else {
                    log.string(value.getClass().getName()).string("  ").zhex(Word.objectToUntrackedPointer(value).rawValue());
                }
            } else if (info.threadLocalClass == FastThreadLocalBytes.class) {
                log.string("(bytes) ").indent(true);
                log.hexdump(primitiveData(thread).add(WordFactory.signed(info.offset)), 8, info.sizeInBytes / 8);
                log.indent(false);
            } else {
                log.string("unknown class ").string(info.threadLocalClass.getName());
            }
            log.newline();
        }
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    private static Pointer primitiveData(IsolateThread thread) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return (Pointer) thread;
        } else {
            return Word.objectToUntrackedPointer(ImageSingletons.lookup(VMThreadLocalSTSupport.class).primitiveThreadLocals);
        }
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    private static Object objectData(IsolateThread thread) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return ((Pointer) thread).toObjectNonNull();
        } else {
            return ImageSingletons.lookup(VMThreadLocalSTSupport.class).objectThreadLocals;
        }
    }

    public static long getOffset(FastThreadLocal threadLocal) {
        VMThreadLocalInfos singleton = ImageSingletons.lookup(VMThreadLocalInfos.class);
        for (VMThreadLocalInfo info : singleton.infos) {
            if (threadLocal.getLocationIdentity().equals(info.locationIdentity)) {
                return info.offset;
            }
        }
        return -1;
    }
}

@AutomaticFeature
class VMThreadLocalInfosFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VMThreadLocalInfos.class, new VMThreadLocalInfos());
    }
}
