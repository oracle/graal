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
package com.oracle.svm.test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;

/**
 * Measures how long Crema (runtime class loading in a native image) needs to turn class files into
 * linked classes: parsing the class file and verifying its bytecode.
 *
 * <p>
 * The program generates {@value #DEFAULT_CLASSES} classes per round whose methods are long
 * straight-line sequences of the instructions a bytecode verifier spends its time on (local
 * variable loads and stores, operand stack manipulation, array accesses, calls), plus large
 * constant pools. It then defines them with a user class loader, which is the case that is
 * verified by default, and links them. Defining a class parses it; linking it verifies it. Every
 * round uses fresh class names so that each one measures the cold path; the first rounds are
 * warm-up and are not reported.
 *
 * <p>
 * It is a stand-alone program and not a unit test on purpose: it needs an image of its own and
 * would add to the cost of every {@code native-unittest} run. Build and run it with the
 * {@code native-image} of the build under test, once per variant:
 *
 * <pre>
 * native-image -H:+UnlockExperimentalVMOptions -H:+RuntimeClassLoading \
 *     -cp &lt;svm-tests classes&gt; com.oracle.svm.test.CremaClassLoadingBenchmark
 * ./com.oracle.svm.test.cremaclassloadingbenchmark
 * </pre>
 *
 * Add {@code -R:ClassVerification=NONE} to the build to measure the same classes without
 * verification; the difference is what verification costs. The output is one line per measured
 * round and a summary line, all prefixed with {@code [crema-class-loading]}. Sizes can be changed
 * with the system properties {@code crema.bench.classes}, {@code crema.bench.methods},
 * {@code crema.bench.statements}, {@code crema.bench.constants}, {@code crema.bench.warmup} and
 * {@code crema.bench.rounds}. It checks that every generated class loaded and initialized, and
 * never judges the timings: comparing them is the reader's job.
 */
public final class CremaClassLoadingBenchmark {

    private static final int DEFAULT_CLASSES = 40;
    private static final int CLASSES = Integer.getInteger("crema.bench.classes", DEFAULT_CLASSES);
    private static final int METHODS = Integer.getInteger("crema.bench.methods", 8);
    private static final int STATEMENTS = Integer.getInteger("crema.bench.statements", 120);
    private static final int CONSTANTS = Integer.getInteger("crema.bench.constants", 300);
    private static final int WARMUP = Integer.getInteger("crema.bench.warmup", 3);
    private static final int ROUNDS = Integer.getInteger("crema.bench.rounds", 7);

    private static final String PREFIX = "[crema-class-loading] ";
    private static final MethodTypeDesc WORK = MethodTypeDesc.of(CD_int, CD_Object, CD_Object.arrayType(), CD_int);

