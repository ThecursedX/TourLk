package com.tourlk.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeletePaymentsRequestDto {

    @NotEmpty(message = "Select at least one payment")
    @Size(max = 200, message = "You can delete at most 200 payments at a time")
    private List<Long> ids;

}
