/*
 Expected invariants:
 - after if (x < 10) { ... } else { ... } edges should be narrowed: true-branch x in [-inf,9], false-branch x in [10, +inf]
*/
public class ConditionalNarrowing {
    public static int main(String[] args) {
        int x = 15;
        if (x < 10) {
            x = 1;
        } else {
            x = 20;
        }
        return x;
    }
}

