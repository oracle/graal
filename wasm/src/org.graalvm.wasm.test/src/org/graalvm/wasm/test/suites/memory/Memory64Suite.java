package org.graalvm.wasm.test.suites.memory;

import java.io.IOException;

import org.graalvm.polyglot.Value;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Assert;
import org.junit.Test;

public class Memory64Suite extends AbstractBinarySuite {
    @Test
    public void test64BitMemory() throws IOException {
        final byte[] binary = hexToBinary("" +
                        "01 05 01 60 00 01 7F" +
                        "03 02 01 00" +
                        "05 04 01 05 01 01" +
                        "07 05 01 01 61 00 00" +
                        "0A 10 01 0E 00 42 00 41 01 36 00 00 42 00 28 00 00 0B");
        runRuntimeTest(binary, instance -> {
            Value a = instance.getMember("a");
            Value x = a.execute();
            Assert.assertTrue(x.fitsInInt());
            Assert.assertEquals(1, x.asInt());
        });
    }

    @Test
    public void testHugeMemory() throws IOException {
        final byte[] binary = hexToBinary("" +
                        "01 05 01 60 00 01 7F" +
                        "03 02 01 00" +
                        "05 04 01 05 01 01" +
                        "07 05 01 01 61 00 00" +
                        "0A 14 01 12 00 42 81 80 02 41 01 36 00 00 42 81 80 02 28 00 00 0B");
        runRuntimeTest(binary, instance -> {
            Value a = instance.getMember("a");
            Value x = a.execute();
            Assert.assertTrue(x.fitsInInt());
            Assert.assertEquals(1, x.asInt());
        });
    }
}
