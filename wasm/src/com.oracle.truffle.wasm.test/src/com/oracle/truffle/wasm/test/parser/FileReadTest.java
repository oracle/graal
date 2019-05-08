package com.oracle.truffle.wasm.test.parser;


import com.oracle.truffle.wasm.parser.binary.BinaryReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileReadTest {

    @Test
    public void ParseHelloWorld() {
        try {
            byte[] data = Files.readAllBytes(new File("/home/ergys/dev/webassembly/hello.wasm").toPath());
            BinaryReader reader = new BinaryReader(data);
            reader.readModule();
        } catch (IOException exc) {
            Assert.fail("cannot open file: " + exc.getLocalizedMessage());
        }
    }

}
