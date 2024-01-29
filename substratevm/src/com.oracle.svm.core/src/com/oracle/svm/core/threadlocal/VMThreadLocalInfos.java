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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Collection;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.log.Log;

import jdk.graal.compiler.word.Word;

@AutomaticallyRegisteredImageSingleton
public class VMThreadLocalInfos {
    /**
     * The {@link VMThreadLocalInfo} objects are scanned during analysis as soon as they are
     * discovered. After analysis, they are sorted and stored in the infos field.
     */
    @UnknownObjectField(availability = ReadyForCompilation.class)//
    private VMThreadLocalInfo[] infos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setInfos(Collection<VMThreadLocalInfo> infos) {
        VMThreadLocalInfos singleton = ImageSingletons.lookup(VMThreadLocalInfos.class);
        singleton.infos = infos.toArray(new VMThreadLocalInfo[0]);
    }

    public static void dumpToLog(Log log, IsolateThread thread, boolean isJavaHeapAccessAllowed) {
        Pointer threadLocals = (Pointer) thread;
        for (VMThreadLocalInfo info : ImageSingletons.lookup(VMThreadLocalInfos.class).infos) {
            log.signed(info.offset).string(": ").string(info.name).string(" = ");
            if (info.threadLocalClass == FastThreadLocalInt.class) {
                int value = threadLocals.readInt(WordFactory.signed(info.offset));
                log.string("(int) ").zhex(value).string(" (").signed(value).string(")");
            } else if (info.threadLocalClass == FastThreadLocalLong.class) {
                long value = threadLocals.readLong(WordFactory.signed(info.offset));
                log.string("(long) ").zhex(value).string(" (").signed(value).string(")");
            } else if (info.threadLocalClass == FastThreadLocalWord.class) {
                WordBase value = threadLocals.readWord(WordFactory.signed(info.offset));
                log.string("(Word) ").zhex(value).string(" (").signed(value).string(")");
            } else if (info.threadLocalClass == FastThreadLocalObject.class) {
                if (isJavaHeapAccessAllowed) {
                    Object value = readThreadLocalObject(threadLocals, info.offset);
                    log.string("(Object) ").zhex(Word.objectToUntrackedPointer(value));
                    if (value != null) {
                        log.indent(true);
                        SubstrateDiagnostics.printObjectInfo(log, value);
                        log.redent(false);
                    }
                } else {
                    Word value = readThreadLocalObjectAsWord(threadLocals, info.offset);
                    log.string("(Object) ").zhex(value);
                }
            } else if (info.threadLocalClass == FastThreadLocalBytes.class) {
                log.string("(bytes) ");
                Pointer data = threadLocals.add(WordFactory.signed(info.offset));
                if (info.sizeInBytes == 8) {
                    log.zhex(data.readWord(0));
                } else {
                    log.indent(true);
                    if (info.sizeInBytes % 8 == 0) {
                        log.hexdump(data, 8, info.sizeInBytes / 8);
                    } else {
                        log.hexdump(data, 1, info.sizeInBytes);
                    }
                    log.redent(false);
                }
            } else {
                log.string("unknown class ").string(info.threadLocalClass.getName());
            }
            log.newline();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Word readThreadLocalObjectAsWord(Pointer threadLocals, int offset) {
        return ReferenceAccess.singleton().readObjectAsUntrackedPointer(threadLocals.add(offset), true);
    }

    @Uninterruptible(reason = "Prevent objects from moving.")
    private static Object readThreadLocalObject(Pointer threadLocals, int offset) {
        return readThreadLocalObjectAsWord(threadLocals, offset).toObject();
    }

    public static int getOffset(FastThreadLocal threadLocal) {
        VMThreadLocalInfos singleton = ImageSingletons.lookup(VMThreadLocalInfos.class);
        for (VMThreadLocalInfo info : singleton.infos) {
            if (threadLocal.getLocationIdentity().equals(info.locationIdentity)) {
                return info.offset;
            }
        }
        return -1;
    }
}
