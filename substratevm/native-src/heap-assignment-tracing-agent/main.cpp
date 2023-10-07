#include <jvmti.h>
#include <iostream>
#include <span>
#include <cstring>
#include <cassert>
#include <vector>
#include <fstream>
#include "settings.h"
#include <ranges>
#include <unordered_map>
#include <memory>
#include <atomic>
#include <iterator>
#include <variant>

#include "JvmtiWrapper.h"

static bool check_jvmti_error(jvmtiError errorcode, const char* code, const char* filename, int line)
{
    bool error = errorcode != JVMTI_ERROR_NONE;
    if (error)
        std::cerr << "JVMTI ERROR " << errorcode << " at " << filename << ':' << line << ": \"" << code << '"' << std::endl;
    return error;
}

#define check_code(retcode, expr) if(check_jvmti_error(expr, #expr, __FILE__, __LINE__)) { return retcode; }
#define check(expr) throw_on_error(expr)
#define check_assert(expr) if(check_jvmti_error(expr, #expr, __FILE__, __LINE__)) { exit(1); }

using namespace std;

bool add_clinit_hook(jvmtiEnv* jvmti_env, const unsigned char* src, jint src_len, unsigned char** dst_ptr, jint* dst_len_ptr);

static void JNICALL onFieldModification(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jmethodID method,
        jlocation location,
        jclass field_klass,
        jobject object,
        jfieldID field,
        char signature_type,
        jvalue new_value);

static void JNICALL onClassPrepare(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jclass klass);

static void JNICALL onVMInit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread);

static void JNICALL onFramePop(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jmethodID method,
        jboolean was_popped_by_exception);

static void JNICALL onClassFileLoad(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jclass class_being_redefined,
        jobject loader,
        const char* name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char* class_data,
        jint* new_class_data_len,
        unsigned char** new_class_data);

static void JNICALL onThreadStart(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread);

static void JNICALL onThreadEnd(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread);

static void JNICALL onObjectFree(
        jvmtiEnv *jvmti_env,
        jlong tagInt);

static void JNICALL onVMObjectAlloc(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jclass object_klass,
        jlong size);


class AgentThreadContext
{
    vector<jobject> runningClassInitializations;
    jobject current_cause = nullptr;
    bool current_cause_record_heap_assignments = false;

public:
    static AgentThreadContext* from_thread(jvmtiEnv* jvmti_env, jthread t)
    {
        AgentThreadContext* tc;
        check(jvmti_env->GetThreadLocalStorage(t, (void**)&tc));

        if(!tc)
        {
#if LOG
            cerr << "Thread had no initialized context!" << endl;
#endif
            tc = new AgentThreadContext();
            check(jvmti_env->SetThreadLocalStorage(t, tc));
        }

        return tc;
    }

    void clinit_push(JNIEnv* env, jobject clazz)
    {
        runningClassInitializations.push_back(env->NewGlobalRef(clazz));
    }

    void clinit_pop(JNIEnv* env)
    {
        assert(!runningClassInitializations.empty());
        runningClassInitializations.pop_back();
        // Leaking jclass global objects since they serve in ObjectContext...
    }

    [[nodiscard]] jobject clinit_top() const
    {
        return runningClassInitializations.back();
    }

    [[nodiscard]] jobject reason(bool heap_assignment) const
    {
        return !runningClassInitializations.empty() ? runningClassInitializations.back() : ((heap_assignment && !current_cause_record_heap_assignments) ? nullptr : current_cause);
    }

    void set_current_cause(JNIEnv* env, jobject cause, bool record_heap_assignments)
    {
        assert(runningClassInitializations.empty());
        current_cause = cause ? env->NewGlobalRef(cause) : nullptr;
        current_cause_record_heap_assignments = record_heap_assignments;
    }
};

class ObjectContext
{
    static inline uint64_t next_id = 0;
    static inline mutex creation_mutex;

    // This id is unique even after collection by GC
    uint64_t _id;
    jobject _allocReason = nullptr;

    static ObjectContext* create(jvmtiEnv* jvmti_env, JNIEnv* env, jobject o);

protected:
    ObjectContext() = default;

public:
    virtual ~ObjectContext() = default;

    [[nodiscard]] uint64_t id() const { return _id; }
    [[nodiscard]] jobject allocReason() const { return _allocReason; }

    static ObjectContext* get_or_create(jvmtiEnv* jvmti_env, JNIEnv* env, jobject o, jobject allocReason);

    static ObjectContext* get(jvmtiEnv* jvmti_env, jobject o);
};

class ObjectTag
{
    static_assert(sizeof(uintptr_t) == 8);

    bool complexData : 1;
    uintptr_t ptr : 63;

    ObjectTag() = default;

public:
    explicit ObjectTag(jobject allocReason) : complexData(false), ptr(reinterpret_cast<uintptr_t>(allocReason)) {}

    explicit ObjectTag(ObjectContext* oc) : complexData(true), ptr(reinterpret_cast<uintptr_t>(oc)) {}

    [[nodiscard]] ObjectContext* getComplexData() const
    {
        return complexData ? reinterpret_cast<ObjectContext*>(ptr) : nullptr;
    }

    [[nodiscard]] jobject getAllocReason() const
    {
        if (complexData) {
            return getComplexData()->allocReason();
        } else {
            return reinterpret_cast<jobject>(ptr);
        }
    }

