package com.example.service

import com.example.model.TetherServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TetherStateHolder {
  private val _state = MutableStateFlow(TetherServiceState())
  val state: StateFlow<TetherServiceState> = _state.asStateFlow()

  fun update(transform: (TetherServiceState) -> TetherServiceState) {
    _state.update(transform)
  }

  fun resetToDisconnected(errorMessage: String? = null) {
    _state.value = TetherServiceState(errorMessage = errorMessage)
  }
}
