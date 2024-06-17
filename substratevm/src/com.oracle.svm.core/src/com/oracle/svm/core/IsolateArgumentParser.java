/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Parses a small subset of the runtime arguments before the image heap is mapped and before the
 * isolate is fully started.
 */
@AutomaticallyRegisteredImageSingleton
public class IsolateArgumentParser {
    private static final RuntimeOptionKey<?>[] OPTIONS = {SubstrateGCOptions.MinHeapSize, SubstrateGCOptions.MaxHeapSize, SubstrateGCOptions.MaxNewSize, SubstrateGCOptions.ReservedAddressSpaceSize,
                    SubstrateOptions.ConcealedOptions.AutomaticReferenceHandling, SubstrateOptions.ConcealedOptions.UsePerfData};
    private static final int OPTION_COUNT = OPTIONS.length;
    private static final CGlobalData<CCharPointer> OPTION_NAMES = CGlobalDataFactory.createBytes(IsolateArgumentParser::createOptionNames);
    private static final CGlobalData<CIntPointer> OPTION_NAME_POSITIONS = CGlobalDataFactory.createBytes(IsolateArgumentParser::createOptionNamePosition);
    private static final CGlobalData<CCharPointer> OPTION_TYPES = CGlobalDataFactory.createBytes(IsolateArgumentParser::createOptionTypes);
    private static final CGlobalData<CLongPointer> HOSTED_VALUES = CGlobalDataFactory.createBytes(IsolateArgumentParser::createHostedValues);
    private static final long[] PARSED_OPTION_VALUES = new long[OPTION_COUNT];

    private static final long K = 1024;
    private static final long M = K * K;
    private static final long G = K * M;
    private static final long T = K * G;

    private static boolean isCompilationIsolate;

