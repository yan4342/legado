package io.legado.app.ui.dict

import io.legado.app.ui.widget.dialog.LegadoSheetDialog

fun createDictSheetDialog(
    word: String,
    context: DictSearchContext = DictSearchContext(),
): LegadoSheetDialog =
    LegadoSheetDialog.create { requestDismiss ->
        DictSheetScreen(
            word = word,
            dictSearchContext = context,
            onDismiss = requestDismiss,
        )
    }
