/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.tests.llirtestgen;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.llvm.tests.Platform;

/**
 * This class (with an executable main method) produces an exhaustive test of all unary/binary/cast
 * bitcode operations on all arithmetic types (incl. vectors).
 */
public class LLIRTestGen {
    // Allow instructions introduced in v8.0?
    private static final boolean LLVM8 = false;

    // Remove exotic operations, allows execution on Sulong.
    private static final boolean REDUCED = true;

    // Needs to correlate to input/output array sizes in the prelude.
    private static final int MAX_INPUT_BYTES = 32;

    private interface Type {
        int getBits();

        int getBytes();

        boolean isFloat();

        String toName();
    }

    private enum ScalarType implements Type {
        // Simple types
        i1(1, false, true, true),
        i8(8, false, true, true),
        i16(16, false, true, true),
        i32(32, false, true, true),
        i64(64, false, true, true),

        // Strange sizes
        i128(128, false, false, true),
        i13(13, false, false, false),
        i15(15, false, false, false),
        i17(17, false, false, false),
        i126(126, false, false, false),
        i129(129, false, false, false),
        i1023(255, false, false, false),
        i1024(256, false, false, true),

        // Floats
        half(16, true, false, false),
        Float(32, true, true, true),
        Double(64, true, true, true),
        fp128(128, true, false, false),
        x86_fp80(80, true, true, false),
        ppc_fp128(128, true, false, false);

        final int bits;
        final int bytes;
        final boolean isFloat;
        final boolean include; // include for Sulong
        final boolean includeVector; // include as vector for Sulong

        ScalarType(int bits, boolean isFloat, boolean include, boolean includeVector) {
            this.isFloat = isFloat;
            this.include = include;
            this.includeVector = includeVector;
            this.bits = bits;
            this.bytes = (bits + 7) / 8;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        @Override
        public String toName() {
            return toString();
        }

        @Override
        public int getBytes() {
            return bytes;
        }

        @Override
        public int getBits() {
            return bits;
        }

        @Override
        public boolean isFloat() {
            return isFloat;
        }
    }

    private static class VectorType implements Type {
        private ScalarType type;
        int length;

        VectorType(ScalarType type, int length) {
            this.type = type;
            this.length = length;
        }

        @Override
        public String toString() {
            return "<" + length + " x " + type + ">";
        }

        @Override
        public String toName() {
            return "" + length + "x" + type;
        }

        @Override
        public int getBytes() {
            return length * type.getBytes();
        }

        @Override
        public int getBits() {
            return type.getBits() * length;
        }

        @Override
        public boolean isFloat() {
            return type.isFloat;
        }
    }

    private static final ArrayList<ScalarType> scalarTypes = new ArrayList<>();
    private static final ArrayList<Type> allTypes = new ArrayList<>();

    static {
        if (REDUCED) {
            scalarTypes.addAll(Arrays.asList(ScalarType.values()).stream().filter(s -> s.include).collect(Collectors.toList()));
        } else {
            // These removals are needed to make it compile with clang.
            scalarTypes.addAll(Arrays.asList(ScalarType.values()));
            scalarTypes.remove(ScalarType.ppc_fp128);
            scalarTypes.remove(ScalarType.fp128);
            scalarTypes.remove(ScalarType.x86_fp80);
            scalarTypes.remove(ScalarType.half);
            scalarTypes.remove(ScalarType.i126);
            scalarTypes.remove(ScalarType.i129);
            scalarTypes.remove(ScalarType.i1023);
            scalarTypes.remove(ScalarType.i1024);
        }

        allTypes.addAll(scalarTypes);

        for (ScalarType type : scalarTypes) {
            for (int length : new int[]{1, 2, 3, 4, 5, 6, 7, 8, 16, 32}) {
                if ((type.includeVector || !REDUCED) && length * type.getBytes() <= MAX_INPUT_BYTES) {
                    allTypes.add(new VectorType(type, length));
                }
            }
        }
    }

    interface UnaryOpFormatter {
        String format(Type inputType, String inputId, Type outputType);
    }

    static final UnaryOpFormatter CONV = (inputType, inputId, outputType) -> String.format("%s %s to %s", inputType, inputId,
                    outputType);
    static final UnaryOpFormatter SIMPLE = (inputType, inputId, outputType) -> String.format("%s %s", inputType, inputId);

