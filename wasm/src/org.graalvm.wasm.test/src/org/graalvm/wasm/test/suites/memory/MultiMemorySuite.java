package org.graalvm.wasm.test.suites.memory;

import org.graalvm.wasm.test.WasmFileSuite;
import org.junit.Test;

import java.io.IOException;

public class MultiMemorySuite extends WasmFileSuite {
    @Override
    protected String testResource() {
        return "multi-memory";
    }

    @Override
    @Test
    public void test() throws IOException {
        // This is here just to make mx aware of the test suite class.
        super.test();
    }
}
