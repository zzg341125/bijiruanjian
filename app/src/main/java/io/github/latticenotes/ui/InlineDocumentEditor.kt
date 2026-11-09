package io.github.latticenotes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.DynamicDrawableSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.latticenotes.data.AttachmentEntity
import io.github.latticenotes.util.AttachmentReferences
import java.io.File
import kotlin.math.min

@Composable
fun InlineDocumentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<AttachmentEntity>,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            DocumentEditText(context).apply {
                background = null
                gravity = Gravity.TOP or Gravity.START
                includeFontPadding = false
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                isSingleLine = false
                setPadding(0, 0, 0, 0)
                setTextColor(textColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setLineSpacing(9f * resources.displayMetrics.scaledDensity, 1f)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        text: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) = Unit

                    override fun onTextChanged(
                        text: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) = Unit

                    override fun afterTextChanged(text: Editable?) {
                        if (!suppressCallback) {
                            currentOnValueChange(text?.toString().orEmpty())
                        }
                    }
                })
            }
        },
        update = { editor ->
            val current = editor.text?.toString().orEmpty()
            if (current != value) {
                val oldSelection = editor.selectionStart.coerceAtLeast(0)
                val selection = if (value.startsWith(current)) {
                    value.length
                } else {
                    oldSelection.coerceAtMost(value.length)
                }
                editor.suppressCallback = true
                editor.setText(value)
                editor.setSelection(selection)
                editor.suppressCallback = false
            }
            editor.applyImageSpans(attachments)
        },
    )
}

private class DocumentEditText(context: Context) : EditText(context) {
    var suppressCallback: Boolean = false
    private val imageSpans = mutableMapOf<Long, DocumentImageSpan>()

    fun applyImageSpans(attachments: List<AttachmentEntity>) {
        val editable = text ?: return
        editable.getSpans(0, editable.length, DocumentImageSpan::class.java)
            .forEach(editable::removeSpan)

        val availableWidth = (
            width - compoundPaddingLeft - compoundPaddingRight
            ).takeIf { it > 0 } ?: (
            resources.displayMetrics.widthPixels - (150f * resources.displayMetrics.density).toInt()
            )
        val maxWidth = availableWidth.coerceAtLeast(
            (240f * resources.displayMetrics.density).toInt(),
        )
        val maxHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        val attachmentsById = attachments.associateBy { it.id }

        AttachmentReferences.references(editable.toString()).forEach { reference ->
            val attachment = attachmentsById[reference.attachmentId] ?: return@forEach
            if (attachment.mimeType?.startsWith("image/") != true) return@forEach
            val span = imageSpans[attachment.id]
                ?: createImageSpan(attachment, maxWidth, maxHeight)?.also {
                    imageSpans[attachment.id] = it
                }
                ?: return@forEach
            editable.setSpan(
                span,
                reference.range.first,
                reference.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

private fun createImageSpan(
    attachment: AttachmentEntity,
    maxWidth: Int,
    maxHeight: Int,
): DocumentImageSpan? {
    val path = Uri.parse(attachment.uri).path ?: return null
    val bitmap = decodeScaledBitmap(File(path), maxWidth, maxHeight) ?: return null
    val drawable = BitmapDrawable(bitmap).apply {
        setBounds(0, 0, bitmap.width, bitmap.height)
    }
    return DocumentImageSpan(drawable)
}

private fun decodeScaledBitmap(
    file: File,
    maxWidth: Int,
    maxHeight: Int,
): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= maxWidth ||
        bounds.outHeight / (sampleSize * 2) >= maxHeight
    ) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return null
    val scale = min(
        maxWidth.toFloat() / decoded.width,
        maxHeight.toFloat() / decoded.height,
    ).coerceAtMost(1f)
    if (scale >= 1f) return decoded
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    decoded.recycle()
    return scaled
}

private class DocumentImageSpan(
    private val image: Drawable,
) : DynamicDrawableSpan(ALIGN_BOTTOM) {
    override fun getDrawable(): Drawable = image.apply {
        if (bounds.isEmpty) setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }
}
