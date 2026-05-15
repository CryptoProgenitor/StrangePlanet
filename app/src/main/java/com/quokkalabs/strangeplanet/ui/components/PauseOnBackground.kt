package com.quokkalabs.strangeplanet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Invokes [onBackground] whenever the hosting activity stops — i.e. the screen
 * is switched off or the app is backgrounded. Every arcade game wires this to
 * its pause action so play never continues unseen.
 */
@Composable
fun PauseOnBackground(onBackground: () -> Unit) {
    val owner = LocalContext.current as? LifecycleOwner ?: return
    val callback by rememberUpdatedState(onBackground)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) callback()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
