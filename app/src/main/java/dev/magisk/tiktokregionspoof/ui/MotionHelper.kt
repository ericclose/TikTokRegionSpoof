package dev.magisk.tiktokregionspoof.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.card.MaterialCardView

object MotionHelper {

    private val easeInterpolator = FastOutSlowInInterpolator()

    fun animateCardColor(card: MaterialCardView, targetColor: Int) {
        val current = card.cardBackgroundColor.defaultColor
        if (current == targetColor) return
        ValueAnimator.ofObject(ArgbEvaluator(), current, targetColor).apply {
            duration = 280L
            interpolator = easeInterpolator
            addUpdateListener { anim ->
                card.setCardBackgroundColor(anim.animatedValue as Int)
            }
            start()
        }
    }

    fun animatePageSwitch(showView: View, hideView: View) {
        if (showView.isVisible && hideView.isGone) return
        hideView.animate().cancel()
        showView.animate().cancel()

        hideView.animate()
            .alpha(0f)
            .setDuration(110L)
            .setInterpolator(easeInterpolator)
            .withEndAction {
                hideView.visibility = View.GONE
                showView.alpha = 0f
                showView.translationY = 18f
                showView.visibility = View.VISIBLE
                showView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(easeInterpolator)
                    .start()
            }
            .start()
    }

    fun staggerEnter(views: List<View>) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 26f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 40L)
                .setDuration(280L)
                .setInterpolator(easeInterpolator)
                .start()
        }
    }

    fun beginContainerTransition(container: ViewGroup) {
        val transition = AutoTransition().apply {
            duration = 220L
            interpolator = easeInterpolator
        }
        TransitionManager.beginDelayedTransition(container, transition)
    }

    fun rotateChevron(chevron: View, expanded: Boolean) {
        chevron.animate()
            .rotation(if (expanded) 180f else 0f)
            .setDuration(200L)
            .setInterpolator(easeInterpolator)
            .start()
    }
}
