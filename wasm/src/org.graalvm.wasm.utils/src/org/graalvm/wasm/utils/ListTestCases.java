package org.graalvm.wasm.utils;

import java.io.IOException;

public class ListTestCases {
    public static void main(String[] args) throws IOException {
        Assert.assertTrue("Usage: [resource]", args.length == 1 && !args[0].isEmpty());
        final String path = args[0].charAt(0) == '/' ? args[0] : "/" + args[0];
        final String result = WasmResource.getResourceAsString(path + "/" + "wasm_test_index", true);
        System.out.println(result);
    }
}
