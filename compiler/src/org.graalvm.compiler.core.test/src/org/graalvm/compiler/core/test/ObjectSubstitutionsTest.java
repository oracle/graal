package org.graalvm.compiler.core.test;

import org.junit.Test;

public class ObjectSubstitutionsTest extends GraalCompilerTest {

    public static int SideEffect;

    public static final void notifySnippet() {
        synchronized (ObjectSubstitutionsTest.class) {
            SideEffect = System.identityHashCode(ObjectSubstitutionsTest.class);
            ObjectSubstitutionsTest.class.notifyAll();
        }
    }

    @Test
    public void testNotifyAllEmpty() {
        test("notifySnippet");
    }

}
