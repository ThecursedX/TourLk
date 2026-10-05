package com.tourlk.dto;

import org.springframework.core.io.Resource;

/** A stored licence document ready to stream to an authorised caller. */
public record LicenceDocumentDownload(Resource resource, String fileName, String contentType) {
}