    static void set(jvmtiEnv* jvmti_env, jobject o, ObjectTag tag)
    {
        check(jvmti_env->SetTag(o, *reinterpret_cast<jlong*>(&tag)));
    }

    static ObjectTag get(jvmtiEnv* jvmti_env, jobject o)
    {
        ObjectTag tag;
        check(jvmti_env->GetTag(o, reinterpret_cast<jlong*>(&tag)));
        return tag;
    }
};
static_assert(sizeof(ObjectTag) == sizeof(jlong));

ObjectContext* ObjectContext::get(jvmtiEnv* jvmti_env, jobject o)
{
    return ObjectTag::get(jvmti_env, o).getComplexData();
}

ObjectContext* ObjectContext::get_or_create(jvmtiEnv* jvmti_env, JNIEnv* env, jobject o, jobject allocReason)
{
    ObjectContext* oc = ObjectContext::get(jvmti_env, o);

    if(!oc)
        oc = create(jvmti_env, env, o);

    if(!oc->_allocReason)
        oc->_allocReason = allocReason;

    return oc;
}

struct Write
{
    uint64_t object_id;
    jobject reason;
};

template<typename T>
class MonotonicConcurrentList
{
    struct Element
    {
        Element* prev;
        T data;

        Element(T data) : data(data) {}
    };

    atomic<Element*> head = nullptr;

public:
    class iterator
    {
        friend class MonotonicConcurrentList;
        Element* cur;

        iterator(Element* cur) : cur(cur) {}

    public:
        using value_type = T;
        using difference_type = std::ptrdiff_t;
        using iterator_category = std::input_iterator_tag;

        bool operator==(default_sentinel_t sentinel)
        {
            return cur == nullptr;
        }

        bool operator!=(default_sentinel_t sentinel)
        {
            return cur != nullptr;
        }

        iterator& operator++()
        {
            cur = cur->prev;
            return *this;
        }

        void operator++(int) { (*this)++; }

        T& operator*() { return cur->data; }
        T& operator->() { return cur->data; }
    };

    static_assert(std::input_or_output_iterator<iterator>);

    MonotonicConcurrentList() = default;

    void push(T data)
    {
        Element* new_elem = new Element(data);
        Element* cur_head = head;

        do
        {
            new_elem->prev = cur_head;
        }
        while(!head.compare_exchange_weak(cur_head, new_elem));
    }

    iterator begin()
    {
        return {head};
    }

    default_sentinel_t end()
    {
        return {};
    }
};

class WriteHistory
{
    MonotonicConcurrentList<Write> history;

public:
    void add(ObjectContext* o, jobject reason)
    {
        history.push({o->id(), reason});
    }

    jobject lookup(ObjectContext* writtenVal)
    {
        uint64_t id = writtenVal->id();
        for(const Write& write : history)
        {
            if(write.object_id == id)
                return write.reason;
        }
        return nullptr;
    }
};


class ClassInfo
{
    unordered_map<jfieldID, size_t> nonstatic_field_indices;
    unordered_map<jfieldID, size_t> static_field_indices;

public:
    ClassInfo(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass klass)
    {
        do
        {
            jint count;
            jfieldID* fields;
            check(jvmti_env->GetClassFields(klass, &count, &fields));

            for(size_t i = 0; i < count; i++)
            {
                char* field_name;
                char* field_signature;
                char* field_generic;

                check(jvmti_env->GetFieldName(klass, fields[i], &field_name, &field_signature, &field_generic));

                // Don't care for primitive types
                if(field_signature[0] != 'L' && field_signature[0] != '[')
                    continue;

                jint modifiers;
                check(jvmti_env->GetFieldModifiers(klass, fields[i], &modifiers));

                if(modifiers & 8 /* ACC_STATIC */)
                    static_field_indices.emplace(fields[i], static_field_indices.size());
                else
                    nonstatic_field_indices.emplace(fields[i], nonstatic_field_indices.size());
            }

            check(jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(fields)));

            klass = jni_env->GetSuperclass(klass);
        }
        while(klass);
    }

    [[nodiscard]] size_t n_static_fields() const
    {
        return static_field_indices.size();
    }

    [[nodiscard]] size_t n_nonstatic_fields() const
    {
        return nonstatic_field_indices.size();
    }

    size_t get_nonstatic_field_index(jfieldID field) const
    {
        auto it = nonstatic_field_indices.find(field);
        assert(it != nonstatic_field_indices.end());
        assert(it->second < nonstatic_field_indices.size());
        return it->second;
    }

    size_t get_static_field_index(jfieldID field) const
    {
        auto it = static_field_indices.find(field);
        assert(it != static_field_indices.end());
        assert(it->second < static_field_indices.size());
        return it->second;
    }
};


class NonArrayObjectContext : public ObjectContext
{
    shared_ptr<const ClassInfo> cc;
    vector<WriteHistory> fields_history;

public:
    ~NonArrayObjectContext() override = default;

    NonArrayObjectContext(shared_ptr<const ClassInfo> cc);

    void registerWrite(jfieldID field, ObjectContext* newVal, jobject reason);

    jobject getWriteReason(jfieldID field, ObjectContext* writtenVal);
};

