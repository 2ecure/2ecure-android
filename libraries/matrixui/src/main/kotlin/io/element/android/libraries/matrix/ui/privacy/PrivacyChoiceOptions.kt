/*
 * Copyright (c) 2026 2ecure.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * 2ecure: the two answers to «Who can find you?» (docs/privacy.md), for the question after signing in and the settings.
 *
 * @param selection true for «Everyone on 2ecure», false for «Only people I chat with», null while nothing is chosen.
 */
@Composable
fun PrivacyChoiceOptions(
    selection: Boolean?,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        ListItem(
            content = { Text(text = stringResource(CommonStrings.twoecure_privacy_choice_everyone)) },
            supportingContent = { Text(text = stringResource(CommonStrings.twoecure_privacy_choice_everyone_description)) },
            trailingContent = ListItemContent.RadioButton(selected = selection == true, compact = true),
            onClick = { onSelect(true) },
            enabled = enabled,
        )
        ListItem(
            content = { Text(text = stringResource(CommonStrings.twoecure_privacy_choice_contacts)) },
            supportingContent = { Text(text = stringResource(CommonStrings.twoecure_privacy_choice_contacts_description)) },
            trailingContent = ListItemContent.RadioButton(selected = selection == false, compact = true),
            onClick = { onSelect(false) },
            enabled = enabled,
        )
    }
}
