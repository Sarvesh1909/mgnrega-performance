package com.mgnrega.backend.service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache<K, V> {
    private static class Entry<V> {
        final V value;
        final long expiresAtMs;
        Entry(V value, long expiresAtMs) { this.value = value; this.expiresAtMs = expiresAtMs; }
    }

    private final Map<K, Entry<V>> store = new ConcurrentHashMap<>();
    private final long ttlMs;

    public SimpleCache(long ttlMs) { this.ttlMs = ttlMs; }

    public V get(K key) {
        Entry<V> e = store.get(key);
        if (e == null) return null;
        if (Instant.now().toEpochMilli() > e.expiresAtMs) {
            store.remove(key);
            return null;
        }
        return e.value;
    }

    public void put(K key, V value) {
        store.put(key, new Entry<>(value, Instant.now().toEpochMilli() + ttlMs));
    }

    public void invalidate(K key) { store.remove(key); }

    public void clear() { store.clear(); }

    @Override public String toString() { return "SimpleCache{" + "size=" + store.size() + ", ttlMs=" + ttlMs + '}'; }
}