class ClassContext : public NonArrayObjectContext
{
    class LazyData
    {
        shared_ptr<const ClassInfo> _info;
        unique_ptr<WriteHistory[]> fields_history = nullptr;

    public:
        LazyData(shared_ptr<const ClassInfo> info) : _info(std::move(info)), fields_history(new WriteHistory[this->_info->n_static_fields()]())
        {}

        void registerStaticWrite(jfieldID field, ObjectContext* newVal, jobject reason)
        {
            fields_history[_info->get_static_field_index(field)].add(newVal, reason);
        }

        jobject getStaticFieldReason(jfieldID field, ObjectContext* writtenVal)
        {
            return fields_history[_info->get_static_field_index(field)].lookup(writtenVal);
        }

        [[nodiscard]] const shared_ptr<const ClassInfo>& info() const
        {
            return _info;
        }
    };

    jweak class_object;
    atomic<LazyData*> lazy = nullptr;

    LazyData& data(jvmtiEnv* jvmti_env, JNIEnv* jni_env)
    {
        if(lazy)
            return *lazy;

        jclass clazz = (jclass)jni_env->NewLocalRef(class_object);
        assert(clazz && "Class object has been collected!");

        // Race condition: Since pointer types are atomically assignable, the worst case is a (minor) memory leak here.
        LazyData* expected = nullptr;
        LazyData* desired = new LazyData(make_shared<ClassInfo>(jvmti_env, jni_env, clazz));
        jni_env->DeleteLocalRef(clazz);
        bool uncontended = lazy.compare_exchange_strong(expected, desired);
        if(uncontended)
            return *desired;

        delete desired;
        return *expected;
    }

public:
    ClassContext(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass klass, shared_ptr<const ClassInfo> declaring_info, shared_ptr<const ClassInfo> own_info = {}) :
        NonArrayObjectContext(std::move(declaring_info)),
        class_object(jni_env->NewWeakGlobalRef(klass))
    {
        if(own_info)
            lazy = new LazyData(std::move(own_info));
    }

    ~ClassContext() override
    {
        delete lazy;
        // TODO: Remove leak of class_object
    }

    void registerStaticWrite(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jfieldID field, ObjectContext* newVal, jobject reason)
    {
        data(jvmti_env, jni_env).registerStaticWrite(field, newVal, reason);
    }

    jobject getStaticFieldReason(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jfieldID field, ObjectContext* writtenVal)
    {
        return data(jvmti_env, jni_env).getStaticFieldReason(field, writtenVal);
    }

    const shared_ptr<const ClassInfo>& info(jvmtiEnv* jvmti_env, JNIEnv* jni_env)
    {
        return data(jvmti_env, jni_env).info();
    }

    static ClassContext* get_or_create(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass klass)
    {
        return dynamic_cast<ClassContext*>(ObjectContext::get_or_create(jvmti_env, jni_env, klass, nullptr));
    }

    jobject made_reachable_by = nullptr;
};

NonArrayObjectContext::NonArrayObjectContext(shared_ptr<const ClassInfo> cc) : cc(std::move(cc)), fields_history(this->cc->n_nonstatic_fields())
{}

void NonArrayObjectContext::registerWrite(jfieldID field, ObjectContext* newVal, jobject reason)
{
    fields_history[cc->get_nonstatic_field_index(field)].add(newVal, reason);
}

jobject NonArrayObjectContext::getWriteReason(jfieldID field, ObjectContext* writtenVal)
{
    return fields_history[cc->get_nonstatic_field_index(field)].lookup(writtenVal);
}


class ArrayObjectContext : public ObjectContext
{
    vector<WriteHistory> elements_history;

public:
    ArrayObjectContext(size_t array_length) : elements_history(array_length)
    { }

    ~ArrayObjectContext() override = default;

    void registerWrite(jint index, ObjectContext* newVal, jobject reason)
    {
        assert(index >= 0 && index < elements_history.size());
        elements_history[index].add(newVal, reason);
    }

    jobject getWriteReason(jint index, ObjectContext* writtenVal)
    {
        assert(index >= 0 && index < elements_history.size());
        return elements_history[index].lookup(writtenVal);
    }
};

ObjectContext* ObjectContext::create(jvmtiEnv* jvmti_env, JNIEnv* env, jobject o)
{
    jclass oClass = env->GetObjectClass(o);
    char* signature;
    char* generic;
    check(jvmti_env->GetClassSignature(oClass, &signature, &generic));

    ObjectContext* oc;

    if(env->IsSameObject(oClass, o))
    {
        auto info = make_shared<ClassInfo>(jvmti_env, env, oClass);
        oc = new ClassContext(jvmti_env, env, oClass, info, info);
    }
    else if(signature[0] == 'L')
    {
        ClassContext* cc = ClassContext::get_or_create(jvmti_env, env, oClass);
        shared_ptr<const ClassInfo> ci = cc->info(jvmti_env, env);

        if(std::strcmp(signature, "Ljava/lang/Class;") == 0)
        {
            oc = new ClassContext(jvmti_env, env, (jclass)o, std::move(ci));
        }
        else
        {
            oc = new NonArrayObjectContext(std::move(ci));
        }
    }
    else if(signature[0] == '[')
    {
        size_t array_length = env->GetArrayLength((jarray)o);
        oc = new ArrayObjectContext(array_length);
    }
    else
    {
        assert(false);
    }

    {
        lock_guard<mutex> l(ObjectContext::creation_mutex);
        ObjectTag oldTag = ObjectTag::get(jvmti_env, o);

        if(auto alreadyExistingOc = oldTag.getComplexData())
        {
#if LOG
            cerr << "Concurrent ObjectContext creation!\n";
#endif
            delete oc;
            oc = alreadyExistingOc;
        }
        else
        {
            oc->_id = next_id++;
            oc->_allocReason = oldTag.getAllocReason();
            ObjectTag::set(jvmti_env, o, ObjectTag(oc));
        }
    }

    check(jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(signature)));
    check(jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(generic)));

    return oc;
}


