package com.focusfish.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.pet.DesktopPetService
import com.focusfish.app.pet.PetWindowController
import com.focusfish.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 桌宠设置 UI 状态
 */
data class PetSettingsUiState(
    /** 桌宠是否已开启 */
    val enabled: Boolean = false,
    /** 是否有悬浮窗权限 */
    val hasOverlayPermission: Boolean = false,
    /** 桌宠尺寸（dp） */
    val sizeDp: Int = PetWindowController.DEFAULT_SIZE_DP,
    /** 是否已授予电池优化白名单 */
    val hasBatteryExemption: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

/**
 * 桌宠设置 ViewModel
 *
 * 桌宠的开启/关闭走「用户主动操作」路径 ——
 * Android 15 起后台启动前台服务受限，必须在 App 可见时启动。
 */
@HiltViewModel
class PetSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val PREFS = "focus_fish_pet"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SIZE_DP = "size_dp"
        private const val KEY_POS_X = "pos_x"
        private const val KEY_POS_Y = "pos_y"
    }

    private val _uiState = MutableStateFlow(PetSettingsUiState())
    val uiState: StateFlow<PetSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * 刷新状态
     */
    fun refresh() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _uiState.update {
            it.copy(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                hasOverlayPermission = PermissionHelper.checkOverlayPermission(appContext),
                sizeDp = prefs.getInt(KEY_SIZE_DP, PetWindowController.DEFAULT_SIZE_DP),
                hasBatteryExemption = PermissionHelper.checkBatteryOptimization(appContext)
            )
        }
    }

    /**
     * 开启桌宠
     *
     * 前置条件：悬浮窗权限。没有权限时先引导用户去系统设置开启。
     */
    fun enablePet() {
        if (!PermissionHelper.checkOverlayPermission(appContext)) {
            PermissionHelper.requestOverlayPermission(appContext)
            _uiState.update {
                it.copy(errorMessage = "需要「显示在其他应用上层」权限才能显示桌宠，去系统设置开启吧")
            }
            return
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()
        DesktopPetService.start(appContext)
        _uiState.update { it.copy(enabled = true, errorMessage = null, message = "大肥鱼上桌了") }
    }

    /**
     * 关闭桌宠
     */
    fun disablePet() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
        DesktopPetService.stop(appContext)
        _uiState.update { it.copy(enabled = false, message = "大肥鱼下班了") }
    }

    /**
     * 切换桌宠开关
     */
    fun togglePet(enabled: Boolean) {
        if (enabled) enablePet() else disablePet()
    }

    /**
     * 修改桌宠尺寸
     */
    fun setSize(sizeDp: Int) {
        val clamped = sizeDp.coerceIn(
            PetWindowController.MIN_SIZE_DP,
            PetWindowController.MAX_SIZE_DP
        )
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIZE_DP, clamped)
            .apply()
        _uiState.update { it.copy(sizeDp = clamped) }
        // 尺寸改了需要重启桌宠才能生效
        if (_uiState.value.enabled) {
            DesktopPetService.stop(appContext)
            // 延迟重启，等服务完全停掉
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                DesktopPetService.start(appContext)
            }
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission() {
        PermissionHelper.requestOverlayPermission(appContext)
    }

    /**
     * 请求电池优化白名单
     *
     * 不加白名单的话，国产 ROM 会在几分钟内把桌宠服务杀掉，
     * 用户看到的就是「桌宠莫名消失」—— 第一大差评来源。
     */
    fun requestBatteryExemption() {
        PermissionHelper.requestBatteryOptimization(appContext)
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
