package com.tiendabackend.tiendabackend.chatbot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Motivo del cierre explícito de una conversación")
public class CierreRequestDTO {

    @Schema(description = "INTENCION_RESUELTA exige que la conversación tenga intenciones y que todas estén "
            + "resueltas; MANUAL cierra sin condiciones. INACTIVIDAD no se admite: lo registra el sistema",
            example = "INTENCION_RESUELTA", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"INTENCION_RESUELTA", "MANUAL"})
    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;
}
