package com.tourlk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** The full replacement list of add-ons for a package (an empty list removes them all). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackageAddOnsRequestDto {

    @NotNull(message = "Add-ons list is required")
    @Valid
    private List<PackageAddOnItemDto> addOns;

}
