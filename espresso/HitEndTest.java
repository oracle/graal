import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final int WARMUP = 25;
    private static final int ITER = 20;
    public static void main(String[] args) {
        System.out.println("Check requireEnd...");
        Matcher m = Pattern.compile("abc$").matcher("abc");
        System.out.println(m.find());
        System.out.println(m.requireEnd());

        // make sure that the re-execution takes place with the same parameters (bounds)
        System.out.println("Check bounds...");
        // TODO fix implementation (save region)
        Matcher m2 = Pattern.compile("abc").matcher("abcabc");
        m2.region(0, 0);
        m2.find();
        m2.region(0, 5);
        System.out.println(m2.hitEnd());

        // make sure that progress is tracked correctly
        System.out.println("Check progress...");
        Matcher m3 = Pattern.compile("abc").matcher("abcabcab");
        m3.find();
        m3.find();
        m3.find();
        System.out.println(m3.hitEnd());

        // TODO implementation (bounds mode should be saved)
        System.out.println("Checking different type of bounds...");
        Matcher m4 = Pattern.compile("(?=abc)").matcher("abc");
        m4.region(0, 0);
        System.out.println(m4.find());
        // System.out.println(m4.hitEnd());
        m4.useTransparentBounds(true);
        System.out.println(m4.hitEnd());

        // TODO oldLast is used for \\G, it could be a problem if we don't back it up but in practice we fallback anyway for \\G so has no actual impact
        System.out.println("Check \\G matcher....");
        Matcher m5 = Pattern.compile("\\Gaa").matcher("aaaa");
        System.out.println(m5.find());
        System.out.println(m5.find());
        System.out.println(m5.hitEnd());
    }
}