    @Platforms(Platform.HOSTED_ONLY.class)
    public IsolateArgumentParser() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createOptionNames() {
        StringBuilder options = new StringBuilder();
        for (int i = 0; i < OPTION_COUNT; i++) {
            options.append(OPTIONS[i].getName());
            options.append("\0");
        }
        return options.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createOptionNamePosition() {
        byte[] result = new byte[Integer.BYTES * OPTION_COUNT];
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
        buffer.putInt(0);

        byte[] optionNames = createOptionNames();
        for (int i = 0; i < optionNames.length; i++) {
            if (optionNames[i] == '\0' && (i + 1) < optionNames.length) {
                buffer.putInt(i + 1);
            }
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createOptionTypes() {
        byte[] result = new byte[Byte.BYTES * OPTION_COUNT];
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
        for (int i = 0; i < OPTION_COUNT; i++) {
            Class<?> optionValueType = OPTIONS[i].getDescriptor().getOptionValueType();
            buffer.put(OptionValueType.fromClass(optionValueType));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createHostedValues() {
        byte[] result = new byte[Long.BYTES * OPTION_COUNT];
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
        for (int i = 0; i < OPTION_COUNT; i++) {
            long value = toLong(OPTIONS[i].getHostedValue());
            buffer.putLong(value);
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static long toLong(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        } else if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Long) {
            return (Long) value;
        } else {
            throw VMError.shouldNotReachHere("Unexpected option value: " + value);
        }
    }

    @Uninterruptible(reason = "Still being initialized.")
    public static void parse(CEntryPointCreateIsolateParameters parameters, CLongPointer parsedArgs) {
        initialize(parsedArgs);
        if (!LibC.isSupported() || !shouldParseArguments(parameters)) {
            // Without LibC support, argument parsing is disabled. So, we just use the build-time
            // values of the runtime options.
            return;
        }

        int argc = 0;
        CCharPointerPointer argv = WordFactory.nullPointer();
        if (parameters.isNonNull() && parameters.version() >= 3 && parameters.getArgv().isNonNull()) {
            argc = parameters.getArgc();
            argv = parameters.getArgv();
        }

        CLongPointer numericValue = StackValue.get(Long.BYTES);
        // Ignore the first argument as it represents the executable file name.
        for (int i = 1; i < argc; i++) {
            CCharPointer arg = argv.read(i);
            if (arg.isNonNull()) {
                CCharPointer tail = matchPrefix(arg);
                if (tail.isNonNull()) {
                    CCharPointer xOptionTail = matchXOption(tail);
                    if (xOptionTail.isNonNull()) {
                        parseXOption(parsedArgs, numericValue, xOptionTail);
                    } else {
                        CCharPointer xxOptionTail = matchXXOption(tail);
                        if (xxOptionTail.isNonNull()) {
                            parseXXOption(parsedArgs, numericValue, xxOptionTail);
                        }
                    }
                }
            }
        }
    }

    /**
     * Note that this logic must be in sync with
     * {@link com.oracle.svm.core.option.RuntimeOptionParser#parseAndConsumeAllOptions}.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    public static boolean shouldParseArguments(CEntryPointCreateIsolateParameters parameters) {
        if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
            return true;
        } else if (RuntimeCompilation.isEnabled() && SubstrateOptions.supportCompileInIsolates()) {
            return isCompilationIsolate(parameters);
        }
        return false;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static boolean isCompilationIsolate(CEntryPointCreateIsolateParameters parameters) {
        return parameters.version() >= 5 && parameters.getIsCompilationIsolate();
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static void persistOptions(CEntryPointCreateIsolateParameters parameters, CLongPointer parsedArgs) {
        isCompilationIsolate = isCompilationIsolate(parameters);

        for (int i = 0; i < OPTION_COUNT; i++) {
            PARSED_OPTION_VALUES[i] = parsedArgs.read(i);
        }
    }

    public static void verifyOptionValues() {
        for (int i = 0; i < OPTION_COUNT; i++) {
            RuntimeOptionKey<?> option = OPTIONS[i];
            if (shouldValidate(option)) {
                validate(option, getOptionValue(i));
            }
        }
    }

    private static boolean shouldValidate(RuntimeOptionKey<?> option) {
        if (SubstrateOptions.UseSerialGC.getValue()) {
            /* The serial GC supports changing the heap size at run-time to some degree. */
            return option != SubstrateGCOptions.MinHeapSize && option != SubstrateGCOptions.MaxHeapSize && option != SubstrateGCOptions.MaxNewSize;
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isCompilationIsolate() {
        return isCompilationIsolate;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean getBooleanOptionValue(int index) {
        return PARSED_OPTION_VALUES[index] == 1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getIntOptionValue(int index) {
        return (int) PARSED_OPTION_VALUES[index];
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getLongOptionValue(int index) {
        return PARSED_OPTION_VALUES[index];
    }

    private static Object getOptionValue(int index) {
        Class<?> optionValueType = OPTIONS[index].getDescriptor().getOptionValueType();
        long value = PARSED_OPTION_VALUES[index];
        if (optionValueType == Boolean.class) {
            assert value == 0 || value == 1 : value;
            return value == 1;
        } else if (optionValueType == Integer.class) {
            return (int) value;
        } else if (optionValueType == Long.class) {
            return value;
        } else {
            throw VMError.shouldNotReachHere("Option value has unexpected type: " + optionValueType);
        }
    }

    private static void validate(RuntimeOptionKey<?> option, Object oldValue) {
        Object newValue = option.getValue();
        if (newValue == null || !newValue.equals(oldValue)) {
            throw new IllegalArgumentException(
                            "The option '" + option.getName() + "' can't be changed after isolate creation. Old value: " + oldValue + ", new value: " + newValue);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void initialize(CLongPointer parsedArgs) {
        for (int i = 0; i < OPTION_COUNT; i++) {
            parsedArgs.write(i, HOSTED_VALUES.get().read(i));
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CCharPointer matchPrefix(CCharPointer arg) {
        if (arg.read(0) == '-') {
            return arg.addressOf(1);
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CCharPointer matchXOption(CCharPointer arg) {
        if (arg.read(0) == 'X' && arg.read(1) == 'm') {
            return arg.addressOf(2);
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CCharPointer matchXXOption(CCharPointer arg) {
        if (arg.read(0) == 'X' && arg.read(1) == 'X' && arg.read(2) == ':') {
            return arg.addressOf(3);
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void parseXOption(CLongPointer parsedArgs, CLongPointer numericValue, CCharPointer tail) {
        byte kind = tail.read();
        if (kind == 's' && parseNumericXOption(tail.addressOf(1), numericValue)) {
            parsedArgs.write(getOptionIndex(SubstrateGCOptions.MinHeapSize), numericValue.read());
        } else if (kind == 'x' && parseNumericXOption(tail.addressOf(1), numericValue)) {
            parsedArgs.write(getOptionIndex(SubstrateGCOptions.MaxHeapSize), numericValue.read());
        } else if (kind == 'n' && parseNumericXOption(tail.addressOf(1), numericValue)) {
            parsedArgs.write(getOptionIndex(SubstrateGCOptions.MaxNewSize), numericValue.read());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void parseXXOption(CLongPointer parsedArgs, CLongPointer numericValue, CCharPointer tail) {
        byte firstChar = tail.read();
        if (firstChar == '+' || firstChar == '-') {
            boolean value = firstChar == '+';
            for (int i = 0; i < OPTION_COUNT; i++) {
                int pos = OPTION_NAME_POSITIONS.get().read(i);
                CCharPointer optionName = OPTION_NAMES.get().addressOf(pos);
                if (OPTION_TYPES.get().read(i) == OptionValueType.Boolean && matches(tail.addressOf(1), optionName)) {
                    parsedArgs.write(i, value ? 1 : 0);
                    break;
                }
            }
        } else {
            for (int i = 0; i < OPTION_COUNT; i++) {
                int pos = OPTION_NAME_POSITIONS.get().read(i);
                CCharPointer optionName = OPTION_NAMES.get().addressOf(pos);
                if (OptionValueType.isNumeric(OPTION_TYPES.get().read(i)) && parseNumericXXOption(tail, optionName, numericValue)) {
                    parsedArgs.write(i, numericValue.read());
                    break;
                }
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean parseNumericXXOption(CCharPointer input, CCharPointer optionName, CLongPointer result) {
        CCharPointer tail = startsWith(input, optionName);
        if (tail.isNull() || tail.read() != '=') {
            return false;
        }
        return parseNumericXOption(tail.addressOf(1), result);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean parseNumericXOption(CCharPointer string, CLongPointer result) {
        CCharPointer pos = string;

        boolean negativeValue = false;
        if (pos.read() == '-') {
            negativeValue = true;
            pos = pos.addressOf(1);
        }

        if (!atojulong(pos, result)) {
            return false;
        }

        if (negativeValue) {
            result.write(-result.read());
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean atojulong(CCharPointer s, CLongPointer result) {
        /* First char must be a digit. Don't allow negative numbers or leading spaces. */
        if (LibC.isdigit(s.read()) == 0) {
            return false;
        }

        CCharPointerPointer tailPtr = (CCharPointerPointer) StackValue.get(CCharPointer.class);

        LibC.setErrno(0);
        UnsignedWord n = LibC.strtoull(s, tailPtr, 10);
        if (LibC.errno() != 0) {
            return false;
        }

        CCharPointer tail = tailPtr.read();
        /*
         * Fail if no number was read at all or if the tail contains more than a single non-digit
         * character.
         */
        if (tail == s || LibC.strlen(tail).aboveThan(1)) {
            return false;
        }

        long modifier;
        switch (tail.read()) {
            case 'T', 't' -> modifier = T;
            case 'G', 'g' -> modifier = G;
            case 'M', 'm' -> modifier = M;
            case 'K', 'k' -> modifier = K;
            case '\0' -> modifier = 1;
            default -> {
                return false;
            }
        }

        UnsignedWord value = n.multiply(WordFactory.unsigned(modifier));
        if (checkForOverflow(value, n, modifier)) {
            return false;
        }
        result.write(value.rawValue());
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean checkForOverflow(UnsignedWord value, UnsignedWord n, long modifier) {
        return value.unsignedDivide(WordFactory.unsigned(modifier)) != n;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean matches(CCharPointer input, CCharPointer expected) {
        CCharPointer tail = startsWith(input, expected);
        return tail.isNonNull() && tail.read() == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CCharPointer startsWith(CCharPointer input, CCharPointer prefix) {
        int i = 0;
        while (prefix.read(i) != 0 && input.read(i) == prefix.read(i)) {
            i++;
        }

        if (prefix.read(i) == 0) {
            return input.addressOf(i);
        }
        return WordFactory.nullPointer();
    }

    @Fold
    public static int getOptionIndex(RuntimeOptionKey<?> key) {
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i] == key) {
                return i;
            }
        }

        throw VMError.shouldNotReachHere("Could not find option " + key.getName() + " in the options array.");
    }

    @Fold
    public static int getStructSize() {
        return Long.BYTES * OPTION_COUNT;
    }

    private static class OptionValueType {
        public static byte Boolean = 1;
        public static byte Integer = 2;
        public static byte Long = 3;

        public static byte fromClass(Class<?> c) {
            if (c == Boolean.class) {
                return Boolean;
            } else if (c == Integer.class) {
                return Integer;
            } else if (c == Long.class) {
                return Long;
            } else {
                throw VMError.shouldNotReachHere("Option value has unexpected type: " + c);
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isNumeric(byte optionValueType) {
            return optionValueType == Integer || optionValueType == Long;
        }
    }
}
