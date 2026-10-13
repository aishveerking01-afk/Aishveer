package com.rui.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rui.app.ChatViewModel
import com.rui.app.R
import com.rui.app.data.Chat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun groupOf(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.isAfter(today.minusDays(8)) -> "Previous 7 days"
        else -> "Older"
    }
}

@Composable
fun Sidebar(vm: ChatViewModel, onNewChat: () -> Unit, onOpenChat: (String) -> Unit, onOpenModels: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val chats by vm.chats.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val grouped = remember(chats, query) {
        chats.filter { it.title.contains(query, ignoreCase = true) }
            .sortedByDescending(Chat::updatedAt)
            .groupBy { groupOf(it.updatedAt) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.rui_logo), "Rui", Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Rui", style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp), color = cs.onSurface)
                Text("feeling calm", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New chat")
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search chats…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions.Default,
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (grouped.isEmpty()) item {
                Text("No chats yet", color = cs.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            listOf("Today", "Yesterday", "Previous 7 days", "Older").forEach { label ->
                val items = grouped[label] ?: return@forEach
                item(key = "h$label") {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp))
                }
                items(items, key = { it.id }) { c ->
                    Text(
                        c.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenChat(c.id) }.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenModels).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Memory, null, tint = cs.secondary)
            Spacer(Modifier.width(12.dp))
            Text("Models & storage", color = cs.onSurface)
        }
    }
}
