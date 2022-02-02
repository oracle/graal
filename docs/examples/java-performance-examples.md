---
layout: docs
toc_group: examples
link_title: Java Performance Examples
permalink: /examples/java-performance-examples/
---

# Java Performance Examples

The [Graal compiler](../reference-manual/java/compiler.md) achieves excellent performance, especially for highly abstracted programs, due to its versatile optimization techniques.
Code using more abstraction and modern Java features like Streams or Lambdas will see greater speedups.
The examples below demonstrate this.

## Running Examples

### Streams API Example

A simple example based on the [Streams API](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html) is used here to demonstrate performance gains when using the Graal compiler.
This example counts the number of uppercase characters in a body of text.
To simulate a large load, the same sentence is processed 10 million times:

1&#46; Save the following code snippet to a file named `CountUppercase.java`:

```java
public class CountUppercase {
    static final int ITERATIONS = Math.max(Integer.getInteger("iterations", 1), 1);
    public static void main(String[] args) {
        String sentence = String.join(" ", args);
        for (int iter = 0; iter < ITERATIONS; iter++) {
            if (ITERATIONS != 1) System.out.println("-- iteration " + (iter + 1) + " --");
            long total = 0, start = System.currentTimeMillis(), last = start;
            for (int i = 1; i < 10_000_000; i++) {
                total += sentence.chars().filter(Character::isUpperCase).count();
                if (i % 1_000_000 == 0) {
                    long now = System.currentTimeMillis();
                    System.out.printf("%d (%d ms)%n", i / 1_000_000, now - last);
                    last = now;
                }
            }
            System.out.printf("total: %d (%d ms)%n", total, System.currentTimeMillis() - start);
        }
    }
}
```

2&#46; Compile it and run as follows:
```shell
javac CountUppercase.java
java CountUppercase This year I would like to run ALL languages in one VM.
1 (319 ms)
2 (275 ms)
3 (164 ms)
4 (113 ms)
5 (100 ms)
6 (124 ms)
7 (86 ms)
8 (76 ms)
9 (77 ms)
total: 69999993 (1414 ms)
```

The warmup time depends on numerous factors like the source code or how many cores a machine has.
If the performance profile of `CountUppercase` on your machine does not match the above, run it for more iterations by adding `-Diterations=N` just after `java` for some `N` greater than 1.

3&#46; Add the `-Dgraal.PrintCompilation=true` option to see statistics for the compilations:
```shell
java -Dgraal.PrintCompilation=true CountUppercase This year I would like to run ALL languages in one VM.
```

This option prints a line after each compilation that shows the method compiled, time taken, bytecodes processed (including inlined methods), size of machine code produced, and amount of memory allocated during compilation.

4&#46; Use the `-XX:-UseJVMCICompiler` option to disable the GraalVM compiler and use the native top tier compiler in the VM to compare performance:
```shell
java -XX:-UseJVMCICompiler CountUppercase This year I would like to run ALL languages in one VM.
1 (492 ms)
2 (441 ms)
3 (443 ms)
4 (470 ms)
5 (422 ms)
6 (382 ms)
7 (407 ms)
8 (425 ms)
9 (343 ms)
total: 69999993 (4249 ms)
```

The preceding example demonstrates the benefits of partial escape analysis (PEA) and advanced inlining, which combine to significantly reduce heap allocation.
The results were obtained using Oracle GraalVM Enterprise Edition.

The GraalVM Community Edition still has good performance compared to the native top-tier compiler as shown below.
You can simulate the Community Edition on the Enterprise Edition by adding the option `-Dgraal.CompilerConfiguration=community`.

### Sunflow Example

[Sunflow](http://sunflow.sourceforge.net) is an open source rendering engine.
The following example is a simplified version of the Sunflow engine core code.
It performs calculations to blend various values for a point of light in a rendered scene.

1&#46; Save the following code snippet to a file named `Blender.java`:
```java
public class Blender {

    private static class Color {
        double r, g, b;

        private Color(double r, double g, double b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public static Color color() {
            return new Color(0, 0, 0);
        }

        public void add(Color other) {
            r += other.r;
            g += other.g;
            b += other.b;
        }

        public void add(double nr, double ng, double nb) {
            r += nr;
            g += ng;
            b += nb;
        }

        public void multiply(double factor) {
            r *= factor;
            g *= factor;
            b *= factor;
        }
    }

    private static final Color[][][] colors = new Color[100][100][100];

    public static void main(String[] args) {
        for (int j = 0; j < 10; j++) {
            long t = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                initialize(new Color(j / 20, 0, 1));
            }
            long d = System.nanoTime() - t;
            System.out.println(d / 1_000_000 + " ms");
        }
    }

    private static void initialize(Color id) {
        for (int x = 0; x < colors.length; x++) {
            Color[][] plane = colors[x];
            for (int y = 0; y < plane.length; y++) {
                Color[] row = plane[y];
                for (int z = 0; z < row.length; z++) {
                    Color color = new Color(x, y, z);
                    color.add(id);
                    if ((color.r + color.g + color.b) % 42 == 0) {
                         // PEA only allocates a color object here
                         row[z] = color;
                    } else {
                         // Here the color object is not allocated at all
                    }
                }
            }
        }
    }
}
```

2&#46; Compile it and run as follows:
```shell
javac Blender.java
java Blender
1156 ms
916 ms
925 ms
980 ms
913 ms
904 ms
862 ms
863 ms
919 ms
868 ms
```

If you would like to check how it would behave when using GraalVM Community, use the following configuration flag:
```shell
java -Dgraal.CompilerConfiguration=community Blender
```

3&#46; Use the `-XX:-UseJVMCICompiler` option to disable the Graal compiler and run with the default HotSpot JIT compiler:
```shell
java -XX:-UseJVMCICompiler Blender
2546 ms
2522 ms
1710 ms
1741 ms
1724 ms
1722 ms
1763 ms
1742 ms
1714 ms
1733 ms
```

The performance improvement comes from the partial escape analysis moving the allocation of `color` in `initialize` down to the point where it is stored into `colors` (i.e., the point at which it escapes).

Check the [Compiler Configuration on JVM](../reference-manual/java/Options.md) reference for other performance tuning options.
