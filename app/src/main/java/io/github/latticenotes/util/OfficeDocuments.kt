package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity

private val embeddedOfficeExtensions = setOf("docx", "xlsx")

fun AttachmentEntity.isEmbeddedOfficeDocument(): Boolean =
    name.substringAfterLast('.', "").lowercase() in embeddedOfficeExtensions

fun List<AttachmentEntity>.firstEmbeddedOfficeAttachment(): AttachmentEntity? =
    firstOrNull(AttachmentEntity::isEmbeddedOfficeDocument)