    public enum UnaryOp {
        zext(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && !input.isFloat() && !output.isFloat() && input.getBits() < output.getBits();
            }
        },
        sext(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && !input.isFloat() && !output.isFloat() && input.getBits() < output.getBits();
            }
        },
        trunc(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && !input.isFloat() && !output.isFloat() && input.getBits() > output.getBits();
            }
        },
        fpext(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && input.isFloat() && output.isFloat() && input.getBits() < output.getBits();
            }
        },
        fptrunc(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && input.isFloat() && output.isFloat() && input.getBits() > output.getBits();
            }
        },
        fptoui(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && input.isFloat() && !output.isFloat() && (!REDUCED || input instanceof ScalarType);
            }
        },
        fptosi(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && input.isFloat() && !output.isFloat() && (!REDUCED || input instanceof ScalarType);
            }
        },
        uitofp(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && !input.isFloat() && output.isFloat() && (!REDUCED || input instanceof ScalarType);
            }
        },
        sitofp(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && !input.isFloat() && output.isFloat() && (!REDUCED || input instanceof ScalarType);
            }
        },
        bitcast(CONV) {
            @Override
            public boolean fits(Type input, Type output) {
                return scalarOrSameLength(input, output) && input.getBits() == output.getBits();
            }
        },
        fneg(SIMPLE) {
            @Override
            public boolean fits(Type input, Type output) {
                return input == output && input.isFloat() && LLVM8;
            }
        };

        final UnaryOpFormatter formatter;

        UnaryOp(UnaryOpFormatter formatter) {
            this.formatter = formatter;
        }

        protected static boolean scalarOrSameLength(Type input, Type output) {
            return (input instanceof ScalarType && output instanceof ScalarType) ||
                            (input instanceof VectorType && output instanceof VectorType && ((VectorType) input).length == ((VectorType) output).length);
        }

        abstract boolean fits(Type input, Type output);
    }

    interface BinaryOpFormatter {
        String format(Type inputType, String inputId0, String inputId1);
    }

    static final BinaryOpFormatter SimpleBin = (inputType, inputId0, inputId1) -> inputType + " " + inputId0 + ", " + inputId1;

    public enum BinaryOp {
        add(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        fadd(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return type.isFloat();
            }
        },
        sub(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        fsub(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return type.isFloat();
            }
        },
        mul(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        fmul(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return type.isFloat();
            }
        },
        udiv(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        sdiv(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        fdiv(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return type.isFloat();
            }
        },
        urem(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        srem(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        frem(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return type.isFloat();
            }
        },
        shl(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        lshr(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        ashr(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        and(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        or(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        },
        xor(SimpleBin) {
            @Override
            public boolean fits(Type type) {
                return !type.isFloat();
            }
        };

        final BinaryOpFormatter formatter;

        BinaryOp(BinaryOpFormatter formatter) {
            this.formatter = formatter;
        }

        abstract boolean fits(Type type);
    }

    static class IDCounter {
        private int idCount = 0;

        public String nextId() {
            return "%v" + (idCount++);
        }
    }

    static class StringDB {
        private StringBuilder strings = new StringBuilder();

        StringBuilder get() {
            return strings;
        }

        /**
         * Create a static string constant and return the load instruction that produces the pointer
         * to it.
         */
        String addConst(String text) {
            String id = String.format("string%d", strings.length());
            int len = text.length() + 1;

            strings.append(String.format("@.str%s = private unnamed_addr constant ", id));
            strings.append(String.format("[%d x i8] c\"%s\\00\", align 1\n", len, text));

            // Fails on LLVM 3.8 with "@%s = local_unnamed_addr global ..."
            strings.append(String.format("@%s = global i8* getelementptr inbounds ", id));
            strings.append(String.format("([%d x i8], [%d x i8]* @.str%s, i64 0, i64 0), align 8\n", len, len, id));

            return String.format("load i8*, i8** @%s, align 8", id);
        }
    }

    /**
     * Create a snippet of code that stores the result to the output and calls the "print_output"
     * function.
     */
    private static void storeAndCheck(IDCounter idCounter, StringDB strings, StringBuilder str, String id, Type type,
                    String output, String description) {
        String pointer = idCounter.nextId();

        str.append(String.format("%s = bitcast i8* %s to %s*\n", pointer, output, type));
        str.append(String.format("store %s %s, %s* %s, align 64\n", type, id, type, pointer));

        String string = idCounter.nextId();

        str.append(String.format("%s = %s\n", string, strings.addConst(description)));
        str.append(String.format("tail call void @print_output(i8* %s, i8* %s)\n", output, string));
    }

    static class Info {
        StringBuilder str = new StringBuilder();
        String in0 = "%0";
        String in1 = "%1";
        String out = "%2";
        boolean generated = false;
        LoadedValues loadedValues;
    }

    static class LoadedValues {
        String v0;
        String v1;

        LoadedValues(String v0, String v1) {
            this.v0 = v0;
            this.v1 = v1;
        }
    }

    private static LoadedValues genBitcastAndLoad(IDCounter idCounter, Info info, Type type) {
        String castPointer0 = idCounter.nextId();
        String castPointer1 = idCounter.nextId();
        LoadedValues loadValues = new LoadedValues(idCounter.nextId(), idCounter.nextId());

        String bitcastFmt = "%s = bitcast i8* %s to %s*\n";
        info.str.append(String.format(bitcastFmt, castPointer0, info.in0, type));
        info.str.append(String.format(bitcastFmt, castPointer1, info.in1, type));

        String loadFmt = "%s = load %s, %s* %s, align 8\n";
        info.str.append(String.format(loadFmt, loadValues.v0, type, type, castPointer0));
        info.str.append(String.format(loadFmt, loadValues.v1, type, type, castPointer1));

        return loadValues;
    }

    private static Info genPrefix(IDCounter idCounter, StringDB strings, Type type, boolean includeStores) {
        Info info = new Info();
        info.str.append("define void @run(i8*,i8*,i8*) {\n");

        // Load all possible data types from the input parameters.
        info.loadedValues = genBitcastAndLoad(idCounter, info, type);

        // Print initial, unmodified output.
        String initialString = idCounter.nextId();

        info.str.append(String.format("%s = %s\n", initialString, strings.addConst("initial")));
        info.str.append(String.format("tail call void @print_output(i8* %s, i8* %s)\n", info.out, initialString));

        if (includeStores) {
            // Simply store all types back to the output array.
            storeAndCheck(idCounter, strings, info.str, info.loadedValues.v0, type, info.out, "store " + type);
        }

        return info;
    }

    private static Optional<StringBuilder> genPostfix(Info info) {
        info.str.append("ret void\n");
        info.str.append("}\n");

        if (info.generated) {
            return Optional.of(info.str);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Generate the contents of the "run" function for binary operations that gets two input
     * pointers and an output pointer and performs the requested combination of types and operation.
     */
    private static Optional<StringBuilder> genBinary(boolean debug, String progName, IDCounter idCounter, StringDB strings, Type type, BinaryOp op,
                    boolean includeStores) {
        Info info = genPrefix(idCounter, strings, type, includeStores);

        if (op.fits(type)) {
            info.generated = true;

            String rhs = info.loadedValues.v1;

            boolean failsForZero = op.toString().contains("rem") || op.toString().contains("div");

            if (failsForZero) {
                if (type instanceof VectorType) {
                    if (debug) {
                        System.err.printf("%s: Ignoring %s on vector type %s\n", progName, op, type);
                    }
                    return Optional.empty();
                }

                String temp = idCounter.nextId();

                if (type == ScalarType.fp128) {
                    info.str.append(String.format("%s = fadd %s %s, 0xL49284756365758473845757575757575\n", temp, type, rhs));
                } else if (type == ScalarType.ppc_fp128) {
                    info.str.append(String.format("%s = fadd %s %s, 0xM49284756365758473845746464646464\n", temp, type, rhs));
                } else if (type == ScalarType.x86_fp80) {
                    info.str.append(String.format("%s = fadd %s %s, 0xK49284756365758473845\n", temp, type, rhs));
                } else if (type.isFloat()) {
                    info.str.append(String.format("%s = fadd %s %s, 1.230000e+02\n", temp, type, rhs));
                } else {
                    info.str.append(String.format("%s = or %s %s, 1\n", temp, type, rhs));
                }

                rhs = temp;
            }

            String id = idCounter.nextId();

            info.str.append(String.format("%s = %s %s\n", id, op, op.formatter.format(type, info.loadedValues.v0, rhs)));
            storeAndCheck(idCounter, strings, info.str, id, type, info.out,
                            String.format("%s %s", op, op.formatter.format(type, "%left", "%right")));
        } else {
            if (debug) {
                System.err.printf("%s: Output %s does not fit for operation %s\n", progName, type, op);
            }
        }

        return genPostfix(info);
    }

    /**
     * Generate the contents of the "run" function for unary operations that gets two input pointers
     * (ignores the second one) and an output pointer and performs the requested combination of type
     * and operation.
     */
    private static Optional<StringBuilder> genUnary(boolean debug, String progName, IDCounter idCounter, StringDB strings, Type type, UnaryOp op,
                    boolean includeStores) {
        Info info = genPrefix(idCounter, strings, type, includeStores);

        for (Type src : allTypes) {
            if (op.fits(src, type)) {
                info.generated = true;
                String id = idCounter.nextId();

                LoadedValues srcLoadedValues = genBitcastAndLoad(idCounter, info, src);

                info.str.append(String.format("%s = %s %s\n", id, op, op.formatter.format(src, srcLoadedValues.v0, type)));
                storeAndCheck(idCounter, strings, info.str, id, type, info.out,
                                String.format("%s %s", op, op.formatter.format(src, "%val", type)));
            } else {
                if (debug) {
                    System.err.printf("%s: Input %s and output %s do not fit for operation %s\n", progName, src, type, op);
                }
            }
        }

        return genPostfix(info);
    }

    /**
     * @param debug is ignored here.
     * @param progName is ignored here.
     */
    private static Optional<StringBuilder> genCustom(boolean debug, String progName, IDCounter idCounter, StringDB strings, Type type, String op,
                    boolean includeStores) {
        Info info = genPrefix(idCounter, strings, type, includeStores);

        // Simply store all types back to the output array.
        storeAndCheck(idCounter, strings, info.str, info.loadedValues.v0, type, info.out, String.format("%s %s", op, type));
        info.generated = true;

        return genPostfix(info);
    }

    private interface Generator<O> {
        Optional<StringBuilder> generate(boolean debug, String progName, IDCounter idCounter, StringDB strings, Type type, O op,
                        boolean includeStores);
    }

    private static <O> void genFile(boolean debug, String progName, String prelude, String filename, Type type, Generator<O> gen, O op,
                    boolean includeStores, boolean printFilename, Set<String> filenameBlacklist) throws FileNotFoundException {
        StringBuilder str = new StringBuilder();
        str.append(String.format("; Generated by %s.\n", progName));
        str.append(prelude);

        String finalFilename = filename;

        if (filenameBlacklist.contains(filename)) {
            finalFilename += ".ignore";
            if (debug) {
                System.err.printf(progName + ": Appending .ignore to %s (%s) because it is blacklisted\n", filename, finalFilename);
            }
        }

        StringDB strings = new StringDB();
        Optional<StringBuilder> contents = gen.generate(debug, progName, new IDCounter(), strings, type, op, includeStores);

        if (!contents.isPresent()) {
            if (debug) {
                System.err.printf(progName + ": Ignoring writing to %s because no contents were generated\n", finalFilename);
            }
            return;
        }

        if (printFilename) {
            System.out.println(finalFilename);
            return;
        }

        str.append(strings.get());
        str.append(contents.get());

        try (PrintStream out = new PrintStream(new FileOutputStream(finalFilename))) {
            out.print(str);
        }
    }

    private static String makeFilename(String base, String op, String type) {
        return new File(base, String.format("%s_%s.ll", op, type)).toString();
    }

    private static String makeBlacklistFilename(String base, String name) {
        return new File(base, String.format("%s.ll", name)).toString();
    }

    private static void helpAndDie(String message) {
        System.err.printf("Error: %s\n", message);
        System.err.println("Usage: LLIRTestGen <OUTPUT_DIR> [--separate-stores] [--print-filenames] [--debug]");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 3) {
            helpAndDie("Invalid number of arguments");
        }

        String outputDir = args[0];
        new File(outputDir).mkdirs();

        boolean separateStores = false;
        boolean printFilenames = false;
        boolean debug = false;

        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--separate-stores")) {
                separateStores = true;
            } else if (args[i].equals("--print-filenames")) {
                printFilenames = true;
            } else if (args[i].equals("--debug")) {
                debug = true;
            } else {
                helpAndDie("Unknown argument: " + args[i]);
            }
        }

        String prelude = null;

        if (!printFilenames) {
            InputStream preludeStream = new FileInputStream(System.getProperty("llirtestgen.prelude"));
            BufferedReader buffer = new BufferedReader(new InputStreamReader(preludeStream));
            prelude = buffer.lines().collect(Collectors.joining("\n"));
            prelude = prelude.replaceAll(", !tbaa ![0-9]+", "");
            prelude = prelude.replaceAll("declare[a-z_ ]* void @run.*\n", "");
        }

        String progName = LLIRTestGen.class.getCanonicalName();

        Set<String> filenameBlacklist = new HashSet<>(Arrays.asList("add_16xi1", "add_1xi1", "add_2xi1", "add_32xi1", "add_3xi1", "add_4xi1", "add_5xi1", "add_6xi1", "add_7xi1", "add_8xi1", "add_i1",
                        "and_16xi1", "and_1xi1", "and_2xi1", "and_32xi1", "and_3xi1", "and_4xi1", "and_5xi1", "and_6xi1", "and_7xi1", "and_8xi1", "and_i1", "ashr_16xi1", "ashr_16xi16", "ashr_16xi8",
                        "ashr_1xi1", "ashr_2xi1", "ashr_2xi16", "ashr_2xi32", "ashr_2xi64", "ashr_2xi8", "ashr_32xi1", "ashr_32xi8", "ashr_3xi1", "ashr_3xi16", "ashr_3xi32", "ashr_3xi32",
                        "ashr_3xi64", "ashr_4xi1", "ashr_4xi16", "ashr_4xi32", "ashr_4xi64", "ashr_5xi1", "ashr_5xi16", "ashr_5xi32", "ashr_5xi8", "ashr_6xi1", "ashr_6xi16", "ashr_6xi32", "ashr_6xi8",
                        "ashr_7xi1", "ashr_7xi16", "ashr_7xi32", "ashr_7xi8", "ashr_8xi1", "ashr_8xi16", "ashr_8xi32", "ashr_8xi8", "ashr_i1", "bitcast_16xi1", "bitcast_1xi1", "bitcast_2xi1",
                        "bitcast_32xi1", "bitcast_3xi1", "bitcast_4xi1", "bitcast_5xi1", "bitcast_6xi1", "bitcast_7xi1", "bitcast_8xi1", "bitcast_i1", "bitcast_i32", "fpext_x86_fp80", "fptosi_i1",
                        "fptosi_i16", "fptosi_i32", "fptosi_i64", "fptosi_i8", "fptoui_i1", "fptoui_i16", "fptoui_i32", "fptoui_i64", "fptoui_i8", "fptrunc_double", "fptrunc_float", "lshr_16xi1",
                        "lshr_16xi16", "lshr_16xi8", "lshr_1xi1", "lshr_2xi1", "lshr_2xi16", "lshr_2xi32", "lshr_2xi64", "lshr_32xi1", "lshr_32xi8", "lshr_3xi1", "lshr_3xi16", "lshr_3xi32",
                        "lshr_3xi64", "lshr_4xi1", "lshr_4xi16", "lshr_4xi32", "lshr_4xi64", "lshr_5xi1", "lshr_5xi16", "lshr_5xi32", "lshr_5xi8", "lshr_6xi1", "lshr_6xi16", "lshr_6xi32", "lshr_6xi8",
                        "lshr_7xi1", "lshr_7xi16", "lshr_7xi32", "lshr_7xi8", "lshr_8xi1", "lshr_8xi16", "lshr_8xi32", "lshr_8xi8", "lshr_i1", "mul_16xi1", "mul_1xi1", "mul_2xi1", "mul_32xi1",
                        "mul_3xi1", "mul_4xi1", "mul_5xi1", "mul_6xi1", "mul_7xi1", "mul_8xi1", "mul_i1", "or_16xi1", "or_1xi1", "or_2xi1", "or_32xi1", "or_3xi1", "or_4xi1", "or_5xi1", "or_6xi1",
                        "or_7xi1", "or_8xi1", "or_i1", "sdiv_i1", "sext_16xi16", "sext_16xi8", "sext_1xi16", "sext_1xi32", "sext_1xi64", "sext_1xi8", "sext_2xi16", "sext_2xi32", "sext_2xi64",
                        "sext_2xi8", "sext_32xi8", "sext_3xi16", "sext_3xi32", "sext_3xi64", "sext_3xi8", "sext_4xi16", "sext_4xi32", "sext_4xi64", "sext_4xi8", "sext_5xi16", "sext_5xi32",
                        "sext_5xi8", "sext_6xi16", "sext_6xi32", "sext_6xi8", "sext_7xi16", "sext_7xi32", "sext_7xi8", "sext_8xi16", "sext_8xi32", "sext_8xi8", "sext_i16", "sext_i32", "sext_i64",
                        "sext_i8", "shl_16xi1", "shl_16xi16", "shl_16xi8", "shl_1xi1", "shl_2xi1", "shl_2xi16", "shl_2xi32", "shl_2xi64", "shl_32xi1", "shl_32xi8", "shl_3xi1", "shl_3xi16",
                        "shl_3xi32", "shl_3xi64", "shl_4xi1", "shl_4xi16", "shl_4xi32", "shl_4xi64", "shl_5xi1", "shl_5xi16", "shl_5xi32", "shl_6xi1", "shl_6xi16", "shl_6xi32", "shl_7xi1",
                        "shl_7xi16", "shl_7xi32", "shl_8xi1", "shl_8xi16", "shl_8xi32", "shl_i1", "sitofp_double", "sitofp_float", "sitofp_x86_fp80", "srem_i1", "sub_16xi1", "sub_1xi1", "sub_2xi1",
                        "sub_32xi1", "sub_3xi1", "sub_4xi1", "sub_5xi1", "sub_6xi1", "sub_7xi1", "sub_8xi1", "sub_i1", "trunc_16xi1", "trunc_1xi1", "trunc_2xi1", "trunc_32xi1", "trunc_3xi1",
                        "trunc_4xi1", "trunc_5xi1", "trunc_6xi1", "trunc_7xi1", "trunc_8xi1", "trunc_i1", "udiv_i1", "uitofp_double", "uitofp_float", "uitofp_x86_fp80", "urem_i1", "xor_16xi1",
                        "xor_1xi1", "xor_2xi1", "xor_32xi1", "xor_3xi1", "xor_4xi1", "xor_5xi1", "xor_6xi1", "xor_7xi1", "xor_8xi1", "xor_i1", "zext_16xi16", "zext_16xi8", "zext_1xi16", "zext_1xi32",
                        "zext_1xi64", "zext_1xi8", "zext_2xi16", "zext_2xi32", "zext_2xi64", "zext_2xi8", "zext_32xi8", "zext_3xi16", "zext_3xi32", "zext_3xi64", "zext_3xi8", "zext_4xi16",
                        "zext_4xi32", "zext_4xi64", "zext_4xi8", "zext_5xi16", "zext_5xi32", "zext_5xi8", "zext_6xi16", "zext_6xi32", "zext_6xi8", "zext_7xi16", "zext_7xi32", "zext_7xi8",
                        "zext_8xi16", "zext_8xi32", "zext_8xi8", "zext_i16", "zext_i32", "zext_i64", "zext_i8",
                        "shl_5xi8", // Fails with LLVM 4.0, 6.0
                        "shl_6xi8", // Fails with LLVM 4.0, 6.0
                        "shl_7xi8", // Fails with LLVM 4.0, 6.0
                        "shl_8xi8", // Fails with LLVM 4.0, 6.0
                        "fmul_x86_fp80", // Fails with managed sulong
                        "fadd_x86_fp80", // Fails with managed sulong
                        "frem_x86_fp80", // Fails with managed sulong
                        "fsub_x86_fp80", // Fails with managed sulong
                        "fdiv_x86_fp80"  // Fails with managed sulong
        ));
        if (Platform.isAArch64()) {
            filenameBlacklist.addAll(Arrays.asList(
                            "ashr_3xi8", "ashr_4xi8", "bitcast_x86_fp80", "lshr_2xi8", "lshr_3xi8", "lshr_4xi8", "shl_2xi8", "shl_3xi8", "shl_4xi8"));
        }

        filenameBlacklist = filenameBlacklist.stream().map(s -> makeBlacklistFilename(outputDir, s)).collect(Collectors.toSet());

        for (Type type : allTypes) {
            String filename;

            if (separateStores) {
                filename = makeFilename(outputDir, "store", type.toName());
                genFile(debug, progName, prelude, filename, type, LLIRTestGen::genCustom, "store", false, printFilenames, filenameBlacklist);
            }

            for (UnaryOp op : UnaryOp.values()) {
                filename = makeFilename(outputDir, op.toString(), type.toName());
                genFile(debug, progName, prelude, filename, type, LLIRTestGen::genUnary, op, !separateStores, printFilenames, filenameBlacklist);
            }

            for (BinaryOp op : BinaryOp.values()) {
                filename = makeFilename(outputDir, op.toString(), type.toName());
                genFile(debug, progName, prelude, filename, type, LLIRTestGen::genBinary, op, !separateStores, printFilenames, filenameBlacklist);
            }
        }
    }
}
