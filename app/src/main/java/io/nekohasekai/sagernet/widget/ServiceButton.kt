package io.nekohasekai.sagernet.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.PointerIcon
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.BaseProgressIndicator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ServiceButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    FloatingActionButton(context, attrs, defStyleAttr), DynamicAnimation.OnAnimationEndListener {

    // Task 10-2: one static power icon for every state (the old paper-plane
    // AnimatedVectorDrawables are gone). The button state is still shown by
    // the green/grey power ring + the progress indicator, not by the icon.
    init {
        setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_power))
    }

    private var checked = false
    private var delayedAnimation: Job? = null
    private lateinit var progress: BaseProgressIndicator<*>
    fun initProgress(progress: BaseProgressIndicator<*>) {
        this.progress = progress
        progress.progressDrawable?.addSpringAnimationEndListener(this)
    }

    override fun onAnimationEnd(
        animation: DynamicAnimation<out DynamicAnimation<*>>?, canceled: Boolean, value: Float,
        velocity: Float
    ) {
        if (!canceled) progress.hide()
    }

    private fun hideProgress() {
        delayedAnimation?.cancel()
        progress.hide()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (checked) View.mergeDrawableStates(
            drawableState,
            intArrayOf(android.R.attr.state_checked)
        )
        return drawableState
    }

    fun changeState(state: BaseService.State, previousState: BaseService.State, animate: Boolean) {
        // Task 11-2: the icon lights up together with the power ring: bright
        // green when connected, dim grey otherwise.
        imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (state == BaseService.State.Connected) R.color.nkb_connected
                else R.color.nkb_text_hint
            )
        )
        // Progress handling moved here from the old per-state AnimatedState
        // onStart lambdas; the icon itself no longer changes with state.
        when (state) {
            BaseService.State.Connecting -> {
                hideProgress()
                delayedAnimation = (context as LifecycleOwner).lifecycleScope.launchWhenStarted {
                    delay(context.resources.getInteger(android.R.integer.config_mediumAnimTime) + 1000L)
                    progress.isIndeterminate = true
                    progress.show()
                }
            }
            BaseService.State.Connected -> {
                delayedAnimation?.cancel()
                progress.setProgressCompat(1, true)
            }
            else -> hideProgress()
        }
        checked = state == BaseService.State.Connected
        refreshDrawableState()
        val description = context.getText(if (state.canStop) R.string.stop else R.string.connect)
        contentDescription = description
        TooltipCompat.setTooltipText(this, description)
        val enabled = state.canStop || state == BaseService.State.Stopped
        isEnabled = enabled
        if (Build.VERSION.SDK_INT >= 24) pointerIcon = PointerIcon.getSystemIcon(
            context,
            if (enabled) PointerIcon.TYPE_HAND else PointerIcon.TYPE_WAIT
        )
    }

}
