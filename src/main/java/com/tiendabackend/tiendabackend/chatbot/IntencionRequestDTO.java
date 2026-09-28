package com.tiendabackend.tiendabackend.chatbot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// intencion y origen se reciben como texto y los valida el servicio: asi un
// valor invalido responde 400 {error} con la lista de valores validos (lo
// lee un LLM), en vez del error generico de deserializacion.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Intención detectada en la conversación. Queda en estado PENDIENTE hasta que se resuelva")
public class IntencionRequestDTO {

    @Schema(description = "Intención del cliente. OTRA se usa para lo que no encaja en el catálogo y exige "
            + "un detalle; indica que el LLM no debe responder",
            example = "CONSULTA_CATALOGO", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"SALUDO", "CONSULTA_CATALOGO", "INICIAR_PEDIDO", "CONSULTAR_ESTADO_PEDIDO", "OTRA"})
    @NotBlank(message = "La intención es obligatoria")
    private String intencion;

    @Schema(description = "Descripción libre de lo que pidió el cliente. Obligatoria si la intención es OTRA",
            example = "Pide el catálogo de auriculares", maxLength = ConversacionIntencion.DETALLE_MAX)
    @Size(max = ConversacionIntencion.DETALLE_MAX, message = "El detalle admite hasta 500 caracteres")
    private String detalle;

    @Schema(description = "Mensaje entrante de esta conversación que originó la intención (opcional)",
            example = "120")
    private Long mensajeId;

    @Schema(description = "Quién clasificó la conversación. Si se omite, MANUAL",
            example = "LLM", allowableValues = {"MANUAL", "LLM"})
    private String origen;
}
