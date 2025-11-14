/*
 Expected invariants:
 - reading arr[i] when i unknown -> wildcard, result probably TOP unless array elements initialized to constants in store
*/
public class ArrayUnknownIndex {
    public static int main(String[] args) {
        int[] arr = new int[10];
        int i = 3; // set to a number or leave unknown for test
        arr[1] = 5;
        int v = arr[i];
        return v;
    }
}

