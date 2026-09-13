package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.legado.app.R
import io.legado.app.domain.model.DramaticRole
import java.io.File

private const val MAX_VISIBLE_CARDS = 3
private val CharacterCardWidth = 152.dp

@Immutable
data class BookDetailCharacterUi(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val dramaticRole: String = "",
    val avatarPath: String = "",
)

/**
 * Book detail strip: at most 3 character cards + "view more" when cast is non-empty,
 * plus shortcuts to voice casting / character list / network / world book / outline.
 */
@Composable
fun BookDetailCastSection(
    characters: List<BookDetailCharacterUi>,
    onCharacterClick: (String) -> Unit,
    onOpenVoiceCasting: () -> Unit,
    onOpenCharacterList: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenWorldBook: () -> Unit,
    onOpenOutline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = characters.take(MAX_VISIBLE_CARDS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.book_characters),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenVoiceCasting) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = stringResource(R.string.book_voice_casting),
                )
            }
            IconButton(onClick = onOpenNetwork) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = stringResource(R.string.character_network),
                )
            }
            IconButton(onClick = onOpenWorldBook) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.book_world_book),
                )
            }
            IconButton(onClick = onOpenOutline) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = stringResource(R.string.book_outline),
                )
            }
        }

        if (characters.isEmpty()) {
            Text(
                text = stringResource(R.string.book_info_cast_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(onClick = onOpenCharacterList),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visible.forEach { character ->
                    CharacterChipCard(
                        character = character,
                        onClick = { onCharacterClick(character.id) },
                    )
                }
                ViewMoreCharacterCard(onClick = onOpenCharacterList)
            }
        }
    }
}

@Composable
private fun CharacterChipCard(
    character: BookDetailCharacterUi,
    onClick: () -> Unit,
) {
    val displayName = character.name.ifBlank { "…" }
    val roleLabelRes = DramaticRole.labelRes(character.dramaticRole)
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(CharacterCardWidth)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatarFile = character.avatarPath.takeIf { it.isNotBlank() }
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                    if (avatarFile != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (roleLabelRes != null) {
                        Text(
                            text = stringResource(roleLabelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (character.subtitle.isNotBlank()) {
                Text(
                    text = character.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ViewMoreCharacterCard(
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(CharacterCardWidth/2)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.book_info_cast_view_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                minLines = 2,
            )
        }
    }
}
