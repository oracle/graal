package com.oracle.svm.hosted.analysis.ai.domain.memory;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AbsMemory represents the product domain (env, store) used by access-path analyses.
 * - env: mapping from AbsVar -> AccessPath (which root a variable refers to)
 * - store: mapping from AccessPath -> IntInterval (heap/field values)
 * <p>
 * Lattice semantics:
 * - join: pointwise join; env keeps a mapping only if both sides map the same AccessPath for a variable (otherwise mapping is lost)
 * - store: union of keys; intervals are joined via IntInterval.joinWith
 * - widen: same shape as join but uses widenWith on intervals
 * - meet: intersection of env/store keys; env keeps mapping only when equal; store meets intervals
 */
public class AbstractMemory extends AbstractDomain<AbstractMemory> {

    private boolean isBot;
    private boolean isTop;
    private final Map<Var, AccessPath> env;
    private final Map<AccessPath, IntInterval> store;

    public AbstractMemory() {
        this.isBot = false;
        this.isTop = false;
        this.env = new HashMap<>();
        this.store = new HashMap<>();
    }

    public AbstractMemory(Map<Var, AccessPath> env, Map<AccessPath, IntInterval> store) {
        this.isBot = false;
        this.isTop = false;
        this.env = new HashMap<>(env);
        this.store = new HashMap<>(store);
    }

    private void ensureNotBotTop() {
        if (isBot || isTop) {
            isBot = false;
            isTop = false;
        }
    }

    public void bindVar(Var v, AccessPath p) {
        Objects.requireNonNull(v);
        Objects.requireNonNull(p);
        ensureNotBotTop();
        env.put(v, p);
    }

    public AccessPath lookupVar(Var v) {
        return env.get(v);
    }

    public void removeVar(Var v) {
        env.remove(v);
    }

    // Store helpers
    public void writeStore(AccessPath p, IntInterval val) {
        Objects.requireNonNull(p);
        Objects.requireNonNull(val);
        ensureNotBotTop();
        IntInterval cur = store.get(p);
        if (cur == null) store.put(p, val.copyOf());
        else cur.joinWith(val);
    }

    /**
     * Strong update: overwrite the exact path and conservatively join into deeper paths that start with it.
     */
    public void writeStoreStrong(AccessPath p, IntInterval val) {
        Objects.requireNonNull(p);
        Objects.requireNonNull(val);
        ensureNotBotTop();
        store.put(p, val.copyOf());
        // join into any deeper keys starting with p
        Set<AccessPath> keys = new HashSet<>(store.keySet());
        for (AccessPath k : keys) {
            if (k.startsWith(p) && !k.equals(p)) {
                IntInterval cur = store.get(k);
                if (cur != null) {
                    cur.joinWith(val);
                    store.put(k, cur);
                }
            }
        }
    }

    public Set<AccessPath> getStoreKeys() {
        return new HashSet<>(store.keySet());
    }

    public Map<Var, AccessPath> getEnvSnapshot() {
        return new HashMap<>(env);
    }

    public void bindLocalByName(String localName, AccessPath p) {
        bindVar(Var.local(localName), p);
    }

    public void bindParamByName(String paramName, AccessPath p) {
        bindVar(Var.param(paramName), p);
    }

    public void bindTempByName(String tempName, AccessPath p) {
        bindVar(Var.temp(tempName), p);
    }

    public AccessPath lookupLocalByName(String localName) {
        return lookupVar(Var.local(localName));
    }

    public AccessPath lookupParamByName(String paramName) {
        return lookupVar(Var.param(paramName));
    }

    public AccessPath lookupTempByName(String tempName) {
        return lookupVar(Var.temp(tempName));
    }

    /**
     * Return a map of store entries whose access paths start with the given prefix.
     */
    public Map<AccessPath, IntInterval> getPathsWithPrefix(AccessPath prefix) {
        Objects.requireNonNull(prefix);
        Map<AccessPath, IntInterval> res = new HashMap<>();
        for (Map.Entry<AccessPath, IntInterval> e : store.entrySet()) {
            if (e.getKey().startsWith(prefix)) res.put(e.getKey(), e.getValue().copyOf());
        }
        return res;
    }

