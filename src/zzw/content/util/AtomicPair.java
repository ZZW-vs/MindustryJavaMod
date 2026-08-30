package zzw.content.util;

/**
 * 可重置的二元组 (PU132 arc.util.AtomicPair 精简移植)
 * <p>Light 的 child 存储: key=直接 child (本光创建), value=间接 child
 * (合并的已有光)。reset() 由 AtomicPair 原版提供。</p>
 */
public class AtomicPair<K, V> {
    public K key;
    public V value;

    public AtomicPair() {}

    public AtomicPair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    /** 重置为 null (原版 AtomicPair.reset) */
    public AtomicPair<K, V> reset() {
        key = null;
        value = null;
        return this;
    }
}