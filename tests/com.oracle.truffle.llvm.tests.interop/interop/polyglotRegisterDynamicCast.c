#include <polyglot.h>

typedef struct object {
    int field1;
    int field2;
} MyObject;

typedef struct object2 {
    MyObject base;
    int field3;
} MyObject2;

POLYGLOT_DECLARE_STRUCT(object);
POLYGLOT_DECLARE_STRUCT(object2);

void *get_object2_typeid(void) {
    return polyglot_object2_typeid();
}

void *test_dynamic_cast(MyObject *object, void* out_array) {
    int i = 0;

    polyglot_set_array_element(out_array, i++, object->field1);
    polyglot_set_array_element(out_array, i++, object->field2);
    polyglot_set_array_element(out_array, i++, ((MyObject2*)object)->field3);

    return out_array;
}
