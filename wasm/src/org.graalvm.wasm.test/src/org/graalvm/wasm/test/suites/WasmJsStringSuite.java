package org.graalvm.wasm.test.suites;

import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.test.WasmFileSuite;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WasmJsStringSuite extends WasmFileSuite {

    private Value execute(String wat, Object... arguments) throws IOException, InterruptedException {
        final ByteSequence binaryMain = ByteSequence.create(compileWat("main", wat));
        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.Builtins", "js-string").allowHostAccess(HostAccess.ALL).build()) {
            Value exports = context.eval(sourceMain).newInstance().getMember("exports");
            final Value main = exports.getMember("_main");
            Object[] args = Arrays.stream(arguments).map(arg -> {
                if (arg instanceof String s) return TruffleString.fromJavaStringUncached(s, TruffleString.Encoding.UTF_16);
                return arg;
            }).toArray();
            return main.execute(args);
        }
    }

    private void test(String wat, Object expected, Object... arguments) throws IOException, InterruptedException {
        if (expected instanceof String) {
            assertEquals(expected, execute(wat, arguments).asString());
        }
        else if (expected instanceof Integer) {
            assertEquals(expected, execute(wat, arguments).asInt());
        }
        /*else if (expected instanceof Integer[]) {
            var result = execute(wat, arguments);
            var host = result.asHostObject();
            assertEquals(expected, host);
        }*/
        else {
            throw new UnsupportedOperationException(String.format("Type of %s is not supported in the test.", expected.getClass().getName()));
        }
    }

    private void testThrows(String wat, Object... arguments) {
        assertThrows(PolyglotException.class, () -> execute(wat, arguments));
    }

    @Test
    public void Length() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "length" (func $length (param externref) (result i32)))

    (func $main (export "_main") (param externref) (result i32)
        local.get 0
        call $length
    )
)
        """;
        testThrows(wat, (Object) null);
        testThrows(wat, new Object());
        testThrows(wat, 1);

        test(wat, 0, "");
        test(wat, 11, "hello world");
        test(wat, 1, "⇔");
        test(wat, 2, "\uD83D\uDC4D");
        test(wat, 2, "👍");
    }

    @Test
    public void Substring() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "substring" (func $substring (param externref) (param i32) (param i32) (result externref)))

    (func $main (export "_main") (param externref) (param i32) (param i32) (result externref)
        local.get 0
        local.get 1
        local.get 2
        call $substring
    )
)
        """;
        testThrows(wat, null, 0, 1);
        testThrows(wat, new Object(), 0, 1);
        testThrows(wat, 0, 0, 1);
        test(wat, "", "hello world", 5, 5);
        test(wat, "hello", "hello world", 0, 5);
        test(wat, " world", "hello world", 5, 11);
        test(wat, "lo wo", "hello world", 3, 8);
        test(wat, "", "hello world", 8, 3); // discrepancy, proposal defines this case as "" while js flips start and end and would return "lo wo"
        test(wat, "hello world", "hello world", 0, 11);
        test(wat, "hello w", "hello world", -1, 7);
        test(wat, "hello world", "hello world", -250, 250);
        test(wat, "o world", "hello world", 4, 250);
        test(wat, "\uD83D", "👍", 0, 1);
        test(wat, "\uD83D\uDC4D", "👍", 0, 2);
        test(wat, "o\uD83D", "hello👍world", 4, 6);
        test(wat, "o\uD83D\uDC4Dw", "hello👍world", 4, 8);
    }

    @Test
    public void CharCodeAt() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "charCodeAt" (func $charCodeAt (param externref) (param i32) (result i32)))

    (func $main (export "_main") (param externref) (param i32) (result i32)
        local.get 0
        local.get 1
        call $charCodeAt
    )
)
        """;
        testThrows(wat, null, 0);
        testThrows(wat, new Object(), 0);
        testThrows(wat, 1, 0);
        testThrows(wat, "", 0);
        test(wat, 0, "\u0000", 0);
        test(wat, 255, "ÿ", 0);
        test(wat, 256, "Ā", 0);
        test(wat, 104, "hello world", 0);
        testThrows(wat, "hello world", -1);
        test(wat, 100, "hello world", 10);
        testThrows(wat, "hello world", 11);
        testThrows(wat, "hello world", 123);
        test(wat, 55357, "👍", 0);
        test(wat, 56397, "👍", 1);
        test(wat, 65535, "\uFFFF", 0);
    }

    @Test
    public void CodePointAt() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "codePointAt" (func $codePointAt (param externref) (param i32) (result i32)))

    (func $main (export "_main") (param externref) (param i32) (result i32)
        local.get 0
        local.get 1
        call $codePointAt
    )
)
        """;
        testThrows(wat, null, 0);
        testThrows(wat, new Object(), 0);
        testThrows(wat, 1, 0);
        testThrows(wat, "", 0);
        test(wat, 0, "\u0000", 0);
        test(wat, 255, "ÿ", 0);
        test(wat, 256, "Ā", 0);
        test(wat, 104, "hello world", 0);
        testThrows(wat, "hello world", -1);
        test(wat, 100, "hello world", 10);
        testThrows(wat, "hello world", 11);
        testThrows(wat, "hello world", 123);

        testThrows(wat, "☃★♲", -1);
        test(wat, 9731, "☃★♲", 0);
        test(wat, 9733, "☃★♲", 1);
        test(wat, 9842, "☃★♲", 2);
        testThrows(wat, "☃★♲", 3);
        test(wat, 128077, "👍", 0);
        test(wat, 56397, "👍", 1);
        test(wat, 65535, "\uFFFF", 0);
    }

    @Test
    public void Compare() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "compare" (func $compare (param externref) (param externref) (result i32)))

    (func $main (export "_main") (param externref) (param externref) (result i32)
        local.get 0
        local.get 1
        call $compare
    )
)
        """;
        testThrows(wat, null, null);
        testThrows(wat, "", null);
        testThrows(wat, null, "");
        testThrows(wat, new Object(), "");
        testThrows(wat, "", new Object());
        testThrows(wat, 1, 1);
        testThrows(wat, "", 1);
        testThrows(wat, new Object(), 1);
        testThrows(wat, 1, new Object());
        testThrows(wat, 1, null);
        testThrows(wat, null, new Object());
        test(wat,0,"a","a");
        test(wat,-1,"a","b");
        test(wat,1,"b","a");
        test(wat,0,"","");
        test(wat,-1,"","a");
        test(wat,1,"a","");
    }

    @Test
    public void Cast() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "cast" (func $cast (param externref) (result externref)))

    (func $main (export "_main") (param externref) (result externref)
        local.get 0
        call $cast
    )
)
        """;
        test(wat,"a","a");
        test(wat,"","");
        testThrows(wat, (Object) null);
        testThrows(wat,new Object());
        testThrows(wat,1);
    }

    @Test
    public void Test() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "test" (func $test (param externref) (result i32)))

    (func $main (export "_main") (param externref) (result i32)
        local.get 0
        call $test
    )
)
        """;
        test(wat,1,"a");
        test(wat,1,"");
        test(wat, 0, (Object) null);
        test(wat, 0, new Object());
        test(wat,0, 1);
    }

    @Test
    public void FromCharCode() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "fromCharCode" (func $fromCharCode (param i32) (result externref)))

    (func $main (export "_main") (param i32) (result externref)
        local.get 0
        call $fromCharCode
    )
)
        """;
        test(wat, "\u0000", 0);
        test(wat, "ÿ", 255);
        test(wat, "Ā", 256);
        test(wat, "h", 104);
        test(wat, "d", 100);
        test(wat, "\uD83D", 55357);
        test(wat, "\uDC4D", 56397);
        test(wat, "\uFFFF", 65535);
        test(wat, "\u0000", 65536);
        test(wat, "\uFFFF", -1);
    }

    @Test
    public void FromCodePoint() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "fromCodePoint" (func $fromCodePoint (param i32) (result externref)))

    (func $main (export "_main") (param i32) (result externref)
        local.get 0
        call $fromCodePoint
    )
)
        """;
        testThrows(wat, -1);

        test(wat, "\u0000", 0);
        test(wat, "ÿ", 255);
        test(wat, "Ā", 256);
        test(wat, "h", 104);
        test(wat, "d", 100);

        test(wat, "☃", 9731);
        test(wat, "★", 9733);
        test(wat, "♲", 9842);
        test(wat, "👍", 128077);
        test(wat, "\uDC4D", 56397);
        test(wat, "\uFFFF", 65535);

        test(wat, "􏿿", 0x10FFFF);
        testThrows(wat, 0x10FFFF + 1);
    }

    @Test
    public void Concat() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "concat" (func $concat (param externref) (param externref) (result externref)))

    (func $main (export "_main") (param externref) (param externref) (result externref)
        local.get 0
        local.get 1
        call $concat
    )
)
        """;
        testThrows(wat, null, null);
        testThrows(wat, null, "");
        testThrows(wat, "", null);
        testThrows(wat, new Object(), new Object());
        testThrows(wat, new Object(), null);
        testThrows(wat, new Object(), "");
        testThrows(wat, "", 0);
        testThrows(wat, 1, 0);
        test(wat,"","","");
        test(wat,"a","a","");
        test(wat,"b","","b");
        test(wat,"ab","a","b");
        test(wat,"👍","\uD83D","\uDC4D");
    }

    @Test
    public void Equals() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "equals" (func $equals (param externref) (param externref) (result i32)))

    (func $main (export "_main") (param externref) (param externref) (result i32)
        local.get 0
        local.get 1
        call $equals
    )
)
        """;
        test(wat, 1, null, null);
        test(wat, 0, "", null);
        test(wat, 0, null, "");
        testThrows(wat, "", new Object());
        testThrows(wat, new Object(), "");
        testThrows(wat, "", 1);
        testThrows(wat, 1, "");
        test(wat,1,"","");
        test(wat,0,"a","");
        test(wat,0,"","b");
        test(wat,0,"a","b");
        test(wat,1,"a","a");
        test(wat,0,"aa","a");
        test(wat,0,"b","bb");
        test(wat,0,"\uD83D","\uDC4D");
        test(wat,1,"\uD83D\uDC4D","👍");
    }

    @Test
    public void FromCharCodeArray() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "fromCharCodeArray" (func $fromCharCodeArray (param externref) (param i32) (param i32) (result externref)))

    (func $main (export "_main") (param externref) (param i32) (param i32) (result externref)
        local.get 0
        local.get 1
        local.get 2
        call $fromCharCodeArray
    )
)
        """;
        test(wat, "hello", new Integer[]{104, 101, 108, 108, 111}, 0, 5);
        test(wat, "ell", new Integer[]{104, 101, 108, 108, 111}, 1, 4);
        test(wat, "h", new Integer[]{104, 101, 108, 108, 111}, 0, 1);
        test(wat, "", new Integer[]{104, 101, 108, 108, 111}, 1, 1);
        test(wat, "", new Integer[]{104, 101, 108, 108, 111}, 4, 4);
        testThrows(wat, new Integer[]{104, 101, 108, 108, 111}, 0, 6);
        testThrows(wat, new Integer[]{104, 101, 108, 108, 111}, -1, 5);
        testThrows(wat, new Integer[]{104, 101, 108, 108, 111}, 4, 0);
        testThrows(wat, new Integer[]{104, 101, 108, 108, 111}, 1, 0);
        testThrows(wat, new Integer[]{104, 101, 108, 108, 111}, 4, -1);
        test(wat, "", new Integer[0], 0, 0);
        testThrows(wat, new Integer[0], 0, 1);
        test(wat, "", new Integer[1], 0, 0);
        testThrows(wat, new Integer[1], 0, 1);
        test(wat, "h", new Integer[]{104}, 0, 1);
        testThrows(wat, 0, 0, 1);
        testThrows(wat, 0, 0, 0);
        test(wat, "", new String[]{"hello"}, 0, 0);
        testThrows(wat, new String[]{"hello"}, 0, 1);
        testThrows(wat, null, 0, 0);
        testThrows(wat, null, 0, 1);
    }

    @Test
    public void IntoCharCodeArray() throws IOException, InterruptedException {
        var wat = """
(module
    (import "js-string" "intoCharCodeArray" (func $intoCharCodeArray (param externref) (param externref) (param i32) (result i32)))

    (func $main (export "_main") (param externref) (param externref) (param i32) (result i32)
        local.get 0
        local.get 1
        local.get 2
        call $intoCharCodeArray
    )
)
        """;
        var expected = new Integer[]{104};
        var arr = new Integer[1];
        execute(wat, "h", arr, 0);
        assertEquals(expected.length, arr.length);
        for (int i = 0; i < arr.length; i++) {
            assertEquals(expected[i],arr[i]);
        }
        expected = new Integer[]{104, 101, 108, 108, 111};
        arr = new Integer[5];
        execute(wat, "hello", arr, 0);
        assertEquals(expected.length, arr.length);
        for (int i = 0; i < arr.length; i++) {
            assertEquals(expected[i],arr[i]);
        }
        expected = new Integer[1];
        arr = new Integer[1];
        execute(wat, "", arr, 0);
        assertEquals(expected.length, arr.length);
        for (int i = 0; i < arr.length; i++) {
            assertEquals(expected[i],arr[i]);
        }
        expected = new Integer[]{100, 100, 100, 104, 101, 108, 108, 111};
        arr = new Integer[]{100, 100, 100, null, 3, 0, 108, 100};
        execute(wat, "hello", arr, 3);
        assertEquals(expected.length, arr.length);
        for (int i = 0; i < arr.length; i++) {
            assertEquals(expected[i],arr[i]);
        }
        testThrows(wat, "hello", new Integer[5], -1);
        testThrows(wat, "hello", new Integer[5], 5);
        testThrows(wat, "", new Integer[0], -1);
        testThrows(wat, "a", new Integer[0], 0);
    }
}