static bool breakpoints_enable = false;
static bool instrumentation_enable = false;

static void addToTracingStack(jvmtiEnv* jvmti_env, JNIEnv* env, jthread thread, jobject reason)
{
    AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);

    if(breakpoints_enable && !tc->reason(true))
    {
        check(jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, thread));
    }

#if LOG || PRINT_CLINIT_HEAP_WRITES

    char inner_clinit_name[1024];
    get_class_name(jvmti_env, type, inner_clinit_name);

    char outer_clinit_name[1024];

    if(tc->clinit_empty())
        outer_clinit_name[0] = 0;
    else
        get_class_name(jvmti_env, tc->clinit_top(), outer_clinit_name);

    if(LOG || (strcmp(inner_clinit_name, outer_clinit_name) != 0))
    {
        cerr << outer_clinit_name << ": " << inner_clinit_name << ".<clinit>()\n";
    }
#endif

    jobject made_reachable_by = tc->reason(false);
    tc->clinit_push(env, reason);

    if(made_reachable_by && reason)
    {
        // Use tc->clinit_top because thats a global ref now...
        ObjectContext* oc = ObjectContext::get_or_create(jvmti_env, env, tc->clinit_top(), nullptr);

        if(auto* cc = dynamic_cast<ClassContext*>(oc))
        {
            assert(!cc->made_reachable_by);
            cc->made_reachable_by = made_reachable_by;
        }
    }
}

static void removeFromTracingStack(jvmtiEnv* jvmti_env, JNIEnv* env, jthread thread, jobject reason)
{
    AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);

    jobject topReason = tc->clinit_top();
    assert(env->IsSameObject(topReason, reason));
    tc->clinit_pop(env);

    if(breakpoints_enable && !tc->reason(true))
    {
        check(jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FIELD_MODIFICATION, thread));
    }

#if LOG
    char inner_clinit_name[1024];
    get_class_name(jvmti_env, type, inner_clinit_name);
    cerr << inner_clinit_name << ".<clinit>() ENDED\n";
#endif
}





class Environment
{
    JavaVM* vm;
    jvmtiEnv* env;

    static jvmtiIterationControl JNICALL heapObjectCallback(jlong class_tag, jlong size, jlong* tag_ptr, void* user_data)
    {
        ObjectTag tag = *reinterpret_cast<ObjectTag*>(tag_ptr);
        delete tag.getComplexData();
        return JVMTI_ITERATION_CONTINUE;
    }

public:
    Environment(JavaVM* vm, jvmtiEnv* env) : vm(vm), env(env) {}

    ~Environment()
    {
        // Free ObjectContexts
        auto res = env->IterateOverHeap(JVMTI_HEAP_OBJECT_TAGGED, heapObjectCallback, nullptr);

        if(res != JVMTI_ERROR_NONE)
        {
            // May happen e.g. on normal process exit, when Destructor is called from c++ stdlib.
            return;
        }

        check_assert(env->DisposeEnvironment());
    }

    jvmtiEnv* jvmti_env() const { return env; }

    JNIEnv* jni_env() const
    {
        void* env;
        jint res = vm->GetEnv(&env, JNI_VERSION_1_1);
        if(res)
            return nullptr;
        return reinterpret_cast<JNIEnv*>(env);
    }
};

static shared_ptr<Environment> _jvmti_env_backing;
static weak_ptr<Environment> _jvmti_env;

template<typename TReturn, typename TFun> requires(std::is_invocable_r_v<TReturn, TFun, jvmtiEnv*>)
static TReturn acquire_jvmti_and_wrap_exceptions(TFun&& lambda)
{
    auto jvmti_env_guard = _jvmti_env.lock();
    if(!jvmti_env_guard)
        return TReturn();
    auto jvmti_env = jvmti_env_guard->jvmti_env();auto thrower = [&](const char* classname, const char* message) {
        JNIEnv* env = jvmti_env_guard->jni_env();
        if(!env) {
            std::cerr << "Fatal error: " << message << std::endl;
            exit(1);
        }
        env->ThrowNew(env->FindClass(classname), message);
    };
    return swallow_cpp_exception_and_throw_java<TReturn>(jvmti_env, thrower, [&]() { return lambda(jvmti_env); });
}

static void acquire_jvmti_and_wrap_exceptions(std::invocable<jvmtiEnv*> auto&& lambda)
{
    acquire_jvmti_and_wrap_exceptions<void>(lambda);
}

static void acquire_jvmti_and_wrap_exceptions(std::invocable<> auto&& lambda)
{
    acquire_jvmti_and_wrap_exceptions([&](jvmtiEnv* jvmti_env) { lambda(); });
}

static void increase_log_cnt()
{
}


