void bar() {
    printf("");
}

void foo()
{
    bar();
    bar();
}

int start() __attribute__((constructor))
{
    foo();
    foo();
    return 0;
}
