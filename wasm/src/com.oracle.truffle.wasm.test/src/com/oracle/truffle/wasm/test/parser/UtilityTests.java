package com.oracle.truffle.wasm.test.parser;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.wasm.parser.binary.BinaryReader;

public class UtilityTests {

    @Test
    public void testUnsignedLEB128() {
        checkUnsignedLEB128(new byte[] {0x00}, 0);
        checkUnsignedLEB128(new byte[] {0x01}, 1);
        checkUnsignedLEB128(new byte[] {0x02}, 2);

        checkUnsignedLEB128(new byte[] {(byte) 0x82, 0x01}, 130);
        checkUnsignedLEB128(new byte[] {(byte) 0x8A, 0x02}, 266);
    }

    private void checkUnsignedLEB128(byte[] data, int expectedValue) {
        BinaryReader reader = new BinaryReader(data);
        int result = reader.readUnsignedLEB128();
        Assert.assertEquals(expectedValue, result);
    }

}
