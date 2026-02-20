package com.oracle.svm.test;

import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.functions.JNIFunctions;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import org.junit.Test;

public class JNIPerformanceTest {
    @Test
    public void measureJNIPerformance() {
        JNIEnvironment env = JNIThreadLocalEnvironment.getAddress();
        long start = System.nanoTime();

        for (int i = 0; i < 100_000_000; i++) {
            // any JNI function
            JNIFunctions.GetVersion(env);
        }

        long end = System.nanoTime();
        long timeMs = (end - start) / 1_000_000;

        System.out.println("Execution time: " + timeMs + " ms");
    }
}