#include <unistd.h>
#include <link.h>
#include <sstream>

static int callback(dl_phdr_info* info, size_t size, void* data)
{
    auto name = string_view(info->dlpi_name);
    string_view self(AGENT_LIBRARY_NAME);

    if(name.ends_with(self))
    {
        *(string*)data = string_view(info->dlpi_name).substr(0, name.size() - self.size());
        return 1;
    }
    else
    {
        return 0;
    }
}

static string get_own_path()
{
    string path;
    bool success = dl_iterate_phdr(callback, &path);
    assert(success);
    return path;
}

static void foreach_option(string_view options, auto callback)
{
    for(size_t start = 0, pos; (pos = options.find(',', start)) != std::string::npos; start = pos + 1)
    {
        string_view option = options.substr(start, pos - start);
        callback(option);
    }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    foreach_option(options, [&](string_view option){
        if(option == "breakpoints")
            breakpoints_enable = true;
        else if(option == "instrumentation")
            instrumentation_enable = true;
        else {
            cerr << "[HeapAssignmentTracingAgent] Unknown option: " << option << endl;
            exit(1);
        }
    });

    jvmtiEnv* env;
    jint res = vm->GetEnv(reinterpret_cast<void **>(&env), JVMTI_VERSION_1_2);
    if(res)
        return 1;

    auto own_path = get_own_path();
    own_path.append("/" HOOK_JAR_NAME);
    check_code(1, env->AddToBootstrapClassLoaderSearch(own_path.c_str()));

    _jvmti_env_backing = std::make_shared<Environment>(vm, env);
    _jvmti_env = _jvmti_env_backing;

    jvmtiCapabilities cap{};
    cap.can_tag_objects = true;
    cap.can_generate_object_free_events = true;
    if(instrumentation_enable)
    {
        cap.can_retransform_classes = true;
        cap.can_retransform_any_class = true;
        cap.can_generate_all_class_hook_events = true;
        cap.can_generate_frame_pop_events = true;
    }
    if(breakpoints_enable)
    {
        cap.can_generate_breakpoint_events = true;
        cap.can_generate_field_modification_events = true;
    }

    check_code(1, env->AddCapabilities(&cap));

    jvmtiEventCallbacks callbacks{ nullptr };
    callbacks.FieldModification = onFieldModification;
    callbacks.ClassPrepare = onClassPrepare;
    callbacks.VMInit = onVMInit;
    callbacks.FramePop = onFramePop;
    callbacks.ClassFileLoadHook = onClassFileLoad;
    callbacks.ThreadStart = onThreadStart;
    callbacks.ThreadEnd = onThreadEnd;
    callbacks.ObjectFree = onObjectFree;
    callbacks.VMObjectAlloc = onVMObjectAlloc;

    check_code(1, env->SetEventCallbacks(&callbacks, sizeof(callbacks)));
    check_code(1, env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr));
    check_code(1, env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr));
    check_code(1, env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_END, nullptr));
    check_code(1, env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_OBJECT_FREE, nullptr));
    if(instrumentation_enable)
    {
        check_code(1, env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr));
        check_code(1, env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr));
    }

    return 0;
}

static void processClass(jvmtiEnv* jvmti_env, jclass klass)
{
#if LOG
    {
        char* class_signature;
        char* class_generic;

        check(jvmti_env->GetClassSignature(klass, &class_signature, &class_generic));
        cerr << "New Class: " << class_signature << "\n";
        check(jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(class_signature)));
        check(jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(class_generic)));
    }
#endif

    // Hook into field modification events

    ClassFields fields(jvmti_env, klass);

    for(auto field : fields)
    {
        FieldName fieldName = FieldName::get(jvmti_env, klass, field);

        // Don't care for primitive types
        if(fieldName.signature[0] != 'L' && fieldName.signature[0] != '[')
            continue;

        auto return_code = jvmti_env->SetFieldModificationWatch(klass, field);
        if(return_code == JVMTI_ERROR_DUPLICATE)
            return; // Silently ignore if the class had already been processed
        check(return_code);

#if LOG
        cerr << "SetFieldModificationWatch: " << class_signature << " . " << field_name << " (" << field_signature << ")\n";
#endif
    }
}

static jniNativeInterface* original_jni;
static void get_class_name(jvmtiEnv *jvmti_env, jclass clazz, span<char> buffer);

static void logArrayWrite(JNIEnv* env, jobjectArray arr, jsize index, jobject val)
{
    acquire_jvmti_and_wrap_exceptions([&](jvmtiEnv* jvmti_env){
        jthread thread;
        check(jvmti_env->GetCurrentThread(&thread));

        AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);

        auto cause = tc->reason(true);
        if(!cause)
            return;

        if(val)
        {
            ObjectContext* val_oc = ObjectContext::get_or_create(jvmti_env, env, val, cause);
            auto* arr_oc = dynamic_cast<ArrayObjectContext*>(ObjectContext::get_or_create(jvmti_env, env, arr, cause));
            assert(arr_oc);
            arr_oc->registerWrite(index, val_oc, cause);
            increase_log_cnt();
        }

#if LOG || PRINT_CLINIT_HEAP_WRITES
        jclass arr_class = env->GetObjectClass(arr);

        char class_name[1024];
        get_class_name(jvmti_env, arr_class, class_name);

        char new_value_class_name[1024];
        if(!val)
        {
            strcpy(new_value_class_name, "null");
        }
        else
        {
            jclass new_value_class = env->GetObjectClass(val);
            get_class_name(jvmti_env, new_value_class, new_value_class_name);
        }

        char cause_class_name[1024];

        if(tc->clinit_empty())
            cause_class_name[0] = 0;
        else
            get_class_name(jvmti_env, tc->clinit_top(), cause_class_name);

        class_name[strlen(class_name) - 2] = 0; // Cut off last "[]"
        cerr << cause_class_name << ": " << class_name << '[' << index << ']' << " = " << new_value_class_name << '\n';
#endif
    });
}

