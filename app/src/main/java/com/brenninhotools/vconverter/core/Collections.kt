package com.brenninhotools.vconverter.core

import kotlin.math.roundToLong

val CaseInsensitiveOrder: Comparator<String> = Comparator { a, b ->
    val n = minOf(a.length, b.length)
    for (i in 0 until n) {
        var c1 = a[i]
        var c2 = b[i]
        if (c1 != c2) {
            c1 = c1.uppercaseChar()
            c2 = c2.uppercaseChar()
            if (c1 != c2) {
                c1 = c1.lowercaseChar()
                c2 = c2.lowercaseChar()
                if (c1 != c2) return@Comparator c1 - c2
            }
        }
    }
    a.length - b.length
}

class SortedMutableMap<K, V>(private val comparator: Comparator<in K>) : AbstractMutableMap<K, V>() {
    private inner class Item(override val key: K, override var value: V) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val old = value
            value = newValue
            return old
        }
    }

    private val items = ArrayList<Item>()

    private fun find(key: K): Int = items.binarySearch { comparator.compare(it.key, key) }

    override fun put(key: K, value: V): V? {
        val i = find(key)
        if (i >= 0) {
            val old = items[i].value
            items[i].value = value
            return old
        }
        items.add(-i - 1, Item(key, value))
        return null
    }

    override fun get(key: K): V? = find(key).let { if (it >= 0) items[it].value else null }

    override fun containsKey(key: K): Boolean = find(key) >= 0

    override fun remove(key: K): V? {
        val i = find(key)
        return if (i >= 0) items.removeAt(i).value else null
    }

    override fun clear() = items.clear()

    override val size: Int get() = items.size

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = items.size

            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                put(element.key, element.value)
                return true
            }

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                val inner = items.iterator()
                return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    override fun hasNext(): Boolean = inner.hasNext()
                    override fun next(): MutableMap.MutableEntry<K, V> = inner.next()
                    override fun remove() = inner.remove()
                }
            }
        }
}

class SortedMutableSet<T>(private val comparator: Comparator<in T>) : AbstractMutableSet<T>() {
    private val items = ArrayList<T>()

    private fun find(element: T): Int = items.binarySearch { comparator.compare(it, element) }

    override fun add(element: T): Boolean {
        val i = find(element)
        if (i >= 0) return false
        items.add(-i - 1, element)
        return true
    }

    override fun contains(element: T): Boolean = find(element) >= 0

    override fun remove(element: T): Boolean {
        val i = find(element)
        if (i < 0) return false
        items.removeAt(i)
        return true
    }

    override val size: Int get() = items.size

    override fun iterator(): MutableIterator<T> = items.iterator()
}

fun <K : Comparable<K>, V> sortedMutableMap(): SortedMutableMap<K, V> = SortedMutableMap(naturalOrder())

fun <K, V> sortedMutableMap(comparator: Comparator<in K>): SortedMutableMap<K, V> = SortedMutableMap(comparator)

fun <T : Comparable<T>> sortedMutableSet(vararg items: T): SortedMutableSet<T> = SortedMutableSet<T>(naturalOrder()).also { it.addAll(items) }

fun <T> sortedMutableSet(comparator: Comparator<in T>): SortedMutableSet<T> = SortedMutableSet(comparator)

fun <T> Iterable<T>.toSortedMutableSet(comparator: Comparator<in T>): SortedMutableSet<T> = SortedMutableSet(comparator).also { set -> forEach { set.add(it) } }

fun <K : Comparable<K>, V> Map<K, V>.toSortedMutableMap(): SortedMutableMap<K, V> = SortedMutableMap<K, V>(naturalOrder()).also { it.putAll(this) }

fun <K, V> MutableMap<K, V>.putIfMissing(key: K, value: V) {
    if (!containsKey(key)) put(key, value)
}

fun Double.roundLong(): Long = if (isNaN()) 0L else roundToLong()

fun Float.roundInt(): Int = if (isNaN()) 0 else kotlin.math.round(this).toInt()
