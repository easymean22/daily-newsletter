package com.dailynewsletter.ui.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Creates a [CoroutineExceptionHandler] that forwards the error message to [onError].
 * Use with `viewModelScope.launch(viewModel.exceptionHandler { ... })` to prevent
 * unhandled exceptions from propagating as fatal crashes.
 */
fun ViewModel.exceptionHandler(onError: (String) -> Unit): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        onError(throwable.message ?: "알 수 없는 오류")
    }
