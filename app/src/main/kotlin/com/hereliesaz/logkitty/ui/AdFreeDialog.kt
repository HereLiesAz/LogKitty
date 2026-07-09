package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.billing.AdsState
import com.hereliesaz.logkitty.billing.BillingManager

@Composable
fun AdFreeDialog(
    billingManager: BillingManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val adFreePrice by billingManager.adFreePrice.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go Ad-Free", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "You can unlock LogKitty permanently by purchasing the Ad-Free version, or temporarily for free for the duration of this session.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                AzButton(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            billingManager.launchBillingFlow(activity)
                        }
                        onDismiss()
                    },
                    text = if (adFreePrice == "Loading...") "Loading..." else "Buy Ad-Free ($adFreePrice)",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                AzButton(
                    onClick = {
                        AdsState.isAdFree.value = true
                        onDismiss()
                    },
                    text = "Free Session",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
