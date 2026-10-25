package com.example.climatecamera

internal enum class ProductFilter(val label: String) {
    ALL("Все"), ACTIVE("Занятые"), FREE("Свободные"), TARGET_REACHED("Цель достигнута")
}
internal enum class ProductSort(val label: String) {
    SLOT("По номеру"), START_DATE("Сначала ранние закладки"), LOSS("По усушке ↓"),
    LAST_WEIGHING("Давно не взвешивались")
}
internal object ProductListPolicy {
    fun select(products: List<Product>, filter: ProductFilter, sort: ProductSort, query: String): List<Product> {
        val slot = query.trim().removePrefix("№").trim()
        val filtered = products.filter { product ->
            (slot.isEmpty() || product.id.toString() == slot) && when (filter) {
                ProductFilter.ALL -> true
                ProductFilter.ACTIVE -> product.active
                ProductFilter.FREE -> !product.active
                ProductFilter.TARGET_REACHED -> product.active && product.initialWeight > 0 &&
                    product.lastWeight <= product.targetWeight
            }
        }
        return when (sort) {
            ProductSort.SLOT -> filtered.sortedBy { it.id }
            ProductSort.START_DATE -> filtered.sortedWith(compareBy<Product> { !it.active }
                .thenBy { ProductDates.day(it.startDate) ?: Long.MAX_VALUE }.thenBy { it.id })
            ProductSort.LOSS -> filtered.sortedWith(compareBy<Product> { !it.active }
                .thenByDescending { if (it.lossPct.isFinite()) it.lossPct else Double.NEGATIVE_INFINITY }.thenBy { it.id })
            ProductSort.LAST_WEIGHING -> filtered.sortedWith(compareBy<Product> { !it.active }
                .thenBy { ProductDates.day(it.history.lastOrNull()?.date ?: it.startDate) ?: Long.MAX_VALUE }
                .thenBy { it.id })
        }
    }
}
