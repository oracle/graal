/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.debug;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.ProjectHeaderFile;

import jdk.graal.compiler.word.Word;

/**
 * This interface is based on the <a href=
 * "https://sourceware.org/gdb/current/onlinedocs/gdb.html/Declarations.html#Declarations">GDB JIT
 * compilation interface</a> and contains implementations for registering and unregistering run-time
 * compilations in GDB.
 */
@CContext(GdbJitInterface.GdbJitInterfaceDirectives.class)
public class GdbJitInterface {

    public static class GdbJitInterfaceDirectives implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return SubstrateOptions.RuntimeDebugInfo.getValue();
        }

        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList(ProjectHeaderFile.resolve("", "include/gdb_jit_compilation_interface.h"));
        }
    }

    private static final class IncludeForRuntimeDebugOnly implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.RuntimeDebugInfo.getValue();
        }
    }

    @CEnum(value = "jit_actions_t")
    public enum JITActions {
        JIT_NOACTION,
        JIT_REGISTER,
        JIT_UNREGISTER;

        @CEnumValue
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public native int getCValue();
    }

    @CStruct(value = "jit_code_entry", addStructKeyword = true)
    public interface JITCodeEntry extends PointerBase {
        // struct jit_code_entry *next_entry;
        @CField("next_entry")
        JITCodeEntry getNextEntry();

        @CField("next_entry")
        void setNextEntry(JITCodeEntry jitCodeEntry);

        // struct jit_code_entry *prev_entry;
        @CField("prev_entry")
        JITCodeEntry getPrevEntry();

        @CField("prev_entry")
        void setPrevEntry(JITCodeEntry jitCodeEntry);

        // const char *symfile_addr;
        @CField("symfile_addr")
        CCharPointer getSymfileAddr();

        @CField("symfile_addr")
        void setSymfileAddr(CCharPointer symfileAddr);

        // uint64_t symfile_size;
        @CField("symfile_size")
        @CUnsigned
        long getSymfileSize();

        @CField("symfile_size")
        void setSymfileSize(@CUnsigned long symfileSize);
    }

    @CStruct(value = "jit_descriptor", addStructKeyword = true)
    public interface JITDescriptor extends PointerBase {
        // uint32_t version;
        @CField("version")
        @CUnsigned
        int getVersion();

        @CField("version")
        void setVersion(@CUnsigned int version);

        // uint32_t action_flag;
        @CField("action_flag")
        @CUnsigned
        int getActionFlag();

        @CField("action_flag")
        void setActionFlag(@CUnsigned int actionFlag);

        // struct jit_code_entry *relevant_entry;
        @CField("relevant_entry")
        JITCodeEntry getRelevantEntry();

        @CField("relevant_entry")
        void setRelevantEntry(JITCodeEntry jitCodeEntry);

        // struct jit_code_entry *first_entry;
        @CField("first_entry")
        JITCodeEntry getFirstEntry();

        @CField("first_entry")
        void setFirstEntry(JITCodeEntry jitCodeEntry);
    }

    @NeverInline("Register JIT code stub for GDB.")
    @CEntryPoint(name = "__jit_debug_register_code", include = IncludeForRuntimeDebugOnly.class, publishAs = CEntryPoint.Publish.SymbolOnly)
    private static void jitDebugRegisterCode(@SuppressWarnings("unused") IsolateThread thread) {
    }

    private static final CGlobalData<JITDescriptor> jitDebugDescriptor = CGlobalDataFactory.forSymbol("__jit_debug_descriptor");

    public static void registerJITCode(CCharPointer addr, @CUnsigned long size, JITCodeEntry entry) {
        /* Create new jit_code_entry */
        entry.setSymfileAddr(addr);
        entry.setSymfileSize(size);

        /* Insert entry at head of the list. */
        JITCodeEntry nextEntry = jitDebugDescriptor.get().getFirstEntry();
        entry.setPrevEntry(Word.nullPointer());
        entry.setNextEntry(nextEntry);

        if (nextEntry.isNonNull()) {
            nextEntry.setPrevEntry(entry);
        }

        /* Notify GDB. */
        jitDebugDescriptor.get().setActionFlag(JITActions.JIT_REGISTER.getCValue());
        jitDebugDescriptor.get().setFirstEntry(entry);
        jitDebugDescriptor.get().setRelevantEntry(entry);
        jitDebugRegisterCode(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Called with raw object pointer.", calleeMustBe = false)
    public static void unregisterJITCode(JITCodeEntry entry) {
        JITCodeEntry prevEntry = entry.getPrevEntry();
        JITCodeEntry nextEntry = entry.getNextEntry();

        /* Fix prev and next in list */
        if (nextEntry.isNonNull()) {
            nextEntry.setPrevEntry(prevEntry);
        }

        if (prevEntry.isNonNull()) {
            prevEntry.setNextEntry(nextEntry);
        } else {
            assert (jitDebugDescriptor.get().getFirstEntry().equal(entry));
            jitDebugDescriptor.get().setFirstEntry(nextEntry);
        }

        /* Notify GDB. */
        jitDebugDescriptor.get().setActionFlag(JITActions.JIT_UNREGISTER.getCValue());
        jitDebugDescriptor.get().setRelevantEntry(entry);
        jitDebugRegisterCode(CurrentIsolate.getCurrentThread());
    }
}
