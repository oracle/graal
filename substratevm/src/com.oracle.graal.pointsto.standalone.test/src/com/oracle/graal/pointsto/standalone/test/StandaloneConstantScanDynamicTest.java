/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test;

import org.junit.Test;

/**
 * This test shall fail because it makes the analysis code itself reachable if the classes are not
 * separated by classloaders.
 *
 * The exception stack is:
 * 
 * <pre>
 *     Exception:com.oracle.graal.pointsto.util.ParallelExecutionException
 *         at com.oracle.graal.pointsto.util.CompletionExecutor.complete(CompletionExecutor.java:261)
 *         at com.oracle.graal.pointsto.PointsToAnalysis.doTypeflow(PointsToAnalysis.java:528)
 *         at com.oracle.graal.pointsto.PointsToAnalysis.finish(PointsToAnalysis.java:516)
 *         at com.oracle.graal.pointsto.AbstractAnalysisEngine.runAnalysis(AbstractAnalysisEngine.java:161)
 *         at com.oracle.graal.pointsto.standalone.PointsToAnalyzer.run(PointsToAnalyzer.java:278)
 *         at com.oracle.graal.pointsto.standalone.test.PointstoAnalyzerTester.runAnalysisAndAssert(PointstoAnalyzerTester.java:159)
 *         at com.oracle.graal.pointsto.standalone.test.PointstoAnalyzerTester.runAnalysisAndAssert(PointstoAnalyzerTester.java:145)
 *         at com.oracle.graal.pointsto.standalone.test.StandaloneConstantScanDynamicTest.test(StandaloneConstantScanDynamicTest.java:43)
 *         at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 *         at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
 *         at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
 *         at java.base/java.lang.reflect.Method.invoke(Method.java:568)
 *         at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
 *         at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
 *         at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
 *         at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
 *         at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
 *         at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
 *         at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
 *         at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
 *         at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
 *         at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
 *         at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
 *         at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
 *         at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
 *         at org.junit.runners.Suite.runChild(Suite.java:128)
 *         at org.junit.runners.Suite.runChild(Suite.java:27)
 *         at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
 *         at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
 *         at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
 *         at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
 *         at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
 *         at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
 *         at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
 *         at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
 *         at com.oracle.mxtool.junit.MxJUnitWrapper.runRequest(MxJUnitWrapper.java:375)
 *         at com.oracle.mxtool.junit.MxJUnitWrapper.main(MxJUnitWrapper.java:230)
 * cause 0jdk.graal.compiler.debug.GraalError: jdk.graal.compiler.debug.GraalError: jdk.graal.compiler.debug.GraalError:
 * should not reach here: Double wrapping of constant. Most likely, the reachability analysis code itself is seen as reachable. java.lang.Object[]
 *         at com.oracle.graal.pointsto.util.AnalysisFuture.setException(AnalysisFuture.java:49)
 *         at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:269)
 *         at com.oracle.graal.pointsto.util.AnalysisFuture.ensureDone(AnalysisFuture.java:63)
 *         at com.oracle.graal.pointsto.heap.ImageHeapObjectArray.readElementValue(ImageHeapObjectArray.java:84)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.onObjectReachable(ImageHeapScanner.java:463)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.lambda$markReachable$5(ImageHeapScanner.java:451)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.lambda$postTask$14(ImageHeapScanner.java:690)
 *         at com.oracle.graal.pointsto.util.CompletionExecutor.executeCommand(CompletionExecutor.java:187)
 *         at com.oracle.graal.pointsto.util.CompletionExecutor.lambda$executeService$0(CompletionExecutor.java:171)
 *         at java.base/java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1395)
 *         at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:373)
 *         at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1182)
 *         at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1655)
 *         at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1622)
 *         at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:165)
 * Caused by: jdk.graal.compiler.debug.GraalError: jdk.graal.compiler.debug.GraalError: should not reach here: Double wrapping of constant. Most likely, the reachability analysis code itself is seen as reachable. java.lang.Object[]
 *         at com.oracle.graal.pointsto.util.AnalysisFuture.setException(AnalysisFuture.java:49)
 *         at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:269)
 *         at com.oracle.graal.pointsto.util.AnalysisFuture.ensureDone(AnalysisFuture.java:63)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.getOrCreateImageHeapConstant(ImageHeapScanner.java:212)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.createImageHeapConstant(ImageHeapScanner.java:186)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.lambda$createImageHeapObjectArray$3(ImageHeapScanner.java:270)
 *         at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
 *         ... 13 more
 * Caused by: jdk.graal.compiler.debug.GraalError: should not reach here: Double wrapping of constant. Most likely, the reachability analysis code itself is seen as reachable. java.lang.Object[]
 *         at jdk.graal.compiler/jdk.graal.compiler.debug.GraalError.shouldNotReachHere(GraalError.java:57)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.maybeReplace(ImageHeapScanner.java:307)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.createImageHeapObject(ImageHeapScanner.java:225)
 *         at com.oracle.graal.pointsto.heap.ImageHeapScanner.lambda$getOrCreateImageHeapConstant$2(ImageHeapScanner.java:205)
 *         at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
 *         ... 18 more
 * </pre>
 */
public class StandaloneConstantScanDynamicTest {

    @Test
    public void test() throws NoSuchMethodException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(StandaloneConstantScanDynamicCase.class);
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
        tester.setExpectedReachableMethods(tester.getTestClass().getDeclaredMethod("run"),
                        tester.getTestClass().getDeclaredMethod("doSomething"));
        tester.runAnalysisAndAssert();
    }
}
