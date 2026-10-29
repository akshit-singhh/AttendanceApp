package com.akshit.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akshit.attendance.ui.theme.*

enum class ChipTone { PRESENT, ABSENT, PENDING }

/** The pill-shaped status badge used everywhere: roster rows, history entries, leave status. */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        ChipTone.PRESENT -> PresentContainer to PresentText
        ChipTone.ABSENT -> AbsentContainer to AbsentText
        ChipTone.PENDING -> PendingContainer to PendingText
    }
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(container, PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
