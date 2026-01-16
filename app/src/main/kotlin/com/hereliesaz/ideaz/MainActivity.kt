package com.hereliesaz.ideaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.core.SheetDetent
import com.composables.core.rememberBottomSheetState
import com.hereliesaz.ideaz.ui.IdeBottomSheet
import com.hereliesaz.ideaz.ui.theme.IDEazTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MainApplication
        val viewModel = app.mainViewModel

        setContent {
            IDEazTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val screenHeight = maxHeight

                    val peekDetent = SheetDetent("peek", calculate = { screenHeight * 0.25f })
                    val halfwayDetent = SheetDetent("halfway", calculate = { screenHeight * 0.5f })
                    val fullyExpandedDetent = SheetDetent("fully_expanded", calculate = { screenHeight * 0.8f })

                    val sheetState = rememberBottomSheetState(
                        initialDetent = fullyExpandedDetent,
                        detents = listOf(peekDetent, halfwayDetent, fullyExpandedDetent)
                    )

                    // Ensure bottom sheet state is synced (optional, but good for tracking)
                    viewModel.stateDelegate.setBottomSheetState(sheetState.currentDetent)

                    IdeBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        peekDetent = peekDetent,
                        halfwayDetent = halfwayDetent,
                        fullyExpandedDetent = fullyExpandedDetent,
                        screenHeight = screenHeight,
                        onSendPrompt = { viewModel.sendPrompt(it) }
                    )
                }
            }
        }
    }
}