static void JNICALL setObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index, jobject val)
{
    logArrayWrite(env, array, index, val);
    original_jni->SetObjectArrayElement(env, array, index, val);
}

static void JNICALL onVMInit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread)
{
    {
        /*
         * Ensure onInitStart is linked:
         * If it was not linked prior to being installed as a hook, the VM crashes in an endless recursion,
         * trying to resolve the method, which causes the construction of new objects...
         */
        jclass hookClass = jni_env->FindClass(HOOK_CLASS_NAME);
        jmethodID onInitStart = jni_env->GetStaticMethodID(hookClass, "onInitStart", "(Ljava/lang/Object;)V");
        jni_env->CallStaticVoidMethod(hookClass, onInitStart, nullptr);
    }

    acquire_jvmti_and_wrap_exceptions([&](){
        if(breakpoints_enable)
        {
            check(jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, nullptr));
        }

        if(breakpoints_enable || instrumentation_enable)
        {
            jint num_classes;
            jclass* classes_ptr;

            LoadedClasses classes(jvmti_env);

            for(jclass clazz : classes)
            {
                if (instrumentation_enable)
                {
                    jboolean is_modifiable;
                    check(jvmti_env->IsModifiableClass(clazz, &is_modifiable));
                    if(is_modifiable)
                        check(jvmti_env->RetransformClasses(1, &clazz));
                }

                if (breakpoints_enable)
                {
                    jint status;
                    check(jvmti_env->GetClassStatus(clazz, &status));
                    if(status & JVMTI_CLASS_STATUS_PREPARED)
                        processClass(jvmti_env, clazz);
                }
            }
        }

        jniNativeInterface* redirected_jni;
        check(jvmti_env->GetJNIFunctionTable(&original_jni));
        check(jvmti_env->GetJNIFunctionTable(&redirected_jni));
        redirected_jni->SetObjectArrayElement = setObjectArrayElement;
        check(jvmti_env->SetJNIFunctionTable(redirected_jni));
    });
}

static void get_class_name(jvmtiEnv *jvmti_env, jclass clazz, span<char> buffer)
{
    if(!clazz)
    {
        buffer[0] = 0;
        return;
    }

    JvmtiString signature = std::move(ClassSignature::get(jvmti_env, clazz).signature);

    size_t array_nesting = 0;
    while(signature[array_nesting] == '[')
        array_nesting++;

    size_t pos;

    if(signature[array_nesting] == 'L')
    {
        for(pos = 0; pos < buffer.size() - 1; pos++)
        {
            char c = signature[pos+array_nesting+1];

            if(c == 0 || c == ';')
            {
                break;
            }

            if(c == '/')
                c = '.';

            buffer[pos] = c;
        }

        if(pos >= buffer.size() - 1)
            buffer[buffer.size() - 1] = 0;
    }
    else
    {
        const char* keyword;

        switch(signature[array_nesting])
        {
            case 'B': keyword = "byte"; break;
            case 'C': keyword = "char"; break;
            case 'D': keyword = "double"; break;
            case 'F': keyword = "float"; break;
            case 'I': keyword = "int"; break;
            case 'J': keyword = "long"; break;
            case 'S': keyword = "short"; break;
            case 'Z': keyword = "boolean"; break;
            default:
                buffer[0] = 0;
                return;
        }

        for(pos = 0; keyword[pos]; pos++)
            buffer[pos] = keyword[pos];
    }

    for(size_t i = 0; i < array_nesting; i++)
    {
        buffer[pos++] = '[';
        buffer[pos++] = ']';
    }

    buffer[pos] = 0;
}

