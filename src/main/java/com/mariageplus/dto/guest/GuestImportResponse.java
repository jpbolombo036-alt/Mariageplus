package com.mariageplus.dto.guest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestImportResponse {

    private int imported;
    private int skipped;
    @Builder.Default
    private List<GuestImportError> errors = new ArrayList<>();
}