    /**
     * Apply a callee summary into this caller state.
     * placeholderToActualRoot maps placeholder root names (strings) to caller AccessPath roots.
     * We translate each summary store entry by replacing the placeholder root with the actual root and weakly joining the value.
     */
    public void applySummary(AbstractMemory summary, Map<String, AccessPath> placeholderToActualRoot) {
        Objects.requireNonNull(summary);
        Objects.requireNonNull(placeholderToActualRoot);
        // Apply store entries
        for (Map.Entry<AccessPath, IntInterval> e : summary.store.entrySet()) {
            AccessPath phPath = e.getKey();
            if (phPath.getRootKind() != AccessPath.RootKind.PLACEHOLDER) {
                // if the summary contains non-placeholder roots, either merge or skip; here we skip
                continue;
            }
            String phRoot = phPath.getRootName();
            AccessPath actualRoot = placeholderToActualRoot.get(phRoot);
            if (actualRoot == null) continue; // no mapping -> skip conservatively
            AccessPath actual = actualRoot;
            for (String f : phPath.getFields()) actual = actual.appendField(f);
            // weak join into caller store
            writeStore(actual, e.getValue());
        }

        // Apply env mappings: if callee bound a placeholder local, map it to caller actual if provided
        for (Map.Entry<Var, AccessPath> e : summary.env.entrySet()) {
            AccessPath vpath = e.getValue();
            if (vpath.getRootKind() == AccessPath.RootKind.PLACEHOLDER) {
                AccessPath mapped = placeholderToActualRoot.get(vpath.getRootName());
                if (mapped != null) bindVar(e.getKey(), mapped);
            }
        }
    }

    // --- AbstractDomain API ---
    @Override
    public boolean isBot() {
        return isBot;
    }

    @Override
    public boolean isTop() {
        return isTop;
    }

