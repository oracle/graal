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
package com.oracle.truffle.espresso.shared.jmh;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;

import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.shared.verifier.Verifier;

/**
 * Measures the shared bytecode verifier and class file parser on a JVM: {@link Verifier#verify}
 * called directly, with the arguments built by {@link BenchRuntime}.
 *
 * <p>
 * The workload is the one of {@code CremaClassLoadingBenchmark} in {@code substratevm}: generated
 * classes whose methods are long straight-line runs of the instructions a verifier spends its time
 * on (local variable loads and stores, operand stack manipulation, array accesses, calls), plus
 * large constant pools. {@link #verify} checks every method of every class; {@link #parse} turns
 * the class files into {@link ParserKlass} instances. The time is per pass over all classes, and
 * {@code instructions} in the first output line gives the size of a pass, so divide to get a cost
 * per instruction.
 *
 * <p>
 * {@link #hotspotLoad} gives the same classes to HotSpot's own verifier, by loading and linking
 * them in a fresh class loader, and {@link #hotspotLoadUnverified} does the same with verification
 * turned off; the difference of the two is what HotSpot's verifier costs. After each iteration of
 * either, a {@code [hotspot-verifier]} line also reports the time HotSpot measured in its verifier
 * itself ({@code sun.cls.classVerifyTime}) per pass.
 *
 * <p>
 * Run it from the {@code espresso-shared} directory with
 *
 * <pre>
 * mx benchmark "espresso-shared:*" -- --jvm=java-home --jvm-config=default
 * </pre>
 *
 * JMH options follow a second {@code --}. The JIT-compiled numbers are the question here, so it is
 * a steady-state benchmark; for the cost of the first pass, which is what a native image pays, add
 * JMH's {@code -bm ss -wi 0 -i 1 -f 10}. {@code -jvmArgsAppend -XX:TieredStopAtLevel=1} restricts
 * HotSpot to its simple compiler, which behaves like ahead-of-time compiled code.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class VerifierBenchmark {

    @Param({"40"}) public int classes;
    @Param({"8"}) public int methods;
    @Param({"120"}) public int statements;
    @Param({"300"}) public int constants;
    /** Branchy blocks per {@code flow} method: guarded blocks, a loop and a handler region. 0 turns the methods off. */
    @Param({"60"}) public int blocks;

    private static final MethodTypeDesc WORK = MethodTypeDesc.of(CD_int, CD_Object, CD_Object.arrayType(), CD_int);

    private static final String ADD_EXPORTS = "--add-exports=java.management/sun.management=ALL-UNNAMED";

    private byte[][] classFiles;
    private String[] classNames;
    /** {@code HotspotClassLoadingMBean.getClassVerificationTime()}, or null if not accessible. */
    private Method verificationTime;
    private Object hotspotClassLoading;
    private long verificationTimeAtStart;
    private int hotspotLoads;
    private BenchRuntime runtime;
    private final List<BenchRuntime.BMethod> toVerify = new ArrayList<>();
    private long instructions;
    private BytecodeStream[] codes;

    @Setup
    public void setup() throws Exception {
        classFiles = new byte[classes][];
        classNames = new String[classes];
        long[] counted = new long[1];
        for (int i = 0; i < classes; i++) {
            classFiles[i] = generate("cremabench/Generated" + i, counted);
            classNames[i] = "cremabench.Generated" + i;
        }
        instructions = counted[0];
        runtime = new BenchRuntime();
        for (byte[] classFile : classFiles) {
            toVerify.addAll(runtime.define(classFile).methods());
        }
        codes = new BytecodeStream[toVerify.size()];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = new BytecodeStream(toVerify.get(i).getCodeAttribute().getOriginalCode());
        }
        /* Fail early, and not in the middle of a measurement, if the model cannot verify them. */
        for (BenchRuntime.BMethod method : toVerify) {
            Verifier.verify(runtime, method);
        }
        PrintStream out = System.out;
        out.println("[verifier-benchmark] classes=" + classes + " methods=" + toVerify.size() + " instructions=" + instructions);
        try {
            /* Not an exported API: needs the --add-exports of the forks of the hotspot benchmarks. */
            hotspotClassLoading = Class.forName("sun.management.ManagementFactoryHelper").getMethod("getHotspotClassLoadingMBean").invoke(null);
            verificationTime = Class.forName("sun.management.HotspotClassLoadingMBean").getMethod("getClassVerificationTime");
        } catch (ReflectiveOperationException | RuntimeException e) {
            verificationTime = null;
        }
    }

    @Setup(Level.Iteration)
    public void startIteration() throws Exception {
        hotspotLoads = 0;
        verificationTimeAtStart = readVerificationTime();
    }

    @TearDown(Level.Iteration)
    public void endIteration() throws Exception {
        if (hotspotLoads > 0) {
            String perPass = verificationTime == null ? "unavailable" : String.format("%.3f ms", (double) (readVerificationTime() - verificationTimeAtStart) / hotspotLoads);
            PrintStream out = System.out;
            out.println("[hotspot-verifier] passes=" + hotspotLoads + " verification time per pass=" + perPass);
        }
    }

    private long readVerificationTime() throws ReflectiveOperationException {
        return verificationTime == null ? 0 : (long) verificationTime.invoke(hotspotClassLoading);
    }

    /** Verifies every method of every generated class. */
    @Benchmark
    public void verify() throws Exception {
        for (BenchRuntime.BMethod method : toVerify) {
            Verifier.verify(runtime, method);
        }
    }

    /**
     * Walks the instructions of every method once and does nothing else: the cost of decoding them,
     * which the verifier pays in each pass it makes over a method's bytecode.
     */
    @Benchmark
    public int decode() {
        int sum = 0;
        for (BytecodeStream code : codes) {
            for (int bci = 0; bci < code.endBCI(); bci = code.nextBCI(bci)) {
                sum += code.currentBC(bci);
            }
        }
        return sum;
    }

    /** Parses every generated class file, validating its constant pool, as for a verified class. */
    @Benchmark
    public void parse(Blackhole blackhole) throws Exception {
        for (byte[] classFile : classFiles) {
            blackhole.consume(runtime.parse(classFile));
        }
    }

    /**
     * Loads and links every generated class in a fresh class loader, so that HotSpot parses and
     * verifies them anew: the work of {@link #parse} and {@link #verify} done by HotSpot, plus the
     * rest of defining and linking a class.
     */
    @Benchmark
    @Fork(value = 2, jvmArgsAppend = ADD_EXPORTS)
    public void hotspotLoad(Blackhole blackhole) throws Exception {
        loadAll(blackhole);
    }

    /** {@link #hotspotLoad} with HotSpot's verification of the classes turned off. */
    @Benchmark
    @Fork(value = 2, jvmArgsAppend = {ADD_EXPORTS, "-XX:+UnlockDiagnosticVMOptions", "-XX:-BytecodeVerificationRemote"})
    public void hotspotLoadUnverified(Blackhole blackhole) throws Exception {
        loadAll(blackhole);
    }

    private void loadAll(Blackhole blackhole) throws ClassNotFoundException {
        GeneratedLoader loader = new GeneratedLoader(classNames, classFiles);
        for (String name : classNames) {
            /* Initializing a class links it, and linking verifies it. */
            blackhole.consume(Class.forName(name, true, loader));
        }
        hotspotLoads++;
    }

    /** Defines the generated classes, each when it is first asked for. */
    private static final class GeneratedLoader extends ClassLoader {
        private final String[] names;
        private final byte[][] classFiles;

        GeneratedLoader(String[] names, byte[][] classFiles) {
            super(ClassLoader.getPlatformClassLoader());
            this.names = names;
            this.classFiles = classFiles;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) {
                    return defineClass(name, classFiles[i], 0, classFiles[i].length);
                }
            }
            throw new ClassNotFoundException(name);
        }
    }

    private byte[] generate(String className, long[] counted) {
        ClassDesc self = ClassDesc.ofInternalName(className);
        return ClassFile.of().build(self, classBuilder -> {
            for (int m = 0; m < methods; m++) {
                int index = m;
                classBuilder.withMethodBody("work" + m, WORK, ACC_PUBLIC | ACC_STATIC, code -> work(code, self, index, counted));
            }
            if (blocks > 0) {
                for (int m = 0; m < methods; m++) {
                    classBuilder.withMethodBody("flow" + m, WORK, ACC_PUBLIC | ACC_STATIC, code -> flow(code, counted));
                }
            }
            classBuilder.withMethodBody("constants", MethodTypeDesc.of(CD_int), ACC_PUBLIC | ACC_STATIC, code -> {
                for (int c = 0; c < constants; c++) {
                    code.ldc(className + " constant " + c).pop();
                    counted[0] += 2;
                }
                code.iconst_0().ireturn();
                counted[0] += 2;
            });
            classBuilder.withMethodBody("answer", MethodTypeDesc.of(CD_int), ACC_PUBLIC | ACC_STATIC, code -> code.bipush(42).ireturn());
            counted[0] += 2;
        });
    }

    /**
     * One method of the form {@code int flowN(Object o, Object[] a, int n)} with control flow:
     * blocks of straight-line code that a forward branch may skip, each target having a stack map
     * frame, then a loop, then two regions each covered by a catch-all handler.
     */
    private void flow(CodeBuilder code, long[] counted) {
        for (int b = 0; b < blocks; b++) {
            Label skip = code.newLabel();
            code.iload(2).ifeq(skip);
            straight(code, counted);
            code.labelBinding(skip);
            counted[0] += 2;
        }
        Label top = code.newLabel();
        code.labelBinding(top);
        straight(code, counted);
        code.iinc(2, -1).iload(2).ifgt(top);
        counted[0] += 3;
        for (int region = 0; region < 2; region++) {
            code.trying(tryBlock -> {
                for (int b = 0; b < blocks; b++) {
                    straight(tryBlock, counted);
                }
            }, handlers -> handlers.catchingAll(handler -> handler.astore(5)));
            counted[0] += 1;
        }
        code.iload(2).ireturn();
        counted[0] += 2;
    }

    /** Ten instructions that store before they load, so they are valid after any join. */
    private static void straight(CodeBuilder code, long[] counted) {
        code.aload(0).astore(3);
        code.aload(1).iload(2).aaload().astore(4);
        code.iload(2).iconst_1().iadd().istore(2);
        counted[0] += 2 + 4 + 4;
    }

    /** One method of the form {@code int workN(Object o, Object[] a, int n)}. */
    private void work(CodeBuilder code, ClassDesc self, int index, long[] counted) {
        for (int s = 0; s < statements; s++) {
            code.aload(0).astore(3);
            code.aload(1).iload(2).aaload().astore(4);
            code.aload(3).aload(4).pop().pop();
            code.iload(2).iconst_1().iadd().istore(2);
            code.aload(1).arraylength().pop();
            code.aload(0).dup().pop().astore(3);
            counted[0] += 2 + 4 + 4 + 4 + 3 + 4;
            if (index > 0 && s % 10 == 0) {
                code.aload(0).aload(1).iload(2).invokestatic(self, "work" + (index - 1), WORK).istore(2);
                counted[0] += 5;
            }
        }
        code.iload(2).ireturn();
        counted[0] += 2;
    }
}
