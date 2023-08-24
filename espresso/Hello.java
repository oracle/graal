//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Hello {
    public Hello() {
    }

    public static void main(String[] var0) {
        Pattern pattern = Pattern.compile("(?:na)+batma\\X");
        System.out.println("-- Pattern (unsupported feature): " + pattern.pattern());

        String[] inputs = new String[]{"nananabatman", "nananbatman", "batman", "nananananananabatman"};

        for (String in: inputs) {
            System.out.println("Input: " + in);
            boolean res = pattern.matcher(in).matches();
            System.out.println("Result: " + res);
        }

        pattern = Pattern.compile("(?:na)+batman");
        System.out.println("-- Pattern: " + pattern.pattern());

        inputs = new String[]{"nananabatman", "nananbatman", "batman", "nananananananabatman"};

        for (String in: inputs) {
            System.out.println("Input: " + in);
            boolean res = pattern.matcher(in).matches();
            System.out.println("Result: " + res);
        }

    }
}
