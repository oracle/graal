package sample;

public class Hello {

    static Hello hello = new Hello();

    Printer printer;

    public Printer createStdOutPrinter() {
        return new StdOutPrinter();
    }

    public void setup(String[] args) {
        if (args.length > 0) {
            printer = createStdOutPrinter();
        } else {
            printer = new NullPrinter();
        }
    }

    public static void main(final String[] args) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                hello.setup(args);
            }
        });
        t.start();
        try {
            t.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (String s : args) {
            hello.printer.print(s);
        }
    }
}

abstract class Printer {

    public abstract void print(String s);
}

class NullPrinter extends Printer {

    public void print(String s) {
    }
}

class StdOutPrinter extends Printer {

    public void print(String s) {
        System.out.println(s);
    }
}
