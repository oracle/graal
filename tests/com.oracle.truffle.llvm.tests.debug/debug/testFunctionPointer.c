int getFive() {
    return 5;
}

int start() __attribute__((constructor)) {
    int (*funcPtr)() = &getFive;
    int res = funcPtr();
    printf("%d", res);
    return 0;
}
