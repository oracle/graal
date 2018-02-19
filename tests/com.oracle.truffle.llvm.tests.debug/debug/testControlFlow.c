void bar() {
    printf("");
}

void foo()
{
    bar();
    bar();
}

int main()
{
    foo();
    foo();
    return 0;
}