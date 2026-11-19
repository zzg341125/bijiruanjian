package io.github.latticenotes.util

import io.github.latticenotes.data.AttachmentEntity

private val embeddedDocumentExtensions = setOf("docx", "xlsx", "pdf")

fun AttachmentEntity.isEmbeddedDocument(): Boolean =
    name.substringAfterLast('.', "").lowercase() in embeddedDocumentExtensions

fun List<AttachmentEntity>.firstEmbeddedDocumentAttachment(): AttachmentEntity? =
    firstOrNull(AttachmentEntity::isEmbeddedDocument)