static void onFieldModification(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jmethodID method,
        jlocation location,
        jclass field_klass,
        jobject object,
        jfieldID field,
        char signature_type,
        jvalue new_value)
{
    if(!new_value.l)
        return;

    acquire_jvmti_and_wrap_exceptions([&](){
        AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);

        auto cause = tc->reason(true);
        assert(cause);

        if(cause)
        {
            ObjectContext* val_oc = ObjectContext::get_or_create(jvmti_env, jni_env, new_value.l, cause);

            if(object)
            {
                auto object_oc = dynamic_cast<NonArrayObjectContext*>(ObjectContext::get_or_create(jvmti_env, jni_env, object, cause));
                assert(object_oc);
                object_oc->registerWrite(field, val_oc, cause);
                increase_log_cnt();
            }
            else
            {
                ClassContext* cc = ClassContext::get_or_create(jvmti_env, jni_env, field_klass);
                cc->registerStaticWrite(jvmti_env, jni_env, field, val_oc, cause);
                increase_log_cnt();
            }
        }

#if LOG || PRINT_CLINIT_HEAP_WRITES
        char class_name[1024];
        get_class_name(jvmti_env, field_klass, {class_name, class_name + 1024});

        char new_value_class_name[1024];
        jclass new_value_class = jni_env->GetObjectClass(new_value.l);
        get_class_name(jvmti_env, new_value_class, new_value_class_name);

        char cause_class_name[1024];

        if(tc->clinit_empty())
            cause_class_name[0] = 0;
        else
            get_class_name(jvmti_env, tc->clinit_top(), cause_class_name);

        if(string_view(new_value_class_name) == "java.lang.String")
        {
            const char* str_val = jni_env->GetStringUTFChars((jstring)new_value.l, nullptr);
            cerr << cause_class_name << ": " << class_name << "." << field_name << " = \"" << str_val << "\"\n";
            jni_env->ReleaseStringUTFChars((jstring)new_value.l, str_val);
        }
        else if(string_view(new_value_class_name) == "java.lang.Class")
        {
            char val_content[1024];
            get_class_name(jvmti_env, (jclass)new_value.l, val_content);
            cerr << cause_class_name << ": " << class_name << "." << field_name << " = java.lang.Class: \"" << val_content << "\"\n";
        }
        else
        {
            cerr << cause_class_name << ": " << class_name << "." << field_name << " = " << new_value_class_name << '\n';
        }
#endif
    });
}

static void JNICALL onFramePop(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jmethodID method,
        jboolean was_popped_by_exception)
{
    acquire_jvmti_and_wrap_exceptions([&](){
        jclass type;
        check(jvmti_env->GetMethodDeclaringClass(method, &type));
        removeFromTracingStack(jvmti_env, jni_env, thread, type);
    });
}

static void JNICALL onClassPrepare(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jclass klass)
{
    acquire_jvmti_and_wrap_exceptions([&](){
        processClass(jvmti_env, klass);
    });
}

static void JNICALL onClassFileLoad(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jclass class_being_redefined,
        jobject loader,
        const char* name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char* class_data,
        jint* new_class_data_len,
        unsigned char** new_class_data)
{
    acquire_jvmti_and_wrap_exceptions([&]() {
#if LOG
        cerr << "ClassLoad: " << name << endl;
#endif

        if(string_view(name) == HOOK_CLASS_NAME // Do not replace our own hooks, logically
           || string_view(name) == "com/oracle/svm/core/jni/functions/JNIFunctionTables") // Crashes during late compile phase
            return;

        add_clinit_hook(jvmti_env, class_data, class_data_len, new_class_data, new_class_data_len);
    });
}

static void record_allocation(jvmtiEnv* jvmti_env, jthread thread, jobject newInstance)
{
    AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);
    if(auto cause = tc->reason(false))
        ObjectTag::set(jvmti_env, newInstance, ObjectTag(cause));
}

extern "C" JNIEXPORT void JNICALL Java_HeapAssignmentTracingHooks_onInitStart(JNIEnv* env, jobject self, jobject instance)
{
    if (!instance) // Happens during our first invocation that ensures linkage
        return;

    acquire_jvmti_and_wrap_exceptions([&](jvmtiEnv* jvmti_env) {
        jthread thread;
        check(jvmti_env->GetCurrentThread(&thread));
        record_allocation(jvmti_env, thread, instance);
    });
}

extern "C" JNIEXPORT void JNICALL Java_HeapAssignmentTracingHooks_onClinitStart(JNIEnv* env, jobject self)
{
    acquire_jvmti_and_wrap_exceptions([&](jvmtiEnv* jvmti_env){
        jvmtiPhase phase;
        check(jvmti_env->GetPhase(&phase));

        if(phase != JVMTI_PHASE_LIVE)
            return;

        jthread thread;
        check(jvmti_env->GetCurrentThread(&thread));

        jmethodID method;
        jlocation location;
        check(jvmti_env->GetFrameLocation(thread, 1, &method, &location));

        jclass type;
        check(jvmti_env->GetMethodDeclaringClass(method, &type));

        addToTracingStack(jvmti_env, env, thread, type);

        check(jvmti_env->NotifyFramePop(thread, 1));
    });
}

static void JNICALL onThreadStart(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread)
{
    acquire_jvmti_and_wrap_exceptions([&]() {
        auto* tc = new AgentThreadContext();
        check(jvmti_env->SetThreadLocalStorage(thread, tc));
    });
}

static void JNICALL onThreadEnd(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread)
{
    acquire_jvmti_and_wrap_exceptions([&]() {
        AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);
        delete tc;
    });
}

void onObjectFree(jvmtiEnv *jvmti_env, jlong tagInt)
{
    acquire_jvmti_and_wrap_exceptions([&]() {
        ObjectTag tag = *reinterpret_cast<ObjectTag*>(&tagInt);
        delete tag.getComplexData();
    });
}

static void JNICALL onVMObjectAlloc(
        jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jclass object_klass,
        jlong size)
{
    acquire_jvmti_and_wrap_exceptions([&]() {
        record_allocation(jvmti_env, thread, object);
    });
}

extern "C" JNIEXPORT void JNICALL Java_HeapAssignmentTracingHooks_notifyArrayWrite(JNIEnv* env, jobject self, jobjectArray arr, jint index, jobject val)
{
    logArrayWrite(env, arr, index, val);
}

