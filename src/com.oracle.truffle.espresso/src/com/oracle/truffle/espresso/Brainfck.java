package com.oracle.truffle.espresso;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Brainfck {
    private Scanner sc = new Scanner(System.in);
    private final int LENGTH = 1 << 16;
    private int[] mem = new int[LENGTH];
    private int pc;

    public void interpret(String code) {
        int l = 0;
        for (int i = 0; i < code.length(); i++) {
            switch (code.charAt(i)) {
                case '>':
                    pc = (pc == LENGTH - 1) ? 0 : pc + 1;
                    break;
                case '<':
                    pc = (pc == 0) ? LENGTH - 1 : pc - 1;
                    break;
                case '+':
                    mem[pc]++;
                    break;
                case '-':
                    mem[pc]--;
                    break;
                case '.':
                    System.out.print((char) mem[pc]);
                    break;
                case ',':
                    mem[pc] = (byte) sc.next().charAt(0);
                    break;
                case '[':
                    if (mem[pc] == 0) {
                        i++;
                        while (l > 0 || code.charAt(i) != ']') {
                            if (code.charAt(i) == '[')
                                l++;
                            if (code.charAt(i) == ']')
                                l--;
                            i++;
                        }
                    }
                    break;
                case ']':
                    if (mem[pc] != 0) {
                        i--;
                        while (l > 0 || code.charAt(i) != '[') {
                            if (code.charAt(i) == ']')
                                l++;
                            if (code.charAt(i) == '[')
                                l--;
                            i--;
                        }
                        i--;
                    }
                    break;
            }
        }
    }

    private static String readFile(String path) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try (FileInputStream fis = new FileInputStream(path)) {
            int bytesRead = 0;
            do {
                bytesRead = fis.read(buf);
                if (bytesRead > 0) {
                    bytes.write(buf, 0, bytesRead);
                }
            } while (bytesRead == buf.length);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        String program = readFile(args[0]);
        new Brainfck().interpret(program);
    }
}