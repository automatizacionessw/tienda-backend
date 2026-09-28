package com.tiendabackend.tiendabackend.chatbot;

import com.tiendabackend.tiendabackend.common.openapi.ErrorRecursoNoEncontrado;
import com.tiendabackend.tiendabackend.common.openapi.ErrorSimple;
import com.tiendabackend.tiendabackend.common.openapi.ErrorValidacion;
import com.tiendabackend.tiendabackend.common.openapi.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Clasificacion manual de conversaciones. Es la misma API que usara el LLM
// para registrar y resolver intenciones y cerrar conversaciones.
@RestController
@RequestMapping("/api/conversaciones")
@Tag(name = OpenApiConfig.TAG_CONVERSACIONES)
public class ConversacionController {

    private final ConversacionService conversacionService;

    public ConversacionController(ConversacionService conversacionService) {
        this.conversacionService = conversacionService;
    }

    @Operation(summary = "Listar conversaciones",
            description = "Devuelve las conversaciones con sus intenciones (sin mensajes), de la de último mensaje "
                    + "más reciente a la más antigua. Con estado=ABIERTA&sinClasificar=true se obtiene la cola de "
                    + "conversaciones vigentes pendientes de clasificar. El estado es el efectivo: una conversación "
                    + "sin mensajes durante el tiempo de inactividad se considera CERRADA por INACTIVIDAD.")
    @ApiResponse(responseCode = "200", description = "Lista de conversaciones (puede estar vacía)")
    @ApiResponse(responseCode = "400", description = "Valor de estado inválido",
            content = @Content(schema = @Schema(implementation = ErrorSimple.class)))
    @GetMapping
    public ResponseEntity<List<ConversacionResponseDTO>> listarConversaciones(
            @Parameter(description = "Filtra por identificador de cliente", example = "42")
            @RequestParam(required = false) Long clienteId,
            @Parameter(description = "Filtra por estado efectivo",
                    schema = @Schema(allowableValues = {"ABIERTA", "CERRADA"}), example = "ABIERTA")
            @RequestParam(required = false) String estado,
            @Parameter(description = "Si es true, solo conversaciones sin ninguna intención registrada",
                    example = "true")
            @RequestParam(defaultValue = "false") boolean sinClasificar) {
        return ResponseEntity.ok(conversacionService.listar(clienteId, estado, sinClasificar));
    }

    @Operation(summary = "Obtener una conversación",
            description = "Devuelve una conversación con sus mensajes en orden cronológico y sus intenciones. "
                    + "Es el contexto necesario para clasificarla.")
    @ApiResponse(responseCode = "200", description = "Conversación encontrada")
    @ApiResponse(responseCode = "404", description = "La conversación no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @GetMapping("/{id}")
    public ResponseEntity<ConversacionDetalleResponseDTO> obtenerConversacion(
            @Parameter(description = "Identificador de la conversación", example = "15") @PathVariable Long id) {
        return ResponseEntity.ok(conversacionService.obtenerDetalle(id));
    }

    @Operation(summary = "Registrar una intención",
            description = "Clasifica una conversación vigente: registra una intención en estado PENDIENTE. Una "
                    + "conversación puede tener varias intenciones de tipos distintos, pero solo una PENDIENTE por "
                    + "tipo. Lo que no encaja en el catálogo se registra como OTRA, con un detalle obligatorio.")
    @ApiResponse(responseCode = "201", description = "Intención registrada en estado PENDIENTE")
    @ApiResponse(responseCode = "400", description = "Datos inválidos: intención u origen fuera de los valores "
            + "permitidos, OTRA sin detalle o mensaje que no es un entrante de la conversación (cuerpo `error`), "
            + "o validación de campos (cuerpo `errores`)",
            content = @Content(schema = @Schema(oneOf = {ErrorSimple.class, ErrorValidacion.class})))
    @ApiResponse(responseCode = "404", description = "La conversación no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @ApiResponse(responseCode = "409", description = "La conversación está cerrada o vencida, o ya tiene una "
            + "intención PENDIENTE del mismo tipo",
            content = @Content(schema = @Schema(implementation = ErrorSimple.class)))
    @PostMapping("/{id}/intenciones")
    public ResponseEntity<IntencionResponseDTO> registrarIntencion(
            @Parameter(description = "Identificador de la conversación", example = "15") @PathVariable Long id,
            @Valid @RequestBody IntencionRequestDTO datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversacionService.registrarIntencion(id, datos));
    }

    @Operation(summary = "Resolver una intención",
            description = "Marca como RESUELTA una intención PENDIENTE cuando se cumplió lo que el cliente quería "
                    + "(por ejemplo, se le envió el catálogo). No cierra la conversación.")
    @ApiResponse(responseCode = "200", description = "Intención resuelta")
    @ApiResponse(responseCode = "404", description = "La conversación no existe o la intención no pertenece a ella",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @ApiResponse(responseCode = "409", description = "La intención ya está RESUELTA o la conversación está cerrada "
            + "o vencida",
            content = @Content(schema = @Schema(implementation = ErrorSimple.class)))
    @PatchMapping("/{id}/intenciones/{intencionId}/resolver")
    public ResponseEntity<IntencionResponseDTO> resolverIntencion(
            @Parameter(description = "Identificador de la conversación", example = "15") @PathVariable Long id,
            @Parameter(description = "Identificador de la intención", example = "7") @PathVariable Long intencionId) {
        return ResponseEntity.ok(conversacionService.resolverIntencion(id, intencionId));
    }

    @Operation(summary = "Cerrar una conversación",
            description = "Cierra una conversación vigente. INTENCION_RESUELTA exige que tenga intenciones y que "
                    + "todas estén resueltas (el LLM la usa tras preguntar si puede ayudar en algo más); MANUAL cierra "
                    + "sin condiciones y conserva las intenciones pendientes. El próximo mensaje del cliente abre "
                    + "una conversación nueva.")
    @ApiResponse(responseCode = "200", description = "Conversación cerrada")
    @ApiResponse(responseCode = "400", description = "Motivo ausente, inválido o INACTIVIDAD (lo registra el sistema)",
            content = @Content(schema = @Schema(oneOf = {ErrorSimple.class, ErrorValidacion.class})))
    @ApiResponse(responseCode = "404", description = "La conversación no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @ApiResponse(responseCode = "409", description = "La conversación ya está cerrada o vencida, o se pidió "
            + "INTENCION_RESUELTA sin intenciones o con intenciones pendientes",
            content = @Content(schema = @Schema(implementation = ErrorSimple.class)))
    @PatchMapping("/{id}/cerrar")
    public ResponseEntity<ConversacionResponseDTO> cerrarConversacion(
            @Parameter(description = "Identificador de la conversación", example = "15") @PathVariable Long id,
            @Valid @RequestBody CierreRequestDTO datos) {
        return ResponseEntity.ok(conversacionService.cerrar(id, datos));
    }
}
