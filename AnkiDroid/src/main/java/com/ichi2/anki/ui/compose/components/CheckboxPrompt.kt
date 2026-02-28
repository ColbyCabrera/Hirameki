package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CheckboxPrompt(
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp
) {
    Row(modifier = modifier
        .fillMaxWidth()
        .clickable { onCheckedChange(!isChecked) }
        // Original XML: marginTop=8dp, marginHorizontal=18dp
        .padding(top = 8.dp, start = horizontalPadding, end = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = isChecked, onCheckedChange = null
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CheckboxPromptPreview() {
    MaterialTheme {
        CheckboxPrompt(
            text = "Enable analytics", isChecked = true, onCheckedChange = {})
    }
}
