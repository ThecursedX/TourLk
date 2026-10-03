package com.tourlk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkDeletePaymentsResultDto {

    private int deleted;
    private List<Skipped> skipped;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skipped {
        private Long id;
        private String reason;
    }

}
