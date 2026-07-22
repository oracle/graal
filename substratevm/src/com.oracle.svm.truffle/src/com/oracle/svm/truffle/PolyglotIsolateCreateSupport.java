/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JMethodID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObjectArray;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNI.JThrowable;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Isolates.ProtectionDomain;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointActions;
import com.oracle.svm.guest.staging.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.os.MemoryProtectionProvider;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.Utf8;

final class PolyglotIsolateCreateSupport {
    static final int FIND_POLYGLOT_ISOLATE_CREATE_EXCEPTION_CLASS_FAILED = 111;
    static final int FIND_POLYGLOT_ISOLATE_CREATE_EXCEPTION_CLASS_CTOR_FAILED = 112;
    static final int INSTANTIATE_POLYGLOT_ISOLATE_CREATE_EXCEPTION_CLASS_FAILED = 113;

    private static final CGlobalData<CCharPointer> polyglotIsolateCreateExceptionClassName = CGlobalDataFactory
                    .createCString("com/oracle/truffle/polyglot/isolate/PolyglotIsolateCreateException");
    private static final CGlobalData<CCharPointer> polyglotIsolateCreateExceptionFactory = CGlobalDataFactory
                    .createCString("create");
    private static final CGlobalData<CCharPointer> polyglotIsolateCreateExceptionFactorySignature = CGlobalDataFactory
                    .createCString("(ILjava/lang/String;)Lcom/oracle/truffle/polyglot/isolate/PolyglotIsolateCreateException;");

    /**
     * Serialized {@link CEntryPointErrors} descriptions. The error descriptions are used in case of
     * isolate creation failure where the code cannot allocate on heap. The serialized form is as
     * follows:
     * {@code (error_code: int, error_description_length: int, error_description_utf8: byte[error_description_length])+}.
     */
    private static final CGlobalData<Pointer> errorDescriptions;

    /**
     * Length of serialized {@link CEntryPointErrors} descriptions stored in the
     * {@link #errorDescriptions} in bytes.
     */
    private static final CGlobalData<CLongPointer> errorDescriptionsLength;

    /**
     * Converts the {@link CEntryPointErrors} descriptions into a byte array that can be stored in
     * the {@link CGlobalData}. The error descriptions are used in case of isolate creation failure
     * where the code cannot allocate on heap.
     */
    static {
        try {
            BinaryOutput.ByteArrayBinaryOutput out = BinaryOutput.create();
            for (Field field : CEntryPointErrors.class.getDeclaredFields()) {
                if (!Modifier.isPublic(field.getModifiers()) || field.getType() != Integer.TYPE) {
                    continue;
                }
                int value = (int) field.get(null);
                byte[] encodedDescription = Utf8.stringToUtf8(CEntryPointErrors.getDescription(value), true);
                out.writeInt(value);
                out.writeInt(encodedDescription.length);
                out.write(encodedDescription, 0, encodedDescription.length);
            }
            int length = out.getPosition();
            byte[] data = out.getArray();
            errorDescriptions = CGlobalDataFactory.createBytes(() -> data);
            errorDescriptionsLength = CGlobalDataFactory.createWord(Word.<UnsignedWord> unsigned(length));
        } catch (ReflectiveOperationException reflectiveException) {
            throw new RuntimeException(reflectiveException);
        }
    }

    @Uninterruptible(reason = "Unsafe state in case of failure")
    @CEntryPoint(name = "Java_com_oracle_truffle_polyglot_isolate_PolyglotNativeIsolateHandler_createIsolate", include = OptionalTrufflePolyglotGuestFeatureEnabled.class)
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static IsolateThread createIsolate(JNIEnv jniEnv, @SuppressWarnings("unused") JClass clazz, int protectionKey, JObjectArray options) {
        // Unpack arguments
        int optionsCount = jniEnv.getFunctions().getGetArrayLength().callNoTransition(jniEnv, options);
        // executable + arguments, but keep argc = 0, argv = null when there are no arguments
        int argc = optionsCount == 0 ? 0 : 1 + optionsCount;
        WordPointer jargs = Word.nullPointer();
        CCharPointerPointer argv = Word.nullPointer();
        int acquiredArgs = 0;
        try {
            if (optionsCount > 0) {
                jargs = UntrackedNullableNativeMemory.malloc(argc * SizeOf.get(WordPointer.class));
                if (jargs.isNull()) {
                    throwInHS(jniEnv, CEntryPointErrors.ALLOCATION_FAILED);
                    return Word.nullPointer();
                }
                argv = UntrackedNullableNativeMemory.malloc(argc * SizeOf.get(CCharPointerPointer.class));
                if (argv.isNull()) {
                    throwInHS(jniEnv, CEntryPointErrors.ALLOCATION_FAILED);
                    return Word.nullPointer();
                }
                // application name
                jargs.write(0, Word.nullPointer());
                argv.write(0, Word.nullPointer());
                for (int i = 0; i < optionsCount; i++) {
                    JString option = (JString) jniEnv.getFunctions().getGetObjectArrayElement().callNoTransition(jniEnv, options, i);
                    if (option.isNull()) {
                        // Preserve an exception raised by GetObjectArrayElement; otherwise report
                        // the unexpected null array element.
                        if (!jniEnv.getFunctions().getExceptionCheck().callNoTransition(jniEnv)) {
                            throwInHS(jniEnv, CEntryPointErrors.NULL_ARGUMENT);
                        }
                        return Word.nullPointer();
                    }
                    jargs.write(1 + i, option);
                    CCharPointer arg = jniEnv.getFunctions().getGetStringUTFChars().callNoTransition(jniEnv, option, Word.nullPointer());
                    if (arg.isNull()) {
                        // Return with a pending JNI exception from GetStringUTFChars
                        return Word.nullPointer();
                    }
                    argv.write(1 + i, arg);
                    acquiredArgs++;
                }
            }
            CEntryPointCreateIsolateParameters params = StackValue.get(CEntryPointCreateIsolateParameters.class);
            // set defaults (see EnterpriseAddressRangeCommittedMemoryProvider.initialize())
            params.setReservedSpaceSize(Word.zero());
            params.setAuxiliaryImageReservedSpaceSize(Word.zero());
            params.setAuxiliaryImagePath(Word.nullPointer());
            params.setArgc(argc);
            params.setArgv(argv);

            // set the protection key
            params.setProtectionKey(protectionKey);
            params.setVersion(3);

            int status = CEntryPointActions.enterCreateIsolate(params);
            if (status != CEntryPointErrors.NO_ERROR) {
                throwInHS(jniEnv, status);
                return Word.nullPointer();
            }
        } finally {
            // Free arguments
            for (int i = 0; i < acquiredArgs; i++) {
                jniEnv.getFunctions().getReleaseStringUTFChars().callNoTransition(jniEnv, jargs.read(i + 1), argv.read(i + 1));
            }
            if (argv.isNonNull()) {
                UntrackedNullableNativeMemory.free(argv);
            }
            if (jargs.isNonNull()) {
                UntrackedNullableNativeMemory.free(jargs);
            }
        }
        IsolateThread result = CurrentIsolate.getCurrentThread();
        CEntryPointActions.leave();
        return result;
    }

