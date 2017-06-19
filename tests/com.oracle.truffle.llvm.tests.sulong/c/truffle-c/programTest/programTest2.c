int recursive_factorial(int n) { return n >= 1 ? n * recursive_factorial(n - 1) : 1; }

int main() { return recursive_factorial(5); }
