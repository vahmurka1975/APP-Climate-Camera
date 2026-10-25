package com.example.climatecamera

import org.junit.Assert.*
import org.junit.Test

class ProductListPolicyTest {
    private val older = Product(2, true, 1000.0, 30.0, "2026-10-01", listOf(Weighing("2026-10-10", 900.0)))
    private val reached = Product(1, true, 1000.0, 30.0, "2026-10-05", listOf(Weighing("2026-10-12", 700.0)))
    private val free = Product(3, false, 0.0, 30.0, "", emptyList())
    private val products = listOf(free, older, reached)
    private fun ids(filter: ProductFilter = ProductFilter.ALL, sort: ProductSort = ProductSort.SLOT, query: String = "") =
        ProductListPolicy.select(products, filter, sort, query).map { it.id }
    @Test fun defaultUsesSlotOrder() { assertEquals(listOf(1, 2, 3), ids()) }
    @Test fun activeFilter() { assertEquals(listOf(1, 2), ids(ProductFilter.ACTIVE)) }
    @Test fun freeFilter() { assertEquals(listOf(3), ids(ProductFilter.FREE)) }
    @Test fun reachedIncludesExactTarget() { assertEquals(listOf(1), ids(ProductFilter.TARGET_REACHED)) }
    @Test fun searchMatchesExactSlot() { assertEquals(listOf(2), ids(query = "2")) }
    @Test fun searchAcceptsNumberSign() { assertEquals(listOf(2), ids(query = " №2 ")) }
    @Test fun queryCombinesWithFilter() { assertTrue(ids(ProductFilter.FREE, query = "2").isEmpty()) }
    @Test fun invalidSlotIsEmpty() { assertTrue(ids(query = "31").isEmpty()) }
    @Test fun earlyStartFirstAndFreeLast() { assertEquals(listOf(2, 1, 3), ids(sort = ProductSort.START_DATE)) }
    @Test fun greaterLossFirstAndFreeLast() { assertEquals(listOf(1, 2, 3), ids(sort = ProductSort.LOSS)) }
    @Test fun oldestControlFirst() { assertEquals(listOf(2, 1, 3), ids(sort = ProductSort.LAST_WEIGHING)) }
    @Test fun neverWeighedUsesStartDate() {
        val p = older.copy(id = 4, history = emptyList(), startDate = "2026-09-01")
        assertEquals(4, ProductListPolicy.select(products + p, ProductFilter.ACTIVE, ProductSort.LAST_WEIGHING, "").first().id)
    }
    @Test fun emptyInput() { assertTrue(ProductListPolicy.select(emptyList(), ProductFilter.ALL, ProductSort.SLOT, "").isEmpty()) }
    @Test fun inputNotMutated() { ids(sort = ProductSort.START_DATE); assertEquals(listOf(3, 2, 1), products.map { it.id }) }
    @Test fun invalidDateSortedLastAmongActive() {
        val bad = older.copy(id = 4, startDate = "bad")
        assertEquals(listOf(2, 1, 4, 3), ProductListPolicy.select(products + bad, ProductFilter.ALL, ProductSort.START_DATE, "").map { it.id })
    }
}