    @CEntryPoint(name = "truffle_isolate_isMemoryProtected", include = OptionalTrufflePolyglotGuestFeatureEnabled.class)
    @SuppressWarnings("unused")
    public static boolean isMemoryProtected(IsolateThread isolateId) {
        if (MemoryProtectionProvider.isAvailable()) {
            ProtectionDomain pd = MemoryProtectionProvider.singleton().getProtectionDomain();
            if (!ProtectionDomain.NO_DOMAIN.equals(pd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Looks up an error description for {@code errorCode} in the C byte array prepared in the image
     * build time.
     *
     * @param errorCode the error code
     * @return the {@link CCharPointer} to the error description, terminated by {@code '\0'}, or
     *         {@link Word#nullPointer() nullptr} when the {@code errorCode} is unknown.
     */
    @Uninterruptible(reason = "Unsafe state in case of failure")
    private static CCharPointer getErrorDescription(int errorCode) {
        int blobLength = (int) errorDescriptionsLength.get().read();
        Pointer p = errorDescriptions.get();
        Pointer end = p.add(blobLength);
        while (p.belowThan(end)) {
            int code = readBigEndianInt(p);
            p = p.add(Integer.BYTES);
            int descriptionLength = readBigEndianInt(p);
            p = p.add(Integer.BYTES);
            if (errorCode == code) {
                return (CCharPointer) p;
            }
            p = p.add(descriptionLength);
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = "Unsafe state in case of failure")
    private static int readBigEndianInt(Pointer p) {
        int b1 = p.readByte(0);
        int b2 = p.readByte(1);
        int b3 = p.readByte(2);
        int b4 = p.readByte(3);
        return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
    }

    @Uninterruptible(reason = "Unsafe state in case of failure", calleeMustBe = false)
    private static void throwInHS(JNIEnv jniEnv, int errorCode) {
        JClass exceptionClass = jniEnv.getFunctions().getFindClass().callNoTransition(jniEnv, polyglotIsolateCreateExceptionClassName.get());
        if (jniEnv.getFunctions().getExceptionCheck().callNoTransition(jniEnv)) {
            jniEnv.getFunctions().getExceptionDescribe().callNoTransition(jniEnv);
            CEntryPointActions.failFatally(FIND_POLYGLOT_ISOLATE_CREATE_EXCEPTION_CLASS_FAILED, Word.nullPointer());
        }
        JMethodID factoryMethod = jniEnv.getFunctions().getGetStaticMethodID().callNoTransition(jniEnv, exceptionClass, polyglotIsolateCreateExceptionFactory.get(),
                        polyglotIsolateCreateExceptionFactorySignature.get());
        if (jniEnv.getFunctions().getExceptionCheck().callNoTransition(jniEnv)) {
            jniEnv.getFunctions().getExceptionDescribe().callNoTransition(jniEnv);
            CEntryPointActions.failFatally(FIND_POLYGLOT_ISOLATE_CREATE_EXCEPTION_CLASS_CTOR_FAILED, Word.nullPointer());
        }
        CCharPointer errorDescription = getErrorDescription(errorCode);
        JNI.JObject hsErrorDescription;
        if (errorDescription.isNonNull()) {
            hsErrorDescription = jniEnv.getFunctions().getNewStringUTF().callNoTransition(jniEnv, errorDescription);
        } else {
            hsErrorDescription = Word.nullPointer();
        }
        JValue args = StackValue.get(2, JNI.JValue.class);
        args.addressOf(0).setInt(errorCode);
        args.addressOf(1).setJObject(hsErrorDescription);
        JThrowable exception = (JThrowable) jniEnv.getFunctions().getCallStaticObjectMethodA().callNoTransition(jniEnv, exceptionClass, factoryMethod, args);
        if (jniEnv.getFunctions().getExceptionCheck().callNoTransition(jniEnv)) {
            jniEnv.getFunctions().getExceptionDescribe().callNoTransition(jniEnv);
            CEntryPointActions.failFatally(INSTANTIATE_POLYGLOT_ISOLATE_CREATE_EXCEPTION_CLASS_FAILED, Word.nullPointer());
        }
        jniEnv.getFunctions().getThrow().callNoTransition(jniEnv, exception);
    }

}
