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
        String url = "https://raw.githubusercontent.com/mariomka/regex-benchmark/master/input-text.txt";
        System.out.println();
        String data;
        try {
            data = fetchData(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        var runs = new RegexRun[]{
                new RegexRun("date", "((([1-3][0-9])|[1-9])\\/((1[0-2])|0?[1-9])\\/[0-9]{4})|((([1-3][0-9])|[1-9])-((1[0-2])|0?[1-9])-[0-9]{4})|((([1-3][0-9])|[1-9])\\.((1[0-2])|0?[1-9])\\.[0-9]{4})", data, 0, true),
                // new RegexRun("ipv4", "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)", data, 0, true),
                // new RegexRun("url", "(?:(?:(?:\\w+):\\/\\/)(?:[^\\/:]*)(?::(?:\\d+))?)?(?:[^#?]*)(?:\\?(?:[^#]*))?(?:#(?:.*))?", data, 0, true),
                // new RegexRun("email", "([-!#-''*+/-9=?A-Z^-~]+(\\.[-!#-''*+/-9=?A-Z^-~]+)*|\"([ ]!#-[^-~ ]|(\\\\[-~ ]))+\")@[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?(\\.[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?)+", data, 0, true),
                // new RegexRun("vowels", "([aeiouAEIOU]+)", "eeeeeeeeeeeeeeiiiiiiiiiiiiiiiiiiieeeeeeeeeeeeeeeeeeeeeeeiiiiiiiiiiiiiiieeeeeeeeeeeee", 0, true),

                // new RegexRun("date", "((([1-3][0-9])|[1-9])\\/((1[0-2])|0?[1-9])\\/[0-9]{4})|((([1-3][0-9])|[1-9])-((1[0-2])|0?[1-9])-[0-9]{4})|((([1-3][0-9])|[1-9])\\.((1[0-2])|0?[1-9])\\.[0-9]{4})", "05-02-1997", 0, false),
                // new RegexRun("ipv4", "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)", "192.168.0.1", 0, true),
                // new RegexRun("url", "(?:(?:(?:\\w+):\\/\\/)(?:[^\\/:]*)(?::(?:\\d+))?)?(?:[^#?]*)(?:\\?(?:[^#]*))?(?:#(?:.*))?", "https://lafo.ssw.uni-linz.ac.at/?computer=15", 0, false),
                // new RegexRun("email", "([-!#-''*+/-9=?A-Z^-~]+(\\.[-!#-''*+/-9=?A-Z^-~]+)*|\"([ ]!#-[^-~ ]|(\\\\[-~ ]))+\")@[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?(\\.[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?)+", "benoit.maillard@protonmail.com", 0, false),
                // new RegexRun("vowels", "([aeiouAEIOU]+)", "eeeeeeeeeeeeeeiiiiiiiiiiiiiiiiiiieeeeeeeeeeeeeeeeeeeeeeeiiiiiiiiiiiiiiieeeeeeeeeeeee", 0, false),
        };


        for (var r: runs) {
            System.out.println("# " + r.name + ":");

            int countPrev = -1;

            long t = System.nanoTime();
            for (int i = 0; i < WARMUP; i++) {
                var count = r.execute();

                if (countPrev != -1)
                    assert count == countPrev;

                long newT = System.nanoTime();
                System.out.println(String.format("Warmup %d elapsed %.6f", i, (double) (newT - t) / 1000000));
                t = newT;

                countPrev = count;
            }

            long start = System.nanoTime();

            countPrev = -1;
            for (int i = 0; i < ITER; i++) {
                var count = r.execute();

                if (countPrev != -1)
                    assert count == countPrev;

                System.out.println("Count: " + count);
                countPrev = count;
            }
            double elapsed = (double) (System.nanoTime() - start) / 1000000 / ITER;
            System.out.println(String.format("Average time: %.6f", elapsed));
        }
    }

    static class RegexRun {
        private final String name;
        private final String pattern;
        private final Pattern patternCompiled;
        private final String input;
        private final int flags;
        private final boolean find;

        RegexRun(String name, String pattern, String input, int flags, boolean find) {
            this.name = name;
            this.pattern = pattern;
            // this.patternCompiled = null;
            this.patternCompiled = Pattern.compile(pattern);
            this.input = input;
            this.flags = flags;
            this.find = find;
        }

        int execute() {
            int count = 0;
            // Pattern p = Pattern.compile(pattern);

            // assert p.pattern().length() != 0;
            Pattern p = this.patternCompiled;
            Matcher m = p.matcher(input);
            if (find)
                while (m.find()) {
                    count++;
                }
            else {
                if (m.matches()) {
                    count++;
                };
            }

            return count;
        }
    }

    private static String fetchData(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<String> s = client.send(request, HttpResponse.BodyHandlers.ofString());

        return s.body();
    }
}