extern "C" JNIEXPORT void JNICALL Java_HeapAssignmentTracingHooks_onThreadStart(JNIEnv* env, jobject self, jthread newThread)
{
#if LOG || PRINT_CLINIT_HEAP_WRITES
    acquire_jvmti_and_wrap_exceptions([&]()
    {
        jvmtiPhase phase;
        check(jvmti_env->GetPhase(&phase));

        if(phase != JVMTI_PHASE_LIVE)
            return;

        jthread thread;
        check(jvmti_env->GetCurrentThread(&thread));

        AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);

        if(tc->clinit_empty())
            return;

        char outer_clinit_name[1024];
        get_class_name(jvmti_env, tc->clinit_top(), outer_clinit_name);

        jvmtiThreadInfo _info;
        check(jvmti_env->GetThreadInfo(newThread, &_info));

        cerr << outer_clinit_name << ": " << "Thread.start(): \"" << _info.name << "\"\n";
    });
#endif
}

extern "C" JNIEXPORT jobject JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_getResponsibleClass(JNIEnv* env, jobject thisClass, jobject imageHeapObject)
{
    return acquire_jvmti_and_wrap_exceptions<jobject>([&](jvmtiEnv* jvmti_env)
    {
        return ObjectTag::get(jvmti_env, imageHeapObject).getAllocReason();
    });
}

extern "C" JNIEXPORT jobject JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_getClassResponsibleForNonstaticFieldWrite(JNIEnv* env, jobject thisClass, jobject receiver, jobject field, jobject val)
{
    return acquire_jvmti_and_wrap_exceptions<jobject>([&](jvmtiEnv* jvmti_env)
    {
        auto receiver_oc = dynamic_cast<NonArrayObjectContext*>(ObjectContext::get(jvmti_env, receiver));
        auto val_oc = ObjectContext::get(jvmti_env, val);
        jobject res = nullptr;
        if(receiver_oc && val_oc)
            res = receiver_oc->getWriteReason(env->FromReflectedField(field), val_oc);
        return res;
    });
}

extern "C" JNIEXPORT jobject JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_getClassResponsibleForStaticFieldWrite(JNIEnv* env, jobject thisClass, jclass declaring, jobject field, jobject val)
{
    return acquire_jvmti_and_wrap_exceptions<jobject>([&](jvmtiEnv* jvmti_env) -> jobject
    {
        auto declaring_cc = dynamic_cast<ClassContext*>(ObjectContext::get(jvmti_env, declaring));
        if(!declaring_cc)
            return nullptr;
        auto val_oc = ObjectContext::get(jvmti_env, val);
        if(!val_oc)
            return nullptr;

        jint class_status;
        check(jvmti_env->GetClassStatus(declaring, &class_status));

        if(!(class_status & JVMTI_CLASS_STATUS_INITIALIZED))
        {
            char class_name[1024];
            get_class_name(jvmti_env, declaring, class_name);
            cerr << "Class not initialized yet field being asked for: " << class_name << endl;
            return nullptr;
        }

        return declaring_cc->getStaticFieldReason(jvmti_env, env, env->FromReflectedField(field), val_oc);
    });
}

extern "C" JNIEXPORT jobject JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_getClassResponsibleForArrayWrite(JNIEnv* env, jobject thisClass, jobjectArray array, jint index, jobject val)
{
    return acquire_jvmti_and_wrap_exceptions<jobject>([&](jvmtiEnv* jvmti_env)
    {
        auto array_oc = dynamic_cast<ArrayObjectContext*>(ObjectContext::get(jvmti_env, array));
        auto val_oc = ObjectContext::get(jvmti_env, val);
        jobject res = nullptr;
        if(array_oc && val_oc)
            res = array_oc->getWriteReason(index, val_oc);
        return res;
    });
}

extern "C" JNIEXPORT jobject JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_getBuildTimeClinitResponsibleForBuildTimeClinit(JNIEnv* env, jobject thisClass, jclass clazz)
{
    return acquire_jvmti_and_wrap_exceptions<jobject>([&](jvmtiEnv* jvmti_env)
    {
        auto cc = dynamic_cast<ClassContext*>(ObjectContext::get(jvmti_env, clazz));
        return cc ? cc->made_reachable_by : nullptr;
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_setCause(JNIEnv* env, jobject thisClass, jobject cause, jboolean recordHeapAssignments)
{
    acquire_jvmti_and_wrap_exceptions([&](jvmtiEnv* jvmti_env)
    {
        jthread thread;
        check(jvmti_env->GetCurrentThread(&thread));
        AgentThreadContext* tc = AgentThreadContext::from_thread(jvmti_env, thread);

        bool fieldModificationWasTracked = tc->reason(true) != nullptr;
        tc->set_current_cause(env, cause, recordHeapAssignments);
        if(breakpoints_enable)
        {
            bool fieldModificationIsTracked = tc->reason(true) != nullptr;
            if(fieldModificationIsTracked != fieldModificationWasTracked)
            {
                check(jvmti_env->SetEventNotificationMode(fieldModificationIsTracked ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_FIELD_MODIFICATION, thread));
            }
        }
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_oracle_graal_pointsto_reports_HeapAssignmentTracing_00024NativeImpl_dispose(JNIEnv* env, jobject thisClass)
{
    _jvmti_env_backing.reset();
}