package org.graalvm.wasm.test.suites.control;

import java.io.IOException;

import org.junit.Test;

import org.graalvm.wasm.test.WasmFileSuite;

public class TailCallSuite extends WasmFileSuite {
    @Override
    protected String testResource() {
        return "tail-call";
    }

    @Override
    @Test
    public void test() throws IOException {
        // This is here just to make mx aware of the test suite class.
        super.test();
    }
}
