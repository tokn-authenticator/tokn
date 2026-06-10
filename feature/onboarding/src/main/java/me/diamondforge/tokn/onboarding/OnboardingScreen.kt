package me.diamondforge.tokn.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.diamondforge.tokn.onboarding.slides.DoneSlide
import me.diamondforge.tokn.onboarding.slides.SecurityPickerSlide
import me.diamondforge.tokn.onboarding.slides.SecuritySetupSlide
import me.diamondforge.tokn.onboarding.slides.WelcomeSlide

private enum class Slide { Welcome, SecurityPicker, SecuritySetup, Done }

private val SLIDES = listOf(Slide.Welcome, Slide.SecurityPicker, Slide.SecuritySetup, Slide.Done)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onSetupBiometric: suspend () -> Boolean = { true },
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val pickMethodMsg = stringResource(R.string.onboarding_security_pick_method)
    val biometricRequiredMsg = stringResource(R.string.onboarding_biometric_required)

    // A successful Welcome-slide import is the user's signal that they're done
    // with this step; pause briefly so the "Imported N accounts" feedback can
    // register, then slide on to the next step automatically.
    LaunchedEffect(state.importedCount) {
        if (state.importedCount != null && SLIDES[pagerState.currentPage] == Slide.Welcome) {
            kotlinx.coroutines.delay(450)
            pagerState.animateScrollToPage(nextPage(pagerState.currentPage, state.cryptType))
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        bottomBar = {
            // Visible (effective) slide count for the indicator: SecuritySetup is hidden when NONE.
            val visibleCount =
                if (state.cryptType == CryptType.NONE) SLIDES.size - 1 else SLIDES.size
            val visibleIndex = effectiveIndex(pagerState.currentPage, state.cryptType)

            PagerControls(
                slideCount = visibleCount,
                currentVisibleIndex = visibleIndex,
                isLast = SLIDES[pagerState.currentPage] == Slide.Done,
                canGoNext = canAdvance(pagerState.currentPage, state),
                isFinishing = state.isFinishing,
                showPrevious = pagerState.currentPage != 0 &&
                        SLIDES[pagerState.currentPage] != Slide.Done,
                onPrevious = {
                    scope.launch {
                        val target = previousPage(pagerState.currentPage, state.cryptType)
                        pagerState.animateScrollToPage(target)
                    }
                },
                onNext = {
                    val current = SLIDES[pagerState.currentPage]
                    when {
                        current == Slide.SecurityPicker && state.cryptType == null -> {
                            scope.launch { snackbar.showSnackbar(pickMethodMsg) }
                        }

                        current == Slide.SecurityPicker && state.cryptType == CryptType.BIOMETRIC -> {
                            scope.launch {
                                if (onSetupBiometric()) {
                                    pagerState.animateScrollToPage(
                                        nextPage(pagerState.currentPage, state.cryptType),
                                    )
                                } else {
                                    snackbar.showSnackbar(biometricRequiredMsg)
                                }
                            }
                        }

                        current == Slide.Done -> viewModel.finish(onFinished)

                        else -> scope.launch {
                            pagerState.animateScrollToPage(
                                nextPage(pagerState.currentPage, state.cryptType),
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = false,
        ) { page ->
            when (SLIDES[page]) {
                Slide.Welcome -> WelcomeSlide(
                    state = state,
                    onImport = viewModel::importBackup,
                    onCancelPendingImport = viewModel::cancelPendingImport,
                    onSuppressLock = viewModel::suppressLock,
                    onClearImportFeedback = viewModel::clearImportFeedback,
                )

                Slide.SecurityPicker -> SecurityPickerSlide(
                    selected = state.cryptType,
                    biometricAvailable = state.biometricAvailable,
                    onSelect = viewModel::setCryptType,
                )

                Slide.SecuritySetup -> SecuritySetupSlide(
                    password = state.password,
                    passwordConfirm = state.passwordConfirm,
                    onPasswordChange = viewModel::setPassword,
                    onPasswordConfirmChange = viewModel::setPasswordConfirm,
                )

                Slide.Done -> DoneSlide()
            }
        }
    }
}

private fun nextPage(current: Int, cryptType: CryptType?): Int {
    // From SecurityPicker, skip SecuritySetup if NONE → jump straight to Done.
    if (SLIDES[current] == Slide.SecurityPicker && cryptType == CryptType.NONE) {
        return SLIDES.indexOf(Slide.Done)
    }
    return (current + 1).coerceAtMost(SLIDES.lastIndex)
}

private fun previousPage(current: Int, cryptType: CryptType?): Int {
    // From Done, skip SecuritySetup back to SecurityPicker if NONE.
    if (SLIDES[current] == Slide.Done && cryptType == CryptType.NONE) {
        return SLIDES.indexOf(Slide.SecurityPicker)
    }
    return (current - 1).coerceAtLeast(0)
}

private fun effectiveIndex(page: Int, cryptType: CryptType?): Int {
    // When NONE, SecuritySetup is invisible, so Done sits one position earlier in the indicator.
    if (cryptType == CryptType.NONE && SLIDES[page] == Slide.Done) return SLIDES.size - 2
    return page
}

private fun canAdvance(page: Int, state: OnboardingUiState): Boolean {
    return when (SLIDES[page]) {
        Slide.Welcome -> true
        Slide.SecurityPicker -> state.cryptType != null
        Slide.SecuritySetup -> state.password.length >= 8 && state.password == state.passwordConfirm
        Slide.Done -> !state.isFinishing
    }
}

@Composable
private fun PagerControls(
    slideCount: Int,
    currentVisibleIndex: Int,
    isLast: Boolean,
    canGoNext: Boolean,
    isFinishing: Boolean,
    showPrevious: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            if (showPrevious) {
                FilledTonalIconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.onboarding_previous)
                    )
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            SlideIndicator(count = slideCount, currentIndex = currentVisibleIndex)
        }
        FilledTonalIconButton(
            onClick = onNext,
            enabled = canGoNext && !isFinishing,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = if (isLast) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(if (isLast) R.string.onboarding_finish else R.string.onboarding_next),
            )
        }
    }
}

@Composable
private fun SlideIndicator(count: Int, currentIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == currentIndex
            val width by animateDpAsState(if (active) 22.dp else 8.dp, label = "indicatorWidth")
            val color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(color = color, shape = CircleShape),
            )
        }
    }
}
