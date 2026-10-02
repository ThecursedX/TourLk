package com.tourlk.dto;

import org.springframework.core.io.Resource;

/** A stored attachment ready to stream to an authorised caller. */
public record TicketAttachmentDownload(Resource resource, String fileName, String contentType, long sizeBytes) {
}
