long adr = "asdf";

long f1() { return adr; }

long f2() { return "asdf"; }

int main() { return f1() == f2(); }