    @Override
    public boolean leq(AbstractMemory other) {
        if (other == null) return false;
        if (this.isBot()) return true;
        if (other.isTop()) return true;
        if (this.isTop()) return other.isTop();
        // env: for each binding in this.env, other.env must have identical mapping
        for (Map.Entry<Var, AccessPath> e : this.env.entrySet()) {
            AccessPath otherP = other.env.get(e.getKey());
            if (otherP == null || !otherP.equals(e.getValue())) return false;
        }
        // store: for each entry in this.store, other.store must have an interval >= (i.e., otherInterval contains this interval)
        for (Map.Entry<AccessPath, IntInterval> e : this.store.entrySet()) {
            IntInterval otherI = other.store.get(e.getKey());
            if (otherI == null) return false;
            if (!e.getValue().leq(otherI)) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AbstractMemory)) return false;
        AbstractMemory o = (AbstractMemory) other;
        if (this.isBot != o.isBot) return false;
        if (this.isTop != o.isTop) return false;
        return this.env.equals(o.env) && this.store.equals(o.store);
    }

    @Override
    public void setToBot() {
        isBot = true;
        isTop = false;
        env.clear();
        store.clear();
    }

    @Override
    public void setToTop() {
        isTop = true;
        isBot = false;
        env.clear();
        store.clear();
    }

    @Override
    public void joinWith(AbstractMemory other) {
        if (other == null) return;
        if (this.isTop || other.isTop) {
            setToTop();
            return;
        }
        if (this.isBot) {
            // adopt other's contents
            this.isBot = other.isBot;
            this.isTop = other.isTop;
            this.env.clear();
            this.env.putAll(other.env);
            this.store.clear();
            for (Map.Entry<AccessPath, IntInterval> e : other.store.entrySet())
                this.store.put(e.getKey(), e.getValue().copyOf());
            return;
        }
        // generic join: env keep mapping only when equal in both sides
        Set<Var> keys = new HashSet<>();
        keys.addAll(this.env.keySet());
        keys.addAll(other.env.keySet());
        Map<Var, AccessPath> newEnv = new HashMap<>();
        for (Var v : keys) {
            AccessPath a = this.env.get(v);
            AccessPath b = other.env.get(v);
            if (a != null && b != null && a.equals(b)) newEnv.put(v, a);
        }
        this.env.clear();
        this.env.putAll(newEnv);

        // store: union of keys with joined intervals
        Set<AccessPath> skeys = new HashSet<>();
        skeys.addAll(this.store.keySet());
        skeys.addAll(other.store.keySet());
        Map<AccessPath, IntInterval> newStore = new HashMap<>();
        for (AccessPath p : skeys) {
            IntInterval a = this.store.get(p);
            IntInterval b = other.store.get(p);
            if (a != null && b != null) {
                IntInterval c = a.copyOf();
                c.joinWith(b);
                newStore.put(p, c);
            } else if (a != null) newStore.put(p, a.copyOf());
            else if (b != null) newStore.put(p, b.copyOf());
        }
        this.store.clear();
        this.store.putAll(newStore);
    }

    @Override
    public void widenWith(AbstractMemory other) {
        if (other == null) return;
        if (this.isTop || other.isTop) {
            setToTop();
            return;
        }
        if (this.isBot) {
            this.isBot = other.isBot;
            this.isTop = other.isTop;
            this.env.clear();
            this.env.putAll(other.env);
            this.store.clear();
            for (Map.Entry<AccessPath, IntInterval> e : other.store.entrySet())
                this.store.put(e.getKey(), e.getValue().copyOf());
            return;
        }

        // env: keep only equal mappings
        Set<Var> keys = new HashSet<>();
        keys.addAll(this.env.keySet());
        keys.addAll(other.env.keySet());
        Map<Var, AccessPath> newEnv = new HashMap<>();
        for (Var v : keys) {
            AccessPath a = this.env.get(v);
            AccessPath b = other.env.get(v);
            if (a != null && b != null && a.equals(b)) newEnv.put(v, a);
        }
        this.env.clear();
        this.env.putAll(newEnv);

        // store: union of keys with widened intervals
        Set<AccessPath> skeys = new HashSet<>();
        skeys.addAll(this.store.keySet());
        skeys.addAll(other.store.keySet());
        Map<AccessPath, IntInterval> newStore = new HashMap<>();
        for (AccessPath p : skeys) {
            IntInterval a = this.store.get(p);
            IntInterval b = other.store.get(p);
            if (a != null && b != null) {
                IntInterval c = a.copyOf();
                c.widenWith(b);
                newStore.put(p, c);
            } else if (a != null) newStore.put(p, a.copyOf());
            else if (b != null) newStore.put(p, b.copyOf());
        }
        this.store.clear();
        this.store.putAll(newStore);
    }

    @Override
    public void meetWith(AbstractMemory other) {
        if (other == null) return;
        if (this.isBot || other.isBot) {
            setToBot();
            return;
        }
        if (this.isTop) {
            // meet(top, other) = other
            this.isTop = false;
            this.env.clear();
            this.env.putAll(other.env);
            this.store.clear();
            for (Map.Entry<AccessPath, IntInterval> e : other.store.entrySet())
                this.store.put(e.getKey(), e.getValue().copyOf());
            return;
        }
        if (other.isTop) {
            // meet(this, top) = this
            return;
        }

        // env: intersection where equal
        Map<Var, AccessPath> newEnv = new HashMap<>();
        for (Map.Entry<Var, AccessPath> e : this.env.entrySet()) {
            AccessPath b = other.env.get(e.getKey());
            if (b != null && b.equals(e.getValue())) newEnv.put(e.getKey(), e.getValue());
        }
        this.env.clear();
        this.env.putAll(newEnv);

        // store: intersection keys with meet of intervals
        Map<AccessPath, IntInterval> newStore = new HashMap<>();
        for (Map.Entry<AccessPath, IntInterval> e : this.store.entrySet()) {
            IntInterval b = other.store.get(e.getKey());
            if (b != null) {
                IntInterval c = e.getValue().copyOf();
                c.meetWith(b);
                newStore.put(e.getKey(), c);
            }
        }
        this.store.clear();
        this.store.putAll(newStore);
    }

    @Override
    public String toString() {
        if (isBot) return "AbsMemory(⊥)";
        if (isTop) return "AbsMemory(⊤)";
        StringBuilder sb = new StringBuilder();
        sb.append("AbsMemory{env=").append(env).append(", store=").append(store).append('}');
        return sb.toString();
    }

    @Override
    public AbstractMemory copyOf() {
        AbstractMemory c = new AbstractMemory();
        c.isBot = this.isBot;
        c.isTop = this.isTop;
        c.env.clear();
        c.env.putAll(this.env);
        c.store.clear();
        for (Map.Entry<AccessPath, IntInterval> e : this.store.entrySet())
            c.store.put(e.getKey(), e.getValue().copyOf());
        return c;
    }

    public IntInterval readStore(AccessPath p) {
        IntInterval v = store.get(p);
        if (v == null) {
            IntInterval top = new IntInterval();
            top.setToTop();
            return top;
        }
        return v.copyOf();
    }

    public String[] getAllTempNames() {
        Set<String> tempNames = new HashSet<>();
        for (Var v : env.keySet()) {
            if (v.kind() == Var.Kind.TEMP) {
                tempNames.add(v.name());
            }
        }
        return tempNames.toArray(new String[0]);
    }
}
