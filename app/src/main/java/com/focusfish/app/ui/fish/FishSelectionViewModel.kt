package com.focusfish.app.ui.fish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.data.local.entity.FishCatalogEntity
import com.focusfish.app.data.local.entity.UserFishEntity
import com.focusfish.app.data.repository.FishRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 大肥鱼选择界面的 UI 状态
 */
data class FishSelectionUiState(
    /** 所有可选品种（首次选择用） */
    val breeds: List<FishCatalogEntity> = emptyList(),
    /** 用户已创建的所有大肥鱼（切换模式用） */
    val existingFishs: List<UserFishEntity> = emptyList(),
    /** 当前选中的品种 ID（首次选择 / 切换模式下添加第二只用） */
    val selectedBreedId: String? = null,
    /** 当前选中的已创建大肥鱼 ID（切换模式用） */
    val selectedExistingFishId: Int? = null,
    /** 用户输入的大肥鱼名（首次选择 / 切换模式下添加第二只用） */
    val fishName: String = "",
    /** 切换模式下是否处于"添加第二个伙伴"的子流程 */
    val isAddingNewFish: Boolean = false,
    /** 是否正在创建/切换 */
    val isCreating: Boolean = false,
    /** 是否创建/切换完成 */
    val isCreated: Boolean = false,
    /** "初识之友"成就（投喂10次）是否已解锁，决定能否添加第二个伙伴 */
    val isFirstFriendUnlocked: Boolean = false,
    /** 错误提示 */
    val errorMessage: String? = null
)

/**
 * 大肥鱼选择 ViewModel
 *
 * 简化设计（仅 2 个品种：经典 + 元气大肥鱼）：
 * - 首次启动：选品种 + 起名 → 创建第一条鱼
 * - 切换模式：显示已创建的大肥鱼列表，点击即切换；若只有 1 只，可添加第二只（上限 2 只）
 *   添加第二个伙伴需先解锁"初识之友"成就（投喂10次）
 */
@HiltViewModel
class FishSelectionViewModel @Inject constructor(
    private val fishRepository: FishRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FishSelectionUiState())
    val state: StateFlow<FishSelectionUiState> = _state.asStateFlow()

    /** "初识之友"成就的里程碑值（投喂10次） */
    private val firstFriendMilestone = 10

    init {
        loadBreeds()
        loadExistingFishs()
        checkFirstFriendAchievement()
    }

    /** 观察所有品种（Flow），种子数据插入后自动更新 */
    private fun loadBreeds() {
        viewModelScope.launch {
            fishRepository.observeAllBreeds().collect { breeds ->
                _state.update { it.copy(breeds = breeds) }
            }
        }
    }

    /** 观察所有已创建的大肥鱼（Flow），用于切换模式展示 */
    private fun loadExistingFishs() {
        viewModelScope.launch {
            fishRepository.observeAllFish().collect { cats ->
                _state.update { it.copy(existingFishs = cats) }
            }
        }
    }

    /**
     * 检查"初识之友"成就是否已解锁
     * 只有解锁后才能添加第二个伙伴
     */
    private fun checkFirstFriendAchievement() {
        viewModelScope.launch {
            val unlocked = fishRepository.isAchievementUnlocked(firstFriendMilestone)
            _state.update { it.copy(isFirstFriendUnlocked = unlocked) }
        }
    }

    /** 选择品种（首次选择模式） */
    fun selectBreed(breedId: String) {
        _state.update { it.copy(selectedBreedId = breedId, errorMessage = null) }
    }

    /** 选择已创建的大肥鱼（切换模式） */
    fun selectExistingFish(fishId: Int) {
        _state.update {
            it.copy(
                selectedExistingFishId = fishId,
                isAddingNewFish = false,
                selectedBreedId = null,
                fishName = "",
                errorMessage = null
            )
        }
    }

    /** 进入/退出“添加第二个伙伴”子流程 */
    fun setAddingNewFish(adding: Boolean) {
        _state.update {
            it.copy(
                isAddingNewFish = adding,
                selectedExistingFishId = if (adding) null else it.selectedExistingFishId,
                selectedBreedId = null,
                fishName = "",
                errorMessage = null
            )
        }
    }

    /** 更新大肥鱼名（限制最多 12 个字符） */
    fun updateName(name: String) {
        val trimmed = if (name.length > 12) name.take(12) else name
        _state.update { it.copy(fishName = trimmed, errorMessage = null) }
    }

    /**
     * 创建新大肥鱼（首次选择）
     */
    fun createFish() {
        val currentState = _state.value
        if (currentState.selectedBreedId == null) {
            _state.update { it.copy(errorMessage = "请选择一条鱼咪") }
            return
        }
        if (currentState.fishName.isBlank()) {
            _state.update { it.copy(errorMessage = "请给大肥鱼起个名字") }
            return
        }

        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                fishRepository.createFish(
                    breedId = currentState.selectedBreedId,
                    name = currentState.fishName.trim()
                )
                _state.update { it.copy(isCreating = false, isCreated = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "创建失败，请重试")
                }
            }
        }
    }

    /**
     * 切换到指定大肥鱼（恢复该大肥鱼的独立养成进度）
     */
    fun switchToFish() {
        val fishId = _state.value.selectedExistingFishId
        if (fishId == null) {
            _state.update { it.copy(errorMessage = "请选择一条鱼咪") }
            return
        }
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                fishRepository.switchToFish(fishId)
                _state.update { it.copy(isCreating = false, isCreated = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "切换失败，请重试")
                }
            }
        }
    }

    /**
     * 切换模式下添加第二个伙伴
     * 前置条件：解锁"初识之友"成就（投喂10次）
     * 必须先选择品种并输入名字
     */
    fun addSecondFish() {
        val currentState = _state.value
        if (currentState.existingFishs.size >= 2) {
            _state.update { it.copy(errorMessage = "最多只能拥有两条鱼咪") }
            return
        }
        // 成就解锁检查：未解锁"初识之友"不允许添加新伙伴
        if (!currentState.isFirstFriendUnlocked) {
            _state.update {
                it.copy(
                    errorMessage = "需先解锁「初识之友」成就（投喂 10 次）才能添加新伙伴"
                )
            }
            return
        }
        if (currentState.selectedBreedId == null) {
            _state.update { it.copy(errorMessage = "请选择一只大肥鱼形态") }
            return
        }
        if (currentState.fishName.isBlank()) {
            _state.update { it.copy(errorMessage = "请先给大肥鱼起个名字") }
            return
        }
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                fishRepository.createFish(
                    breedId = currentState.selectedBreedId,
                    name = currentState.fishName.trim()
                )
                _state.update {
                    it.copy(
                        isCreating = false,
                        isCreated = true,
                        isAddingNewFish = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "创建失败，请重试")
                }
            }
        }
    }
}
