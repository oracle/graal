long f1() { return "asdf"; }

long f2() { return "asdfg"; }

long f3() { return "asdfg"; }

int main() { return f1() == f2() + f2() == f3() + f1() == f3(); }
