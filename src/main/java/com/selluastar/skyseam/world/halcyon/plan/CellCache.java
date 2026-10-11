package com.selluastar.skyseam.world.halcyon.plan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * A bounded cache of one plan layer's grid cells, so a long journey across the unbounded Halcyon (docs/DECISIONS.md
 * K81) never fills memory: each of 16 stripes keeps its most recently used cells and drops the oldest. Every cell is
 * made from the seed alone, so a dropped cell comes back the same when it is asked for again. Thread-safe: chunks
 * are made on several threads at once.
 */
final class CellCache<V> {
    private static final int STRIPES = 16;
    private final Stripe<V>[] stripes;
    private final LongFunction<V> make;

    @SuppressWarnings("unchecked")
    CellCache(int capacity, LongFunction<V> make) {
        this.make = make;
        this.stripes = new Stripe[STRIPES];
        for (int k = 0; k < STRIPES; k++) {
            stripes[k] = new Stripe<>(Math.max(16, capacity / STRIPES));
        }
    }

    V get(long key) {
        Stripe<V> stripe = stripes[(int) (Noise.mix(0, key, 0, 0, 0) >>> 60)];
        synchronized (stripe) {
            V value = stripe.get(key);
            if (value != null) {
                return value;
            }
        }
        // Made outside the lock: making a cell may ask other layers (or this one) for theirs.
        V value = make.apply(key);
        synchronized (stripe) {
            V raced = stripe.putIfAbsent(key, value);
            return raced != null ? raced : value;
        }
    }

    /** How many cells are held now, for tests. */
    int size() {
        int total = 0;
        for (Stripe<V> stripe : stripes) {
            synchronized (stripe) {
                total += stripe.size();
            }
        }
        return total;
    }

    private static final class Stripe<V> extends LinkedHashMap<Long, V> {
        private final int capacity;

        Stripe(int capacity) {
            super(capacity * 2, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) {
            return size() > capacity;
        }
    }
}
