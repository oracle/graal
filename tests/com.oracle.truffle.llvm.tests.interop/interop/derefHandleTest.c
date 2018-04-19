#include <polyglot.h>
#include <truffle.h>

void *test_allocate_deref_handle(void *managed) {
    void* arr = truffle_deref_handle_for_managed(managed);
    return arr;
}

typedef int64_t (*fun)(int64_t a, int64_t b);

struct Point {
	int32_t x;
	int32_t y;
	fun identity;
};

POLYGLOT_DECLARE_STRUCT(Point);

int32_t test_read_from_deref_handle(void *managed) {
	struct Point* p = truffle_deref_handle_for_managed(polyglot_as_Point(managed));
	int32_t x = p->x;
    int32_t y = p->y;
    return x*x + y*y;
}

void test_write_to_deref_handle(void *managed, int32_t x, int32_t y) {
	struct Point* p = truffle_deref_handle_for_managed(polyglot_as_Point(managed));
	p->x = x;
    p->y = y;
}

int64_t test_call_deref_handle(void *managed, int64_t a, int64_t b) {
	fun f = (fun) truffle_deref_handle_for_managed(managed);
	return f(a, b);
}

int32_t test_deref_handle_pointer_arith(void *managed) {
	void *p = truffle_deref_handle_for_managed(polyglot_as_Point(managed)) + sizeof(int32_t);
	return *(int32_t *)p;
}

int64_t test_call_deref_handle_member(struct Point *p, int64_t a, int64_t b) {
	return p->identity(a, b);
}