    /** A class loader of our own: classes it defines are verified by default. */
    private static final class GeneratedClassLoader extends ClassLoader {
        GeneratedClassLoader() {
            super(CremaClassLoadingBenchmark.class.getClassLoader());
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    /** What one round generated and how long it took to load. */
    private record Round(int classes, long bytes, long instructions, long defineNanos, long linkNanos) {
        long totalNanos() {
            return defineNanos + linkNanos;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(PREFIX + "classes=" + CLASSES + " methods=" + METHODS + " statements=" + STATEMENTS + " constants=" + CONSTANTS +
                        " warmup=" + WARMUP + " rounds=" + ROUNDS);
        Round[] measured = new Round[ROUNDS];
        for (int round = 0; round < WARMUP + ROUNDS; round++) {
            Round result = runRound(round);
            if (round >= WARMUP) {
                measured[round - WARMUP] = result;
                System.out.println(PREFIX + String.format("round=%d define_ms=%.1f link_ms=%.1f total_ms=%.1f", round - WARMUP, ms(result.defineNanos()), ms(result.linkNanos()),
                                ms(result.totalNanos())));
            }
        }
        long[] define = Arrays.stream(measured).mapToLong(Round::defineNanos).sorted().toArray();
        long[] link = Arrays.stream(measured).mapToLong(Round::linkNanos).sorted().toArray();
        long[] total = Arrays.stream(measured).mapToLong(Round::totalNanos).sorted().toArray();
        Round shape = measured[0];
        System.out.println(PREFIX + String.format("summary classes=%d class_file_kb=%d instructions=%d median_define_ms=%.1f median_link_ms=%.1f median_total_ms=%.1f " +
                        "median_total_ns_per_instruction=%.0f", shape.classes(), shape.bytes() / 1024, shape.instructions(), ms(median(define)), ms(median(link)), ms(median(total)),
                        (double) median(total) / shape.instructions()));
    }

    /** Generates a fresh set of classes, then times defining and linking them. */
    private static Round runRound(int round) throws Exception {
        GeneratedClassLoader loader = new GeneratedClassLoader();
        String[] names = new String[CLASSES];
        byte[][] bytecode = new byte[CLASSES][];
        long[] instructions = new long[1];
        long bytes = 0;
        for (int i = 0; i < CLASSES; i++) {
            names[i] = "cremabench.r" + round + ".Generated" + i;
            bytecode[i] = generate(names[i], instructions);
            bytes += bytecode[i].length;
        }

        Class<?>[] defined = new Class<?>[CLASSES];
        long start = System.nanoTime();
        for (int i = 0; i < CLASSES; i++) {
            defined[i] = loader.define(names[i], bytecode[i]);
        }
        long defined0 = System.nanoTime();
        for (int i = 0; i < CLASSES; i++) {
            Class<?> linked = Class.forName(names[i], true, loader);
            if (linked != defined[i]) {
                throw new AssertionError("Class loaded twice: " + names[i]);
            }
        }
        long linked0 = System.nanoTime();

        /* Initialization ran, so the classes were linked (and verified); prove it. */
        for (Class<?> clazz : defined) {
            Object answer = clazz.getMethod("answer").invoke(null);
            if (!Integer.valueOf(42).equals(answer)) {
                throw new AssertionError(clazz.getName() + ".answer() returned " + answer);
            }
        }
        return new Round(CLASSES, bytes, instructions[0], defined0 - start, linked0 - defined0);
    }

    /**
     * Generates one class: {@code METHODS} long straight-line methods, a method with
     * {@code CONSTANTS} string constants, and {@code answer()}.
     */
    private static byte[] generate(String className, long[] instructions) {
        ClassDesc self = ClassDesc.of(className);
        return ClassFile.of().build(self, classBuilder -> {
            for (int m = 0; m < METHODS; m++) {
                int index = m;
                classBuilder.withMethodBody("work" + m, WORK, ACC_PUBLIC | ACC_STATIC, code -> work(code, self, index, instructions));
            }
            classBuilder.withMethodBody("constants", MethodTypeDesc.of(CD_int), ACC_PUBLIC | ACC_STATIC, code -> {
                for (int c = 0; c < CONSTANTS; c++) {
                    code.ldc(className + " constant " + c).pop();
                    instructions[0] += 2;
                }
                code.iconst_0().ireturn();
                instructions[0] += 2;
            });
            classBuilder.withMethodBody("answer", MethodTypeDesc.of(CD_int), ACC_PUBLIC | ACC_STATIC, code -> code.bipush(42).ireturn());
            instructions[0] += 2;
        });
    }

    /**
     * One method of the form {@code int workN(Object o, Object[] a, int n)}: a long run of the
     * statements a verifier has to check one by one, each leaving the operand stack empty.
     */
    private static void work(CodeBuilder code, ClassDesc self, int index, long[] instructions) {
        for (int s = 0; s < STATEMENTS; s++) {
            code.aload(0).astore(3);                // local 3 = o
            code.aload(1).iload(2).aaload().astore(4); // local 4 = a[n]
            code.aload(3).aload(4).pop().pop();     // reference loads and pops
            code.iload(2).iconst_1().iadd().istore(2); // n++
            code.aload(1).arraylength().pop();      // array operand check
            code.aload(0).dup().pop().astore(3);    // stack duplication
            instructions[0] += 2 + 4 + 4 + 4 + 3 + 4;
            if (index > 0 && s % 10 == 0) {
                /* A call to the previous method: method reference, descriptor, and arguments. */
                code.aload(0).aload(1).iload(2).invokestatic(self, "work" + (index - 1), WORK).istore(2);
                instructions[0] += 5;
            }
        }
        code.iload(2).ireturn();
        instructions[0] += 2;
    }

    private static long median(long[] sorted) {
        return sorted[sorted.length / 2];
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }
}
