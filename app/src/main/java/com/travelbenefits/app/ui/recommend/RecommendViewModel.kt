package com.travelbenefits.app.ui.recommend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.RecommendationEngine
import com.travelbenefits.app.domain.model.RecommendationEntry
import com.travelbenefits.app.domain.model.SpendingCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RecommendViewModel @Inject constructor(
    walletRepository: WalletRepository,
    private val recommendationEngine: RecommendationEngine,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow(SpendingCategory.DINING)

    val uiState: StateFlow<List<RecommendationEntry>> = combine(
        walletRepository.observeResolvedCards(),
        selectedCategory,
    ) { cards, category ->
        recommendationEngine.rank(cards, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val category: StateFlow<SpendingCategory> = selectedCategory

    fun selectCategory(category: SpendingCategory) {
        selectedCategory.value = category
    }
}
